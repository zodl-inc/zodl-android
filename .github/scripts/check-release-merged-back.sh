#!/usr/bin/env bash
#
# Check that every tagged release/ branch has been merged back into its
# maintenance line, and forward down to main.
#
# Usage: check-release-merged-back.sh [pr-base] [pr-ref]
#
#   pr-base   branch a pending change targets; that branch is then evaluated
#             as it would be with the change applied. Omit to check the
#             branches as they stand.
#   pr-ref    the pending change; defaults to HEAD (refs/pull/N/merge in CI).
#
# A release/ branch counts as released only when a tag points at its tip, so
# in-flight release branches are skipped. The maintenance line comes from the
# TAG, not the branch name: in the Swift SDK, release/3.0.0 is tagged 2.8.0-rc.1
# and belongs to maint/v2.8.x.
#
# Branches resolve under refs/remotes/$REMOTE, which defaults to origin -- what
# CI checks out. Set REMOTE to run this against a local clone whose canonical
# remote has another name, and run `git fetch` first.
#
# Exits 1 when a tagged release is missing from a branch it should be in.

set -euo pipefail

PR_BASE="${1:-}"
PR_REF="${2:-HEAD}"
REMOTE="${REMOTE:-origin}"

say() {
  printf '%s\n' "$*"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$*" >>"$GITHUB_STEP_SUMMARY"; fi
}

# Maintenance lines in version order, then main. v:refname sorts v2.10.x after
# v2.9.x.
CHAIN=()
while IFS= read -r ref; do
  CHAIN+=("$ref")
done < <(
  git for-each-ref --sort=v:refname --format='%(refname:lstrip=3)' \
    "refs/remotes/${REMOTE}/maint/v*"
)
CHAIN+=('main')

# Resolve a chain branch, substituting the pending change when it targets it.
resolve() {
  if [ -n "$PR_BASE" ] && [ "$1" = "$PR_BASE" ]; then
    git rev-parse "$PR_REF"
  else
    printf '%s/%s\n' "$REMOTE" "$1"
  fi
}

say '## Release branches merged back'
say ''

failed=0
checked=0

while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  ref="${REMOTE}/${rel}"

  tags="$(git tag --points-at "$ref" | tr '\n' ' ')"
  tags="${tags% }"
  if [ -z "$tags" ]; then
    say ":grey_question: \`${rel}\` has no tag at its tip; not a released branch, skipped."
    continue
  fi

  # [v]MAJOR.MINOR.PATCH[-anything] -> maint/vMAJOR.MINOR.x. The optional
  # trailing part covers both a pre-release suffix (2.8.0-rc.1) and the build
  # number some repos append (3.9.3-2393).
  tag="${tags%% *}"
  ver="${tag#v}"
  major="${ver%%.*}"
  rest="${ver#*.}"
  minor="${rest%%.*}"
  maint="maint/v${major}.${minor}.x"

  # Its own line is the obligation. Anything downstream of it is merge-forward
  # lag, which the merge-forward jobs already track, so it warns rather than
  # fails; otherwise the merge-back PR itself would report red for main.
  # A retired line is simply absent from the chain.
  own=''
  downstream=()
  seen=0
  for b in "${CHAIN[@]}"; do
    if [ "$b" = "$maint" ]; then seen=1; own="$b"; continue; fi
    if [ "$seen" -eq 1 ]; then downstream+=("$b"); fi
  done
  if [ "$seen" -eq 0 ]; then
    own='main'
    downstream=()
    say ":grey_question: \`${rel}\` (\`${tag}\`) belongs to \`${maint}\`, which no longer exists; checking main only."
  fi

  checked=$((checked + 1))

  lagging=()
  for b in ${downstream[@]+"${downstream[@]}"}; do
    if ! git merge-base --is-ancestor "$ref" "$(resolve "$b")"; then
      lagging+=("$b")
    fi
  done

  if ! git merge-base --is-ancestor "$ref" "$(resolve "$own")"; then
    failed=1
    say ":x: \`${rel}\` (\`${tag}\`) is **not** merged back into \`${own}\`."
  elif [ "${#lagging[@]}" -ne 0 ]; then
    list="$(printf '%s, ' "${lagging[@]}")"
    say ":warning: \`${rel}\` (\`${tag}\`) is in \`${own}\` but has not reached ${list%, } yet."
  else
    say ":white_check_mark: \`${rel}\` (\`${tag}\`) is in \`${own}\` and downstream."
  fi
done < <(
  git for-each-ref --sort=v:refname --format='%(refname:lstrip=3)' \
    "refs/remotes/${REMOTE}/release/*"
)

say ''

if [ "$checked" -eq 0 ]; then
  say 'No tagged release branches to check.'
  exit 0
fi

if [ "$failed" -eq 0 ]; then
  say "All ${checked} tagged release branches are merged back into their line."
  exit 0
fi

say 'Advisory only. Merge the release branch back into its maintenance line and'
say 'let it flow forward, rather than cherry-picking, so the tag stays reachable.'
exit 1
