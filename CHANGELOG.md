# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). From 1.1.0 on, this
fork (unibrain1/mock-brevo) follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
with its own version numbers; each release notes the upstream
(c0boleis/mock-brevo) version it is based on.

## [Unreleased]

- Versioning: the fork now uses its own SemVer (this release is its first), recorded upstream base in `pom.xml` `<upstream.version>` and the image label `io.github.unibrain1.upstream-version`.

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
