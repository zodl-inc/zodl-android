#!/usr/bin/env bash
# Automates the forward-merge cascade: release/vX.Y.Z (shipped) -> maint/vX.Y.x
# -> next maint line -> ... -> main. See the zodl_an_cascade_merge skill for the
# full model this implements.
#
# For every pending link this opens (or reuses) a PR and turns on GitHub's
# native auto-merge with the "merge" method. It never uses squash or rebase:
# every landed commit must keep two parents, or the *next* cascade replays
# already-merged content as phantom conflicts (this has happened twice via
# accidental squash - see the skill, §3-4).
#
# Requires: git, gh (authenticated via GH_TOKEN), a full clone (fetch-depth: 0).
set -euo pipefail

MAIN_BRANCH="main"
REMOTE="origin"

log() { printf '%s\n' "$*"; }

# --- discover maint lines, sorted oldest -> newest -----------------------
mapfile -t MAINT_BRANCHES < <(
  git for-each-ref --format='%(refname:short)' "refs/remotes/${REMOTE}/maint/v*.x" |
    sed "s#^${REMOTE}/##" |
    grep -E '^maint/v[0-9]+\.[0-9]+\.x$' |
    awk -F'[v.x]' '{printf "%03d.%03d %s\n", $2, $3, $0}' |
    sort |
    awk '{print $2}'
)

if [ "${#MAINT_BRANCHES[@]}" -eq 0 ]; then
  log "No maint/vX.Y.x branches found - nothing to cascade."
  exit 0
fi

log "maint lines, oldest -> newest: ${MAINT_BRANCHES[*]}"

# --- helpers ---------------------------------------------------------------

is_ancestor() {
  # is_ancestor <old> <new>  -- true if <old> tip is already merged into <new>
  git merge-base --is-ancestor "${REMOTE}/$1" "${REMOTE}/$2"
}

pr_title() {
  local source="$1" target="$2"
  printf "Merge %s into %s" "$source" "$target"
}

# ensure_pr_and_automerge <source> <target>
# Opens (or reuses) a PR from <source> into <target> and, if it is cleanly
# mergeable, enables native auto-merge with the merge-commit method. If it
# conflicts, leaves it as a plain PR for a human - never auto-resolves.
ensure_pr_and_automerge() {
  local source="$1" target="$2"

  if is_ancestor "$source" "$target"; then
    log "  [skip] $source is already merged into $target"
    return 0
  fi

  local existing
  existing=$(gh pr list --head "$source" --base "$target" --state open --json number -q '.[0].number // empty')

  local pr_number
  if [ -n "$existing" ]; then
    pr_number="$existing"
    log "  [reuse] PR #$pr_number ($source -> $target)"
  else
    log "  [new] opening PR: $source -> $target"
    local title body
    title=$(pr_title "$source" "$target")
    body=$(cat <<BODY
Automated forward-merge cascade (release -> maint -> main).

Opened by \`.github/workflows/cascade-merge.yml\`. Lands as a real two-parent
merge commit only - squash and rebase are never used here on purpose (see the
\`zodl_an_cascade_merge\` skill for why a squashed cascade link poisons every
cascade after it).

If this PR shows conflicts, resolve them per the skill's §4 recovery recipe if
they look like the squash-poisoned-ancestry pattern (dozens of add/add
conflicts); otherwise resolve normally and merge with **Merge pull request**,
never squash or rebase.
BODY
)
    pr_number=$(gh pr create --base "$target" --head "$source" --title "$title" --body "$body" --json number -q '.number' 2>/dev/null) || {
      log "  [warn] could not open PR for $source -> $target (may already exist under a different state, or need a human to look) - skipping"
      return 0
    }
  fi

  local mergeable
  mergeable=$(gh pr view "$pr_number" --json mergeable -q '.mergeable')

  if [ "$mergeable" != "MERGEABLE" ]; then
    log "  [conflict] PR #$pr_number ($source -> $target) is not cleanly mergeable (state: $mergeable) - left for manual resolution, auto-merge NOT enabled"
    return 0
  fi

  local already_auto
  already_auto=$(gh pr view "$pr_number" --json autoMergeRequest -q '.autoMergeRequest // empty')
  if [ -n "$already_auto" ]; then
    log "  [ok] PR #$pr_number already has auto-merge enabled"
    return 0
  fi

  log "  [auto-merge] enabling native auto-merge (merge commit) on PR #$pr_number"
  gh pr merge "$pr_number" --auto --merge || \
    log "  [warn] could not enable auto-merge on PR #$pr_number (needs a human to check, e.g. missing required checks) "
}

# --- 1. release/vX.Y.Z (shipped) -> its own maint/vX.Y.x -------------------

log "Checking release -> maint back-merges..."
mapfile -t RELEASE_BRANCHES < <(
  git for-each-ref --format='%(refname:short)' "refs/remotes/${REMOTE}/release/v*" |
    sed "s#^${REMOTE}/##" |
    grep -E '^release/v[0-9]+\.[0-9]+\.[0-9]+$'
)

for release in "${RELEASE_BRANCHES[@]}"; do
  version="${release#release/v}"
  xy="${version%.*}"
  maint="maint/v${xy}.x"

  if ! git show-ref --verify --quiet "refs/remotes/${REMOTE}/${maint}"; then
    continue # no matching maint line (yet) for this release version
  fi

  tip=$(git rev-parse "${REMOTE}/${release}")
  tags_at_tip=$(git tag --points-at "$tip")
  if [ -z "$tags_at_tip" ]; then
    continue # not shipped (no tag) yet - nothing to back-merge
  fi

  ensure_pr_and_automerge "$release" "$maint"
done

# --- 2. maint/vX.Y.x -> next newer maint line -------------------------------

log "Checking maint -> maint forward merges..."
for ((i = 0; i < ${#MAINT_BRANCHES[@]} - 1; i++)); do
  older="${MAINT_BRANCHES[$i]}"
  newer="${MAINT_BRANCHES[$((i + 1))]}"
  ensure_pr_and_automerge "$older" "$newer"
done

# --- 3. newest maint line -> main -------------------------------------------

log "Checking newest maint -> main..."
newest_maint="${MAINT_BRANCHES[$((${#MAINT_BRANCHES[@]} - 1))]}"
ensure_pr_and_automerge "$newest_maint" "$MAIN_BRANCH"

log "Cascade check complete."
