#!/usr/bin/env bash
# smoke-test-functions.sh
#
# Verifies that deployed Cloud Functions are reachable and returning
# expected application-level errors (not HTTP 4xx/5xx network errors).
#
# Tests:
#   - claimLinkingCode called with code "000000" (does not exist)
#     → must return a Firebase "not-found" error, not a network/server error.
#
# Usage:
#   scripts/smoke-test-functions.sh
#
# Prerequisites:
#   - Functions must be deployed to Firebase.
#   - curl must be installed.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
FIREBASERC="$ROOT/backend/.firebaserc"

if [[ ! -f "$FIREBASERC" ]]; then
  echo "ERROR: $FIREBASERC not found. Run from project root or inside the repo." >&2
  exit 1
fi

# Extract project ID from .firebaserc using python (available on macOS/Linux)
PROJECT_ID="$(python3 -c "import json,sys; d=json.load(open('$FIREBASERC')); print(d['projects']['default'])")"
REGION="asia-south1"
FUNCTION_URL="https://${REGION}-${PROJECT_ID}.cloudfunctions.net/claimLinkingCode"

echo "Project : $PROJECT_ID"
echo "Endpoint: $FUNCTION_URL"
echo ""

# Firebase callable functions expect a POST with JSON body { "data": { ... } }
# The response for an application error is:
#   { "error": { "status": "NOT_FOUND", "message": "...", "code": 5 } }
# We consider the test passing if the HTTP status is 200 OR 404 (Firebase SDKs
# return 404 for NOT_FOUND on callable v2, but v1 returns 200 with error body).

RESPONSE="$(curl --silent --max-time 15 \
  --write-out '\n__HTTP_STATUS__%{http_code}' \
  -X POST "$FUNCTION_URL" \
  -H "Content-Type: application/json" \
  -d '{"data":{"code":"000000"}}')"

HTTP_STATUS="$(echo "$RESPONSE" | grep '__HTTP_STATUS__' | sed 's/__HTTP_STATUS__//')"
BODY="$(echo "$RESPONSE" | grep -v '__HTTP_STATUS__')"

echo "HTTP status : $HTTP_STATUS"
echo "Response    : $BODY"
echo ""

# Accept 200 (v1 callable with error body) or 404 (v2 callable NOT_FOUND)
if [[ "$HTTP_STATUS" != "200" && "$HTTP_STATUS" != "404" ]]; then
  echo "FAIL — unexpected HTTP status $HTTP_STATUS (functions may not be deployed or region is wrong)"
  exit 1
fi

# If we got 200, the body must contain an error with status NOT_FOUND
if [[ "$HTTP_STATUS" == "200" ]]; then
  if echo "$BODY" | grep -qi '"status".*"NOT_FOUND"\|"not-found"\|not_found'; then
    echo "PASS — claimLinkingCode returned NOT_FOUND for unknown code (function is reachable)"
    exit 0
  else
    echo "FAIL — HTTP 200 but response does not contain NOT_FOUND error"
    echo "       Raw body: $BODY"
    exit 1
  fi
fi

# HTTP 404 from Firebase callable v2 = NOT_FOUND at application level
if [[ "$HTTP_STATUS" == "404" ]]; then
  if echo "$BODY" | grep -qi 'not.found\|NOT_FOUND'; then
    echo "PASS — claimLinkingCode returned NOT_FOUND for unknown code (function is reachable)"
    exit 0
  else
    echo "FAIL — HTTP 404 but response does not look like a Firebase NOT_FOUND error"
    echo "       Raw body: $BODY"
    exit 1
  fi
fi
