#!/usr/bin/env bash
# check-doc-links.sh — checks that every Brevo API reference slug in the admin UI
# (src/main/resources/static/js/app.js) still resolves: the DOC_ROUTES table and any
# literal developers.brevo.com/reference/<slug> link.
#
# Brevo renames its reference pages from time to time (for example getsenders ->
# get-senders), and a stale slug gives a 404 link in the request log.
#
# Usage: scripts/check-doc-links.sh   (needs network access; exit 1 if a slug fails)

set -euo pipefail
cd "$(dirname "$0")/.."

app=src/main/resources/static/js/app.js
slugs=$( {
  sed -n '/const DOC_ROUTES = \[/,/\];/p' "$app" | grep -o "'[a-z0-9-]*'\],\$" | tr -d "'],"
  grep -o 'developers\.brevo\.com/reference/[a-z0-9-][a-z0-9-]*' "$app" | sed 's#.*/##' || true
} | sort -u)

# An empty list means the patterns above stopped matching, not that all links work.
if [ -z "$slugs" ]; then
  echo "no slugs found in $app"
  exit 1
fi

failed=0
for slug in $slugs; do
  code=$(curl -s -o /dev/null -L --max-time 20 -w '%{http_code}' "https://developers.brevo.com/reference/$slug" || true)
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
