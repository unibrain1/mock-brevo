# CLAUDE.md

## Project Intent

`mock-brevo` is a local mock of the [Brevo](https://developers.brevo.com/) (ex-Sendinblue) transactional API, used by client apps in dev and integration tests. Clients point at this server instead of `api.brevo.com`; request/response shapes are preserved so the Brevo PHP SDK works unchanged.

## Stack

- Jackson 3 (`tools.jackson.*`); DTO annotations stay in `com.fasterxml.jackson.annotation`

## Build & run

The Maven wrapper (`./mvnw`) is checked in — no system Maven install required. Java 25 on `PATH` is the only prerequisite. First invocation downloads Maven 3.9.16 into `~/.m2/wrapper/`.

If the host has no Java 25 (`Unable to locate a Java Runtime`), run the wrapper in a container. The named volume keeps the Maven cache between runs. On Docker Desktop (macOS):

```bash
docker run --rm -v "$PWD":/w -v mock-brevo-m2:/root/.m2 -w /w eclipse-temurin:25-jdk ./mvnw -q -Plint verify
```

On a Linux host, run as your own user and bind-mount your Maven cache, or `target/` becomes owned by root (a new named volume would be owned by root too):

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$HOME/.m2":/tmp/.m2 \
  -v "$PWD":/w -w /w eclipse-temurin:25-jdk ./mvnw -q -Plint verify
```

```bash
./mvnw -Plint verify                            # what CI runs: javac -Xlint -Werror + SpotBugs + tests
docker compose up -d --build                    # containerized; H2 file volume-mounted at /app/data
```

Web/doc linting and browser tests use Node (`npm ci` first; not needed to build or run the app):

```bash
MOCK_BREVO_URL=http://localhost:8080 npm run test:ui   # Playwright, against a running server
yamllint -s .                                   # YAML
```

`package.json` has npm `overrides` for `katex` and `smol-toml`. They fix Dependabot alerts in `markdownlint-cli2`'s dependencies, which pins the vulnerable versions. After each `markdownlint-cli2` bump, remove both overrides, run `npm install` and `npm ls katex smol-toml`. If a package then resolves to a patched version, leave its override out. The tooling needs Node `^22.13 || >=24` (`engines` in `package.json`). ESLint 10 leaves out Node 23, other lint dependencies need Node 22 or later, and the katex override needs 22.12 or later.

Read the exit code of every lint and test command. Do not pipe them through `tail` or `grep` in a way that hides a failure: `npx markdownlint-cli2 … | tail -1` reported success while it had an error, and CI caught it later.

CI (`.github/workflows/`): `ci.yml` (Java lint + API tests, Playwright UI tests, Docker smoke), `lint.yml` (web/docs, YAML, actionlint, hadolint), `claude-code-review.yml` (Claude PR review with a live progress checklist and inline comments on lines; the check fails if this run posted no review, or if the "Blocking" section has content) and `claude.yml` (`@claude` mentions); both need the `CLAUDE_CODE_OAUTH_TOKEN` secret. SpotBugs exclusions live in `spotbugs-exclude.xml`; add a reason for each.

The `claude-code-review.yml` checklist mirrors the rules in this file, in `.claude/skills/` and in `.claude/rules/admin-ui.md`. When you change one of these rules, also change the workflow prompt.

The H2 console is mounted at `/h2-console` (JDBC URL visible in startup logs).

Check a change against the real Brevo PHP SDK with the `check-brevo-sdk` skill (`.claude/skills/check-brevo-sdk/SKILL.md`).

## Architecture

### Account auto-provisioning (central invariant)

Every incoming request under `/v3/**` carries an `api-key` header. The `ApiKeyInterceptor` resolves or creates an `Account` for that key, attaches it to the request, and exposes it to controllers via `CurrentAccount.require()`. Consequences:

- **Unknown key ≠ error.** Only missing/blank keys return 401. Any other key provisions a new `Account` (+ a default `Sender`) lazily.
- **Every entity is account-scoped.** Contact, ContactList, Folder, SmtpTemplate, EmailCampaign, SentEmail, Sender all carry a `@ManyToOne account_id`. Queries filter on it. Admin routes look up by `apiKey`.
- **No global data.** Two tenants with different keys see independent worlds.

### Transactional boundaries

Lazy collections (notably `Contact.lists`) are iterated in controllers during response building. `open-in-view: false` is set, so methods that traverse lazy relations must be annotated with `@Transactional` from **`org.springframework.transaction.annotation`** (not `jakarta.transaction.Transactional`). The Jakarta annotation doesn't trigger Spring's proxy, which will surface as `LazyInitializationException`.

### Admin (non-Brevo) routes

The admin routes are in `MockStatusController`, `MockAccountController` and `MockWebhooksController`.

- `DELETE /mock-status/accounts/{apiKey}` (`MockAccountController`) deletes one account and its rows in one transaction, with the account row locked. It deletes child rows first, in the order of `MockResetController`.
- If you add an account-scoped entity, add its `deleteByAccount` to `MockAccountController` and its delete to `MockResetController` (`/mock/reset`).
- In the UI, each account row's "⋯" menu has "Delete this key…" and "New campaign…".

### Email events (shared store)

`EmailEvent` rows record every send (one `request` event per to/cc/bcc recipient) and every simulated webhook event (manual fire and auto-fired `delivered`), keyed by account. The webhook, the event report and the block list read this one table, so one simulated event looks the same everywhere. `event` holds the webhook name (`hard_bounce`, …). The account comes from the sent email that `messageId` names, else from the fire body's `apiKey`. With neither, the webhook is still sent but nothing is recorded (`"recorded": false`). An `apiKey` with another account's `messageId`, or a value longer than its column, returns 400. The webhook is sent after the event row commits, so a receiver that reads the event report finds it. `POST /mock/reset` clears the table.

A recorded `hard_bounce`, `spam` or `unsubscribed` also adds the recipient to the account's `BlockedContact` list (reason `hardBounce`, `contactFlaggedAsSpam`, `unsubscribedViaEmail`). One entry per (account, email); the latest block wins. `senderEmail` falls back to the account's own address, because the Brevo SDK model requires it. `POST /mock/reset` clears it too.

Webhooks copy `tags`, `subject`, `sender_email`, `template_id` and `X-Mailin-custom` from the sent email's stored payload. The bearer token (`token` in the fire body, else `MOCK_WEBHOOK_TOKEN`) is sent as `Authorization: Bearer …` and is never logged. The outbound `RestClient` uses HTTP/1.1 with a buffered body (so a `Content-Length`, no chunked, no h2c upgrade), as real Brevo does.

### Admin UI rules (`src/main/resources/static/`)

The admin UI rules are in `.claude/rules/admin-ui.md`. Claude Code loads them when a session works on a file in `src/main/resources/static/`.

### Request logging

`RequestLoggingFilter` (order = `HIGHEST_PRECEDENCE + 10`) matches `/v3/**` and `/mock-webhooks/**` via `AntPathMatcher` and appends to `RequestLogStore` (synchronized `ArrayDeque`, capped at 500). The filter wraps `chain.doFilter` in a try/finally so errors and 4xx/5xx responses are still captured. The api-key header is masked (first 6 + last 4 chars) before storage — never log raw keys regardless of `MOCK_STATUS_REVEAL_KEYS`. In a `/mock-webhooks/**` request body, a `"token"` field is replaced with `"***"` and an `"apiKey"` field is masked like the header, before storage. Mask any new secret field in a logged body the same way. Memory only; ring buffer resets on restart.

### Config properties

`MockBrevoProperties` defines them (`mock-brevo.*` prefix, bound in `application.yml`). The README and `application.yml` list all of them.

- **Never log a send's body or secrets.** `log-sends` (`MOCK_LOG_SENDS`, default `true`) logs one INFO line per send (after commit): `messageId`, addresses, subject, `templateId`, `tags`. Never the body, attachments, `params`, `headers` or the api-key. Control characters in client values become spaces.

## Working on this repo

### Branches and PRs

- A milestone gets a branch `milestone/vX.Y.Z` from `main`. Each issue gets a branch from it (`issue/<n>-<slug>`) and a PR into the milestone branch. One PR merges the milestone branch into `main`.
- GitHub closes an issue from "Closes #N" only when the PR merges into `main`. Put "Closes #…" for every milestone issue in the milestone → `main` PR.
- **Stacked PRs:** before you merge a PR with `--delete-branch`, move every PR that is based on its branch to the milestone branch (`gh pr edit <n> --base milestone/vX.Y.Z`). Deleting a base branch **closes** the PRs on it; it does not move them.
- Each branch adds its own line under `## [Unreleased]` in `CHANGELOG.md`, so a rebase often conflicts there. Keep every branch's line.

### The Claude review

- Fix every **Blocking** and **Important** finding. Take one round of **Suggestions**, then reply with what you did not change and why, and merge. Do not loop on optional suggestions.
- "Claude encountered an error" in the review comment means the workflow failed, not the code. The `claude-review` check then fails with "No Claude review comment from this run". Run the review again. Do not use an older review.

### Tests

- A new test must **fail** when you revert the fix. Check this once: revert the fix, run the test, restore the fix. In this repo two tests passed with the bug still present: a log-truncation test whose cut fell before the token, and an end-to-end test of the webhook order.
- Do not test the order of `@Async` work end to end. The other thread usually wins the race anyway. Mock the async bean and call the method in a `TransactionTemplate` (see `WebhookAfterCommitTest`).

### Docker disk

Image checks fill the Docker disk quickly, and on Docker Desktop the disk is shared by every project (`No space left on device`, `Unable to create tempDir`). Use one scratch tag (such as `mock-brevo:check`), and remove the containers and images you create when the check is done. `docker builder prune -f` frees the build cache, but ask first: the cache can belong to other projects.

## Releasing (this fork)

Use the `release` skill (`.claude/skills/release/SKILL.md`) for the release steps.

Keep only the `origin` remote (unibrain1/mock-brevo). Do not keep a permanent `upstream` remote: with one, `gh` can pick c0boleis/mock-brevo as the target of `gh pr create`. In each clone:

- If a clone still has the old `upstream` remote (and its `tagOpt=--no-tags` setting), run `git remote remove upstream`.
- If a clone has a remote other than `origin`, run `gh repo set-default unibrain1/mock-brevo` once. `gh` then uses the fork for read and write commands. With only `origin`, `gh` has no other repo to pick.
- Also pass `--repo unibrain1/mock-brevo` to `gh` write commands, as a second safeguard.

## Endpoint coverage

The original priority list is in `ENDPOINTS.md`. The upstream author wrote it for their own app, so its "Called from" column names that app's call sites. `ENDPOINTS.md` and the README list what is implemented.

Add a new Brevo endpoint with the `add-endpoint` skill. Match Brevo JSON field names exactly, and scope every query by account.

## Gotchas

- **Don't enable `AUTO_SERVER=TRUE` on the H2 JDBC URL.** It triggers `NoClassDefFoundError: org/h2/util/NetworkConnectionInfo` under Spring Boot's nested-jar classloader when a second JVM (e.g. parallel tests) tries to connect. The datasource URL in `application.yml` is plain file mode.
- **Lombok annotation processing** must stay enabled. Entities rely on `@Getter`/`@Setter`. Since JDK 23 javac only runs processors listed in `annotationProcessorPaths` (see `pom.xml`); "cannot find symbol" on getters means Lombok didn't run.
- **Async work that reads a row needs `afterCommit`.** `SmtpForwarder.forward` is `@Async` and reads the `SentEmail` by id, and `WebhookSender.send` must not reach a receiver before the event row exists. Inside a transaction, register them with `TransactionSynchronizationManager.registerSynchronization(... afterCommit ...)`, as `TransactionalEmailController.send` and `WebhookService.fire` do.
- **Keep `Cache-Control: no-cache` on the admin UI files** (`spring.web.resources.cache` in `application.yml`). Without it, a browser used an `app.js` from an older image with a newer `index.html`, and the FR/EN toggle did nothing. After such an upgrade, one hard reload is still necessary.
- **Brevo doc links use kebab-case slugs** (`get-transac-blocked-contacts`). Brevo renames reference pages from time to time. After you change `DOC_ROUTES` in `app.js`, run `scripts/check-doc-links.sh`: it requests every slug and exits 1 if one does not return 200.
- **Don't actually send email.** `POST /v3/smtp/email` stores the payload and returns a synthetic `messageId`. The only outbound mail path is the opt-in `SmtpForwarder` (`MOCK_SMTP_ENABLED`, off by default), meant for a local catcher such as Mailpit; don't add any other transport or enable it by default.
