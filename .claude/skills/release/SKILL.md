---
name: release
description: "Release a new version of the mock-brevo fork. Use when you bump the version, tag a release, or merge an upstream release."
---

# Releasing (this fork)

The fork uses its own SemVer (see README "Fork versions"); `pom.xml` `<upstream.version>` records the upstream base.

1. Collect changes under `## [Unreleased]` in `CHANGELOG.md` as you go. In a PR, bump with upstream's script: `scripts/new_version.sh 1.1.0 --bump-only`. Run it **without** `-m`: then it moves the `[Unreleased]` content under the new version (with `-m` it inserts a new section and leaves `[Unreleased]` behind). It updates `pom.xml` and `CHANGELOG.md` without committing, and calls `./mvnw`, so run it where Java 25 is available (or in `eclipse-temurin:25-jdk`). Then edit the entry to state the upstream base, and add the version to the README's "Fork versions" table.
2. Merge the PR, then tag `main`: `git tag -a v1.1.0 -m "…" && git push origin v1.1.0`. `release.yml` publishes `1.1.0`, `1.1`, `1` and `latest`; `latest` only ever comes from a release tag (a manual `workflow_dispatch` run from `main` publishes just a `main` tag; its `version` input sets only the `IMAGE_VERSION` build-arg, not the image tags).
3. Only tag forward: re-running an older release moves `latest` back to it.

Only the tag push runs `release.yml`, so CI does not test its steps. Before you tag, run the step that reads the upstream base on the branch you tag. It must print exactly one version. The command is a copy of the `upstream` step in `release.yml`; if you change one, change the other:

```bash
sed -n 's:.*<upstream.version>\([^$<][^<]*\)</upstream.version>.*:\1:p' pom.xml | head -n1
```

`pom.xml` has a second `<upstream.version>` line (`${upstream.version}`, in the `build-info` config). This broke the `v1.1.0` run, and the step now skips values that start with `$`. If a release run fails **before** it publishes an image (check `docker manifest inspect ghcr.io/unibrain1/mock-brevo:<version>`), fix it on `main`, delete the remote tag and tag again. If it published anything, release the next patch version instead.

Keep only the `origin` remote (unibrain1/mock-brevo). Do not keep a permanent `upstream` remote: with one, `gh` can pick c0boleis/mock-brevo as the target of `gh pr create`. In each clone:

- If a clone still has the old `upstream` remote (and its `tagOpt=--no-tags` setting), run `git remote remove upstream`.
- If a clone has a remote other than `origin`, run `gh repo set-default unibrain1/mock-brevo` once. `gh` then uses the fork for read and write commands. With only `origin`, `gh` has no other repo to pick.
- Also pass `--repo unibrain1/mock-brevo` to `gh` write commands, as a second safeguard.

To merge an upstream release, fetch it from the URL. This adds no remote and imports no tags (an upstream tag such as `v1.1.0` would clash with the fork's own). `--no-ff` always makes a merge commit:

```bash
git fetch --no-tags https://github.com/c0boleis/mock-brevo.git refs/tags/vX.Y.Z   # an upstream tag, or a branch name
git merge --no-ff FETCH_HEAD
# then update <upstream.version> in pom.xml
```
