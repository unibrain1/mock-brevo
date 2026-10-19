---
paths: ["src/main/resources/static/**"]
---

# Admin UI rules

These rules apply to files in `src/main/resources/static/`.

- **Every user-visible string goes through `I18N.t('key')`** (or a `data-i18n*` attribute in `index.html`), and the key must exist in **both** the `fr` and `en` dictionaries in `js/i18n.js`. No French or English literals in `app.js`.
- **Escape dynamic values** inserted into HTML with `esc()`. Only dictionary strings whose key ends in `Html` may be inserted unescaped.
- **Don't shadow `t`** (the translator) with a local variable in `app.js`.
- Plain ES2020 in an IIFE with `'use strict'`; no build step, no new runtime dependencies.
