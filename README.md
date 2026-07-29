[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e6e9a17f8190438e83874c1b3f7cb62f)](https://www.codacy.com/gh/codacy/codacy-shellcheck?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=codacy/codacy-shellcheck&amp;utm_campaign=Badge_Grade)
[![Build Status](https://circleci.com/gh/codacy/codacy-shellcheck.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/codacy/codacy-shellcheck)

# Codacy ShellCheck

Create the docker:

```bash
sbt universal:stage graalvm-native-image:packageBin
docker build -t codacy-shellcheck .
```

The docker is supposed to be run with the following command:

```sh
docker run -it -v $srcDir:/src  <DOCKER_NAME>:<DOCKER_VERSION>
```

and $srcDir must contain a valid `.codacy.json` configuration

## ShellCheck Configuration

This wrapper supports ShellCheck configuration files. You can place a `.shellcheckrc` file in your repository root to configure ShellCheck behavior.

### Example `.shellcheckrc`

```
disable=SC2086,SC2181
enable=optional
external-sources=true
```

For more information about ShellCheck configuration options, visit the [ShellCheck wiki](https://www.shellcheck.net/)

## Docs

[Docker Docs](https://support.codacy.com/hc/en-us/articles/207994725-Tool-Developer-Guide)

[Scala Docker Template Docs](https://support.codacy.com/hc/en-us/articles/207280379-Tool-Developer-Guide-Using-Scala)

## Test

For a faster development loop you can create a Docker image based on the JVM instead of creating a native-image:

```bash
sbt universal:stage
docker build -t codacy-shellcheck --target dev .
```

Follow the instructions at [codacy-plugins-test](https://github.com/codacy/codacy-plugins-test/blob/master/README.md#test-definition)

## Generating the documentation

1. Update the `VERSION` variable in `generate.sh` and run:

```bash
cd doc-generator
./generate.sh
```

This will create updated `patterns.json`, `description.json` and the individual documentation Markdown files.

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped ShellCheck version, but also base image / orb / dependency bumps. Follow it top to bottom; it tells you what to change, how to regenerate derived files, how to test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is a **Codacy engine**: a thin Scala wrapper (`src/main/scala/codacy/shellcheck/ShellCheck.scala`, built on `codacy-engine-scala-seed`) that packages [ShellCheck](https://www.shellcheck.net/) (a Haskell-based shell script linter) as a Docker image Codacy's platform can run against a customer's source code. ShellCheck itself is **not compiled from source** here — the `Dockerfile` downloads a pre-built Linux x86_64 binary release directly from `koalaman/shellcheck`'s GitHub Releases and copies it to `/usr/bin`. The wrapper Scala code simply shells out to the `shellcheck` binary (`shellcheck -x -f json ...`) and parses its JSON output (see `ShellCheck.apply` in `ShellCheck.scala`).

The `docs/` directory is not just documentation — it is **machine-consumed configuration**:

- `docs/patterns.json` — the full list of ShellCheck rules ("patterns", e.g. `SC2086`), their category/level, and which are enabled out of the box. Generated file, do not hand-edit.
- `docs/description/description.json` + `docs/description/SC*.md` — human-readable titles/descriptions per pattern, used in the Codacy UI. Generated file, do not hand-edit.
- `docs/tests/*` and `docs/multiple-tests/*` — fixtures used by `codacy-plugins-test` to validate the engine actually produces the results it claims to for real shell script samples.
- `docs/tool-description.md` — short blurb about the tool, hand-maintained.

All the generated artifacts above come from **`doc-generator/generate.sh`**, a bash script that clones the real `koalaman/shellcheck` repo at tag `v<VERSION>` and `koalaman/shellcheck.wiki` (the wiki holds one page per rule, used as the source for descriptions), then cross-references `doc-generator/categories.json` (per-pattern Codacy category/subcategory overrides) and `doc-generator/defaults.json` (list of patterns enabled by default) to build `patterns.json`, `description.json`, and one `docs/description/SC####.md` file per rule. This script needs **git, network access, and `jq`** installed locally — no pandoc, no sbt.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `Dockerfile` → `ARG SHELLCHECK_VERSION` | Which pre-built ShellCheck binary release is downloaded and packaged into the image | Bump to the target version. Confirm a matching `v<version>` release with a `.linux.x86_64.tar.xz` asset exists in [koalaman/shellcheck releases](https://github.com/koalaman/shellcheck/releases). |
| `doc-generator/generate.sh` → `VERSION` variable | Which git tag is cloned from `koalaman/shellcheck` (and its wiki) to regenerate `docs/patterns.json` / descriptions | **Must be kept in sync with the Dockerfile's `SHELLCHECK_VERSION`.** Note the historical inconsistency in git history: sometimes stored with a leading `v` (e.g. `v0.9.0`), sometimes without (e.g. `0.10.0`) — the script itself does `-b v$VERSION`, so only omit the `v` if the variable is used exactly like the current file does. |
| `build.sbt` → `scalaVersion` | Scala compiler version | Bump opportunistically alongside other changes if asked; not tied to ShellCheck version. |
| `build.sbt` → `codacy-engine-scala-seed` dependency | Codacy's engine SDK/base library | Check [Maven Central](https://mvnrepository.com/artifact/com.codacy/codacy-engine-scala-seed) for newer versions if asked to update it; not tied to ShellCheck bumps. |
| `project/build.properties` | sbt version | Bump if the build itself needs a newer sbt, or opportunistically alongside a version bump PR (seen in the `0.10.0` bump commit). |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI steps (checkout, versioning, sbt build, docker publish, tagging) | Check the latest published version, or use `git log -p .circleci/config.yml` to see prior bump history as a fallback reference. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI after the image is built | Same as above. |
| `Dockerfile` → base image (`alpine:3.21.3`) | OS the ShellCheck binary and JVM/native image run on | Only bump if asked explicitly, or if the target ShellCheck release requires a newer glibc/musl — don't bump opportunistically. |
| `src/main/scala/codacy/shellcheck/ShellCheck.scala` | Wrapper code that invokes `shellcheck` and parses its JSON output | Only needs changes if ShellCheck's CLI flags or JSON output schema changed between versions (rare) — check the release's changelog. |

### 3. Step-by-step update procedure

1. **Bump `SHELLCHECK_VERSION` in `Dockerfile`** and the matching `VERSION` in `doc-generator/generate.sh`, as scoped by the task. Bump CircleCI orbs / sbt / base image only if also in scope.
2. **Regenerate the docs.** Requires `git`, network access, and `jq` on `PATH`: `cd doc-generator && ./generate.sh`. Review the diff in `docs/patterns.json`, `docs/description/description.json`, and `docs/description/SC*.md` for new/removed/renamed rules.
3. **Build the JVM-based dev image** for a faster loop: `sbt universal:stage` then `docker build -t codacy-shellcheck --target dev .` (the default multi-stage target additionally requires `graalvm-native-image:packageBin`, matching what CI does: `sbt "scalafmt::test; test:scalafmt::test; sbt:scalafmt::test; universal:stage; graalvm-native-image:packageBin"`).
4. **Run `codacy-plugins-test` locally** before pushing — clone https://github.com/codacy/codacy-plugins-test and run its DockerTest commands (this repo's CI passes `run_multiple_tests: true`, i.e. the `multiple` test mode) against your local image tag.
5. **Iterate on failures**, re-running only the relevant DockerTest command after each fix.
6. **Commit** the version bump(s) together with the regenerated `docs/` files in one change.
7. **Push and open a PR.** CI (`.circleci/config.yml`) runs `codacy/checkout_and_version` → `publish_docker_local` (scalafmt checks, `universal:stage`, `graalvm-native-image:packageBin`, `docker build`) → `plugins_test` → `codacy/publish_docker` (master only) → `codacy/tag_version` (master only).
8. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (CircleCI API/UI for the failing job — don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. **The CI environment's toolchain can differ from your local one**, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human — in which case explain it in the PR rather than guessing.

### 4. Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `scalafmt::test` fails in CI/locally | Generated or hand-edited Scala file not formatted | Run `sbt scalafmt` then re-run the check command |
| `doc-generator/generate.sh` fails to clone or find rule pages | Wrong/nonexistent `v<VERSION>` tag, or the wiki page for a rule was renamed/removed upstream | Verify the tag exists in `koalaman/shellcheck` and that the wiki still documents the rule |
| `pattern`/`json` DockerTest fails | Rule renamed/removed/added upstream between ShellCheck versions | Re-run `generate.sh`; confirm the change matches ShellCheck's release notes |
| `multiple` DockerTest fails on a specific fixture folder in `docs/multiple-tests` | Expectations stale for new tool behavior | Regenerate/update the expected results to match the new (verified correct) output |
| CI `publish_docker`/`tag_version` don't run on your branch | Expected — gated to the `master` branch only | Nothing to fix |

### 5. Definition of done

- `SHELLCHECK_VERSION` (Dockerfile) and `VERSION` (doc-generator/generate.sh) bumped together and consistent with each other, plus any other in-scope version bumps (CircleCI orbs, sbt, base image).
- Generated docs (`docs/patterns.json`, `docs/description/*`) regenerated via `doc-generator/generate.sh` and committed, with fixture inconsistencies resolved.
- Local `sbt` build/format commands pass.
- Docker image builds successfully (dev target at minimum; native-image target if validating the full CI path).
- `codacy-plugins-test` commands pass locally against the freshly built image.
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure (fetch the real CI log, fix, push, re-poll) until all pass — a passing local build is not sufficient, because the CI toolchain can differ from your local one (see step 8).

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features:

- Identify new Static Analysis issues
- Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
- Auto-comments on Commits and Pull Requests
- Integrations with Slack, HipChat, Jira, YouTrack
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.

## License

Licensed under the Apache License, Version 2.0.
