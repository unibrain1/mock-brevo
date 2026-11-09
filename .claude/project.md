# Project facts for devkit commands

The flow commands read this file. Claude Code does not load it in a normal
session. Each fact is one line: a dash, the key, a colon, and the value in
backticks. The keys are in
<https://github.com/unibrain1/devkit/blob/main/docs/project-md.md>.

- test: `docker run --rm -v "$PWD":/w -v mock-brevo-m2:/root/.m2 -w /w eclipse-temurin:25-jdk ./mvnw -q -Plint verify`
- lint: `npm run lint && yamllint -s . && actionlint`
- deploy: see the `release` skill (`.claude/skills/release/SKILL.md`)
- plans: `docs/plans`
