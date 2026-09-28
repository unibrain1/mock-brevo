# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). From 1.1.0 on, this
fork (unibrain1/mock-brevo) follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
with its own version numbers; each release notes the upstream
(c0boleis/mock-brevo) version it is based on.

## [Unreleased]

## [1.2.0] — 2026-09-28

Fork release, based on upstream 1.0.0. ElanRegistry compatibility: event report, block list and webhooks that match real Brevo.

- Admin UI: every Brevo doc link in the request log uses Brevo's current kebab-case reference slugs. 23 of them returned 404 (#29). `POST /v3/smtp/templates/{id}/sendTest` replaces the old `sendTemplate` route. New `scripts/check-doc-links.sh` checks every slug.
- New endpoints `GET /v3/smtp/blockedContacts` and `DELETE /v3/smtp/blockedContacts/{email}`, the transactional block list (#18). A fired `hard_bounce`, `spam` or `unsubscribed` blocks the recipient, with Brevo's reason codes and messages. Supports `startDate`/`endDate`, `limit` (max 100), `offset`, `senders` and `sort`. Admin routes `GET|POST /mock-status/accounts/{apiKey}/blocked` and `DELETE …/blocked/{email}` read and seed the list. The admin UI shows a `blocked` counter with a drill-down. Checked with the Brevo PHP SDK v1.0.2 `getTransacBlockedContacts` and `smtpBlockedContactsEmailDelete` calls.
- New endpoint `GET /v3/smtp/statistics/events`, the transactional email event report (#17). It lists one `requests` event per recipient of each send and every simulated webhook event, with Brevo's report event names, filters, paging, `sort` and `400 {code, message}` errors. Checked with the Brevo PHP SDK v1.0.2 `getEmailEventReport` call. The admin UI's request log now links the event report to the Brevo doc, and `GET /v3/smtp/emails` now links to the correct doc page. `POST /v3/smtp/email` now returns `400 {code, message}` for a recipient address longer than 320 characters.
- Webhooks match real Brevo deliveries (#19). They send an optional bearer token (`token` in the fire body, or `MOCK_WEBHOOK_TOKEN`), copy `tags`, `tag`, `subject`, `sender_email`, `template_id` and `X-Mailin-custom` from the sent email, add `id`, `ts_event`, `ts_epoch`, `uuid` and `sending_ip`, and use Brevo's `YYYY-MM-DD HH:MM:SS` `date` and field set per event. They go out as HTTP/1.1 with a `Content-Length`. `POST /mock-webhooks/fire` also accepts `tags`, `apiKey` and `link`, and returns `recorded`. The request log masks the fire body's `token` and `apiKey`. With no known sent email, the webhook leaves out `subject`, `sender_email` and `template_id` instead of sending `null` (the other fields are always present). Every fired event is stored in a new shared email-event table, which `POST /mock/reset` clears.
- Docs: README, CLAUDE.md, config comments and the pom description no longer name the upstream author's app. The README explains the API base URL and web UI base URL for apps in Docker without app-specific variables (#16).

## [1.1.0] — 2026-09-27

Fork release, based on upstream 1.0.0. First release with the fork's own SemVer.

- Admin UI: the header shows the build version (for example `v1.1.0`). Hover it to see the upstream base and the build time. New endpoint `GET /mock-status/version`.
- Admin UI: static files are sent with `Cache-Control: no-cache` and no `Last-Modified`. A browser no longer mixes an old cached `app.js` with a new `index.html` after an image upgrade, which made the FR/EN toggle do nothing. A browser that cached the files from an earlier version can still use them once, so do one hard reload (Ctrl/Cmd+Shift+R) after this upgrade.
- Versioning: the fork now uses its own SemVer, starting with this release. The upstream base is recorded in `pom.xml` `<upstream.version>` and in the image label `io.github.unibrain1.upstream-version`.

## [1.0.0-en.3] — 2026-09-24

Fork release, based on upstream 1.0.0. First release tagged `latest`; README quick start uses the fork's image.

## [1.0.0-en.2] — 2026-09-24

Fork release, based on upstream 1.0.0. Spring Boot 3.3.6 → 4.1.1 and Java 21 → 25 (unibrain1/mock-brevo#8); JSON contract unchanged (snapshot-tested).

## [1.0.0-en.1] — 2026-09-23

Fork release, based on upstream 1.0.0. English admin UI with FR/EN toggle, English sample data and docs; optional Docker Hub publishing; tests, linting and CI.

## [1.0.0] — 2026-05-06

## [0.3.0] — 2026-04-24

add page to watch campagne information
add readme github in docker overview

## [0.2.0] — 2026-04-24

add scripts for:

- change version
- deploy

## [0.1.0] — 2026-04-24

Initial public release. Scope: cover the Brevo REST endpoints actively used by
the Enoria project and any client relying on the `getbrevo/brevo-php` SDK.

### Added

#### Brevo API surface

- `GET /v3/account` with full payload (`organization_id`, `user_id`,
  `enterprise`, `dateTimePreferences`, plan, address, relay,
  marketingAutomation — all SDK-required fields populated).
- `POST /v3/smtp/email` with payload persistence and returning a synthetic
  `messageId`.
- `GET /v3/senders` with generated IPs.
- `GET/POST /v3/contacts/lists`, `POST /v3/contacts/import` (CSV), `GET
  /v3/contacts/lists/{id}/contacts`, `DELETE /v3/contacts/lists/{id}/contacts`,
  `PUT /v3/contacts/{identifier}`.
- `GET/POST /v3/emailCampaigns` + `POST /v3/emailCampaigns/{id}/sendNow` with
  faker-generated statistics (globalStats, campaignStats, statsByDevice…).
- `GET/POST /v3/smtp/templates` with non-null defaults on all required fields.
- `GET/POST /v3/contacts/folders`.

#### Platform

- Auto-provisioning: any non-empty `api-key` header creates a new tenant on the
  fly with a deterministic `organization_id`, a default sender, and 2 seeded
  faker campaigns.
- Strict account scoping for every persisted entity.
- File-based H2 database, survives restarts.
- Optional SMTP forward relaying captured emails to a real SMTP catcher
  (Mailpit, MailHog…). Activated by default in the `dev` Spring profile.
- Outbound webhook fire endpoint (`POST /mock-webhooks/fire`) for simulating
  `delivered`, `opened`, `click`, `hard_bounce`, etc.

#### Admin web UI

- Single-page UI at `/` with two tabs (accounts / REST calls).
- Light / dark theme toggle with persistence; Monaco Editor syncs its theme.
- Monaco Editor for JSON bodies with folding, read-only, view-state preserved
  across auto-refresh cycles.
- Copy-to-clipboard button on every body.
- Inline form to create faker campaigns per account.
- Direct links to the matching Brevo documentation page for each logged route.
- In-memory ring buffer of the last 500 REST calls with full headers and bodies
  (request body / response body, truncation at 16 KiB).

### Disclaimer

Not affiliated with Brevo SAS. "Brevo" is a trademark of Brevo SAS. Every
endpoint is re-implemented from the publicly documented API contract; no Brevo
source code is redistributed.
