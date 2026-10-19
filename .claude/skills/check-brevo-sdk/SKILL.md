---
name: check-brevo-sdk
description: "Check a mock-brevo change against the real Brevo PHP SDK. Use when you add or change an endpoint or a response shape."
---

# Check a change against the real Brevo PHP SDK

The SDK deserializes strictly, and its models have required fields (`valid()` / `listInvalidProperties()`). Get the SDK version your client uses: with a local ElanRegistry checkout, it is vendored (v1.0.2) at `Registry/usersc/plugins/sendinblue/vendor`. Otherwise run `composer require getbrevo/brevo-php:1.0.2` in a scratch directory. To check a new endpoint with it:

1. Build and run the branch: `docker build -t mock-brevo:check . && docker run -d --name mb-check -p 127.0.0.1:18092:8080 mock-brevo:check`.
2. Seed data with `curl` (a send, then `/mock-webhooks/fire`).
3. Run a PHP script in `php:8.3-cli` with the vendor dir mounted read-only and `--add-host=host.docker.internal:host-gateway`. Set the host to `http://host.docker.internal:18092/v3`, make the same call the client makes, and print `valid()` for each model.
4. Remove the container and the image when done (see "Docker disk" in CLAUDE.md).
