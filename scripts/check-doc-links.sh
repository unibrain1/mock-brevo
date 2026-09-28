#!/usr/bin/env bash
# check-doc-links.sh — checks that every Brevo API reference slug in the admin UI's
# DOC_ROUTES table (src/main/resources/static/js/app.js) still resolves.
#
# Brevo renames its reference pages from time to time (for example getsenders ->
# get-senders), and a stale slug gives a 404 link in the request log.
#
# Usage: scripts/check-doc-links.sh   (needs network access; exit 1 if a slug fails)

set -euo pipefail
cd "$(dirname "$0")/.."

slugs=$(sed -n '/const DOC_ROUTES = \[/,/\];/p' src/main/resources/static/js/app.js \
  | grep -o "'[a-z0-9-]*'\],\$" | tr -d "'],")

failed=0
for slug in $slugs; do
  code=$(curl -s -o /dev/null -L -w '%{http_code}' "https://developers.brevo.com/reference/$slug")
  if [ "$code" != "200" ]; then
    echo "$code $slug"
    failed=$((failed + 1))
  fi
done

total=$(echo "$slugs" | wc -w | tr -d ' ')
if [ "$failed" -gt 0 ]; then
  echo "$failed of $total slugs failed"
  exit 1
fi
echo "all $total slugs return 200"
