# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Intent

`mock-brevo` is a local mock of the [Brevo](https://developers.brevo.com/) (ex-Sendinblue) transactional API, used by client apps in dev and integration tests. Clients point at this server instead of `api.brevo.com`; request/response shapes are preserved so the Brevo PHP SDK works unchanged.

## Stack

- Java 25, Spring Boot 4.1.x (Spring Web MVC, not WebFlux)
- Jackson 3 (`tools.jackson.*`); DTO annotations stay in `com.fasterxml.jackson.annotation`
- Spring Data JPA + H2 in file mode (persists across restarts)
- Lombok for entity boilerplate
- Maven (no Gradle)
- Docker for deployment

## Build & run

The Maven wrapper (`./mvnw`) is checked in — no system Maven install required. Java 25 on `PATH` is the only prerequisite. First invocation downloads Maven 3.9.16 into `~/.m2/wrapper/`.

```bash
./mvnw spring-boot:run                          # run from sources (port 8080, hot path for demos)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"
./mvnw clean package -DskipTests                # build fat jar → target/mock-brevo-*.jar
java -jar target/mock-brevo-*.jar           # run the packaged jar
./mvnw test                                     # API tests + JSON shape snapshots (in-memory H2)
./mvnw test -Dtest=ClassName#method             # run a single test
./mvnw -Plint verify                            # what CI runs: javac -Xlint -Werror + SpotBugs + tests
docker compose up -d --build                    # containerized; H2 file volume-mounted at /app/data
```

Web/doc linting and browser tests use Node (`npm ci` first; not needed to build or run the app):

```bash
npm run lint                                    # ESLint, HTMLHint, Stylelint, markdownlint
MOCK_BREVO_URL=http://localhost:8080 npm run test:ui   # Playwright, against a running server
yamllint -s .                                   # YAML
```

CI (`.github/workflows/`): `ci.yml` (Java lint + API tests, Playwright UI tests, Docker smoke), `lint.yml` (web/docs, YAML, actionlint, hadolint), `claude-code-review.yml` (Claude PR review with a live progress checklist; its checklist mirrors the rules in this file, and a non-empty "Blocking" section fails the check) and `claude.yml` (`@claude` mentions); both need the `CLAUDE_CODE_OAUTH_TOKEN` secret. SpotBugs exclusions live in `spotbugs-exclude.xml`; add a reason for each.

The H2 console is mounted at `/h2-console` (JDBC URL visible in startup logs).

## Architecture

Single Spring Boot module. Code layout:

```text
src/main/java/org/enoria/mockbrevo/
├── MockBrevoApplication.java        # @SpringBootApplication + @EnableAsync
├── config/                          # WebConfig (interceptor), AppConfig (beans), MockBrevoProperties
├── auth/                            # ApiKeyInterceptor, AccountService, CurrentAccount helper
├── domain/                          # JPA entities + repositories (Account, Sender, Contact, ContactList,
│                                    #   Folder, SmtpTemplate, EmailCampaign, SentEmail, EmailEvent)
├── brevo/                           # Controllers that mirror /v3/** Brevo endpoints
│   └── dto/                         # Jackson records for request/response payloads
├── admin/                           # MockStatusController, MockWebhooksController (non-Brevo admin routes)
├── events/                          # EmailEventService (shared email-event store)
└── webhook/                         # WebhookService (payload + record), WebhookSender (async outbound HTTP)
```

### Account auto-provisioning (central invariant)

Every incoming request under `/v3/**` carries an `api-key` header. The `ApiKeyInterceptor` resolves or creates an `Account` for that key, attaches it to the request, and exposes it to controllers via `CurrentAccount.require()`. Consequences:

- **Unknown key ≠ error.** Only missing/blank keys return 401. Any other key provisions a new `Account` (+ a default `Sender`) lazily.
- **Every entity is account-scoped.** Contact, ContactList, Folder, SmtpTemplate, EmailCampaign, SentEmail, Sender all carry a `@ManyToOne account_id`. Queries filter on it. Admin routes look up by `apiKey`.
- **No global data.** Two tenants with different keys see independent worlds.

### Transactional boundaries

Lazy collections (notably `Contact.lists`) are iterated in controllers during response building. `open-in-view: false` is set, so methods that traverse lazy relations must be annotated with `@Transactional` from **`org.springframework.transaction.annotation`** (not `jakarta.transaction.Transactional`). The Jakarta annotation doesn't trigger Spring's proxy, which will surface as `LazyInitializationException`.

### Admin (non-Brevo) routes

- `GET /` — static HTML UI (in `src/main/resources/static/index.html`). Two tabs: accounts + live REST call log. Polls `/mock-status` and `/mock-status/requests` every 2s. No build step — inline CSS/JS, served by Spring Boot's default static handler.
- `GET /mock-status` — list every provisioned account with counters (emails, contacts, lists, campaigns, templates, folders, senders). Toggle `MOCK_STATUS_REVEAL_KEYS=false` to mask raw keys.
- `GET /mock-status/version` — build version, upstream base and build time from `META-INF/build-info.properties` (generated by the `build-info` goal in `pom.xml`). The UI header shows it once at page load.
- `GET /mock-status/requests?limit=200&apiKey=…` — last N captured REST calls (in-memory ring buffer, max 500, anti-chronological). Entries include method, path, query, masked api-key, status, durationMs.
- `GET /mock-status/accounts/{apiKey}/emails` — inspect captured emails for a given tenant (payload + messageId) — this is the hook for test assertions.
- `POST /mock-webhooks/fire` — push a Brevo event webhook to a target URL. Used to simulate `delivered`, `opened`, `click`, `hard_bounce`, etc. against a client's webhook callback URL. Body: `url`, `event`, `email`, and optional `reason`, `messageId`, `token`, `tags`, `apiKey`, `link`. See README "Webhook simulation" for the field set per event.

### Email events (shared store)

`EmailEvent` rows record every send (one `request` event per to/cc/bcc recipient) and every simulated webhook event (manual fire and auto-fired `delivered`), keyed by account. The webhook, the event report and the block list read this one table, so one simulated event looks the same everywhere. `event` holds the webhook name (`hard_bounce`, …). The account comes from the sent email that `messageId` names, else from the fire body's `apiKey`. With neither, the webhook is still sent but nothing is recorded (`"recorded": false`). An `apiKey` with another account's `messageId`, or a value longer than its column, returns 400. The webhook is sent after the event row commits, so a receiver that reads the event report finds it. `POST /mock/reset` clears the table.

Webhooks copy `tags`, `subject`, `sender_email`, `template_id` and `X-Mailin-custom` from the sent email's stored payload. The bearer token (`token` in the fire body, else `MOCK_WEBHOOK_TOKEN`) is sent as `Authorization: Bearer …` and is never logged. The outbound `RestClient` uses HTTP/1.1 with a buffered body (so a `Content-Length`, no chunked, no h2c upgrade), as real Brevo does.

### Admin UI rules (`src/main/resources/static/`)

- **Every user-visible string goes through `I18N.t('key')`** (or a `data-i18n*` attribute in `index.html`), and the key must exist in **both** the `fr` and `en` dictionaries in `js/i18n.js`. No French or English literals in `app.js`.
- **Escape dynamic values** inserted into HTML with `esc()`. Only dictionary strings whose key ends in `Html` may be inserted unescaped.
- **Don't shadow `t`** (the translator) with a local variable in `app.js`.
- Plain ES2020 in an IIFE with `'use strict'`; no build step, no new runtime dependencies.

### Request logging

`RequestLoggingFilter` (order = `HIGHEST_PRECEDENCE + 10`) matches `/v3/**` and `/mock-webhooks/**` via `AntPathMatcher` and appends to `RequestLogStore` (synchronized `ArrayDeque`, capped at 500). The filter wraps `chain.doFilter` in a try/finally so errors and 4xx/5xx responses are still captured. The api-key header is masked (first 6 + last 4 chars) before storage — never log raw keys regardless of `MOCK_STATUS_REVEAL_KEYS`. A `"token"` field in a `/mock-webhooks/**` request body is replaced with `"***"` before storage. Memory only; ring buffer resets on restart.

### Config properties

Defined in `MockBrevoProperties` (`mock-brevo.*` prefix, bound in `application.yml`):

| Property | Env var | Default | Effect |
|----------|---------|---------|--------|
| `reveal-keys` | `MOCK_STATUS_REVEAL_KEYS` | `true` | Expose raw `apiKey` in `/mock-status` |
| `default-webhook-url` | `MOCK_DEFAULT_WEBHOOK_URL` | `""` | If set + `auto-fire-delivered=true`, fires `delivered` after every `POST /v3/smtp/email` |
| `auto-fire-delivered` | `MOCK_AUTO_FIRE_DELIVERED` | `false` | Enables the above |
| `webhook-token` | `MOCK_WEBHOOK_TOKEN` | `""` | Default bearer token for outbound webhooks (`Authorization: Bearer …`) |
| H2 file path | `MOCK_BREVO_DB_PATH` | `./data/brevo` | Controls `jdbc:h2:file:…` location |

## Releasing (this fork)

The fork uses its own SemVer (see README "Fork versions"); `pom.xml` `<upstream.version>` records the upstream base.

1. Collect changes under `## [Unreleased]` in `CHANGELOG.md` as you go. In a PR, bump with upstream's script: `scripts/new_version.sh 1.1.0 --bump-only`. Run it **without** `-m`: then it moves the `[Unreleased]` content under the new version (with `-m` it inserts a new section and leaves `[Unreleased]` behind). It updates `pom.xml` and `CHANGELOG.md` without committing, and calls `./mvnw`, so run it where Java 25 is available (or in `eclipse-temurin:25-jdk`). Then edit the entry to state the upstream base, and add the version to the README's "Fork versions" table.
2. Merge the PR, then tag `main`: `git tag -a v1.1.0 -m "…" && git push origin v1.1.0`. `release.yml` publishes `1.1.0`, `1.1`, `1` and `latest`; `latest` only ever comes from a release tag (a manual `workflow_dispatch` run from `main` publishes just a `main` tag; its `version` input sets only the `IMAGE_VERSION` build-arg, not the image tags).
3. Only tag forward: re-running an older release moves `latest` back to it.

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

## Endpoint coverage

The original priority list is in `ENDPOINTS.md`. The upstream author wrote it for their own app, so its "Called from" column names that app's call sites. In this repo today:

- **P0** `/v3/account`, `POST /v3/smtp/email`, `/v3/senders` — implemented
- **P1** `/v3/contacts/lists` (CRUD), `/v3/contacts/import` (CSV), `/v3/contacts/lists/{id}/contacts`, `PUT /v3/contacts/{email}` — implemented
- **P2** `/v3/emailCampaigns`, `POST /v3/emailCampaigns`, `POST /v3/emailCampaigns/{id}/sendNow` — implemented
- **P3** `/v3/smtp/templates` (GET/POST), `/v3/contacts/folders` (GET/POST) — implemented
- **P4** `POST /mock-webhooks/fire` — implemented (async, best-effort)
- **v1.2.0** `GET /v3/smtp/statistics/events` — implemented. It reads the email-event store and maps webhook names to the report's names (`hard_bounce` → `hardBounces`, `click` → `clicks`, …). Several send tags become one `tag` string joined with `|`, as in Brevo's CSV export. Invalid parameters return `400 {code: "invalid_parameter", message}`.

When adding a new Brevo endpoint: (1) add a DTO record in `brevo/dto/` with `@JsonIgnoreProperties(ignoreUnknown = true)` on request records (Brevo payloads often have optional fields we don't model); (2) make the controller method read `CurrentAccount.require()` first; (3) scope every query by account; (4) match Brevo's JSON field names exactly — the PHP SDK deserializes strictly; (5) add a `capture(...)` for it in `ApiShapeSnapshotTest` and record its snapshot with `./mvnw test -Dtest=ApiShapeSnapshotTest -Dsnapshots.update=true`. A failing snapshot means the JSON contract changed: fix the code, or regenerate only if the change is intended and review the diff in `src/test/resources/api-shapes/`.

## Gotchas

- **Don't enable `AUTO_SERVER=TRUE` on the H2 JDBC URL.** It triggers `NoClassDefFoundError: org/h2/util/NetworkConnectionInfo` under Spring Boot's nested-jar classloader when a second JVM (e.g. parallel tests) tries to connect. The datasource URL in `application.yml` is plain file mode.
- **Lombok annotation processing** must stay enabled. Entities rely on `@Getter`/`@Setter`. Since JDK 23 javac only runs processors listed in `annotationProcessorPaths` (see `pom.xml`); "cannot find symbol" on getters means Lombok didn't run.
- **Don't actually send email.** `POST /v3/smtp/email` stores the payload and returns a synthetic `messageId`. The only outbound mail path is the opt-in `SmtpForwarder` (`MOCK_SMTP_ENABLED`, off by default), meant for a local catcher such as Mailpit; don't add any other transport or enable it by default.
