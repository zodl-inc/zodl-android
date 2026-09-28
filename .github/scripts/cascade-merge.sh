#!/usr/bin/env bash
# Automates the forward-merge cascade: release/vX.Y.Z (shipped) -> maint/vX.Y.x
# -> next maint line -> ... -> main. See the zodl_an_cascade_merge skill for the
# full model this implements.
#
# For every pending link this opens (or reuses) a PR. maint->maint and the
# newest-maint->main links get GitHub's native auto-merge (method: merge,
# never squash/rebase - a squashed or rebased cascade link poisons every
# cascade after it, see the skill §3-4). release->maint links never get
# auto-merge: release prep re-headers the CHANGELOG's `## [Unreleased]`
# section into `## [X.Y.Z]`, and a plain git merge can silently re-parent
# maint-only entries added after the cut under that release heading - a human
# has to eyeball the CHANGELOG diff (see PR #2499 for a worked example).
#
# A PR carrying the `cascade-hold` label is left alone (auto-merge is never
# (re-)armed on it) so a human can park a link without closing the PR - it
# would just reopen on the next tick otherwise, since duplicates are only
# suppressed against an *open* PR for the same head/base.
#
# Requires: git, gh (authenticated via GH_TOKEN), a full clone (fetch-depth: 0).
#
# Note for a local dry-run: needs bash >= 4 (mapfile) - stock macOS bash (3.2)
# doesn't have it; use Homebrew bash.
set -euo pipefail

MAIN_BRANCH="main"
REMOTE="origin"
REVIEWER="${CASCADE_REVIEWER:-}" # optional gh handle/team to request review from

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

cascade_pr_body() {
  local source="$1" target="$2" auto_merge="$3"
  local preamble
  preamble=$(cat <<'BODY'
Automated forward-merge cascade (release -> maint -> main).

Opened by `.github/workflows/cascade-merge.yml`. Lands as a real two-parent
merge commit only - squash and rebase are never used here on purpose (a
squashed or rebased cascade link poisons every cascade after it - see the
`zodl_an_cascade_merge` skill, §3-4, for the two times this already happened
by hand).

CI note: this PR was opened with the workflow's own token, which does not
trigger `pull_request`-triggered checks on it, and this ruleset has no
required check today - please confirm CI already passed on the source branch
before approving.
BODY
  )
  if [ "$auto_merge" = "true" ]; then
    cat <<BODY
$preamble

Auto-merge is armed with the **merge** method - it lands the instant this gets
1 approval. Add the \`cascade-hold\` label to pause that without closing the PR.
BODY
  else
    cat <<BODY
$preamble

**This link always needs a manual merge** (never auto-armed): a release ->
maint back-merge can require re-parenting CHANGELOG.md entries by hand so
unreleased maint fixes don't read as shipped in the release (see PR #2499 for
a worked example). Please eyeball CHANGELOG.md and docs/whatsNew, then land it
with **Merge pull request** - never squash or rebase.
BODY
  fi
}

# ensure_cascade_pr <source> <target> <auto_merge: true|false>
# Opens (or reuses) a PR from <source> into <target>. When auto_merge is
# "true" and the PR is cleanly mergeable, enables native GitHub auto-merge
# (merge-commit method). Never auto-resolves a conflict - always leaves it for
# a human.
ensure_cascade_pr() {
  local source="$1" target="$2" auto_merge="$3"

  if is_ancestor "$source" "$target"; then
    log "  [skip] $source is already merged into $target"
    return 0
  fi

  local pr_number
  pr_number=$(gh pr list --head "$source" --base "$target" --state open --json number -q '.[0].number // empty')

  if [ -z "$pr_number" ]; then
    log "  [new] opening PR: $source -> $target"
    local title body reviewer_args=()
    title=$(pr_title "$source" "$target")
    body=$(cascade_pr_body "$source" "$target" "$auto_merge")
    [ -n "$REVIEWER" ] && reviewer_args=(--reviewer "$REVIEWER")

    # `gh pr create` has no --json/-q output mode - it prints the PR URL to
    # stdout on success. Errors are NOT swallowed here on purpose: a real
    # failure (auth, network, an unexpected existing-PR state) should be
    # visible in the run log rather than silently skipped.
    local pr_url
    pr_url=$(gh pr create --base "$target" --head "$source" --title "$title" --body "$body" "${reviewer_args[@]}") || {
      log "  [warn] could not open PR for $source -> $target - skipping this link this run"
      return 0
    }
    pr_number="${pr_url##*/}"
    log "  [new] opened PR #$pr_number"
  else
    log "  [reuse] PR #$pr_number ($source -> $target)"
  fi

  if [ "$auto_merge" != "true" ]; then
    log "  [manual] $source -> $target always needs a human merge - auto-merge not armed"
    return 0
  fi

  local labels
  labels=$(gh pr view "$pr_number" --json labels -q '[.labels[].name] | join(",")')
  if [[ ",$labels," == *",cascade-hold,"* ]]; then
    log "  [hold] PR #$pr_number is labeled cascade-hold - leaving auto-merge off"
    return 0
  fi

  local mergeable is_draft
  mergeable=$(gh pr view "$pr_number" --json mergeable -q '.mergeable')
  is_draft=$(gh pr view "$pr_number" --json isDraft -q '.isDraft')

  case "$mergeable" in
    MERGEABLE) ;;
    UNKNOWN)
      log "  [pending] PR #$pr_number mergeability not computed yet - will check again next run"
      return 0
      ;;
    *)
      log "  [conflict] PR #$pr_number ($source -> $target) is not cleanly mergeable (state: $mergeable) - left for manual resolution, auto-merge NOT enabled"
      return 0
      ;;
  esac

  local already_auto
  already_auto=$(gh pr view "$pr_number" --json autoMergeRequest -q '.autoMergeRequest // empty')
  if [ -n "$already_auto" ]; then
    log "  [ok] PR #$pr_number already has auto-merge enabled"
    return 0
  fi

  log "  [auto-merge] enabling native auto-merge (merge commit) on PR #$pr_number"
  gh pr merge "$pr_number" --auto --merge || {
    if [ "$is_draft" = "true" ]; then
      log "  [warn] PR #$pr_number is a draft - auto-merge can't be enabled until it's marked ready for review"
    else
      log "  [warn] could not enable auto-merge on PR #$pr_number (needs a human to check, e.g. missing required checks)"
    fi
  }
}

# --- 1. release/vX.Y.Z (shipped) -> its own maint/vX.Y.x -------------------
# Never auto-merged - see cascade_pr_body for why.

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
    log "  [unmatched-release] $release has no matching $maint branch yet - skipping"
    continue
  fi

  tip=$(git rev-parse "${REMOTE}/${release}")
  tags_at_tip=$(git tag --points-at "$tip")
  if [ -z "$tags_at_tip" ]; then
    continue # not shipped (no tag) yet - nothing to back-merge
  fi

  ensure_cascade_pr "$release" "$maint" "false"
done

# --- 2. maint/vX.Y.x -> next newer maint line -------------------------------

log "Checking maint -> maint forward merges..."
for ((i = 0; i < ${#MAINT_BRANCHES[@]} - 1; i++)); do
  older="${MAINT_BRANCHES[$i]}"
  newer="${MAINT_BRANCHES[$((i + 1))]}"
  ensure_cascade_pr "$older" "$newer" "true"
done

# --- 3. newest maint line -> main -------------------------------------------

log "Checking newest maint -> main..."
newest_maint="${MAINT_BRANCHES[$((${#MAINT_BRANCHES[@]} - 1))]}"
ensure_cascade_pr "$newest_maint" "$MAIN_BRANCH" "true"

log "Cascade check complete."
