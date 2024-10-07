#!/bin/false

#
# This little beauty is the result of stepping through Garmins auth process
# to get tokens for accessing your own data.
# While they do have an API https://developer.garmin.com/gc-developer-program/activity-api/,
# you need to be approved for that and cannot get just a token for your own stuff.
# Hence, I am replicating here what the browser does.
#
# This script must be sourced:
#
# source retrieve_garmin_tokens.sh <username> <password>
#

LC_ALL=en_US.UTF-8

_USERNAME="$1"
_PASSWORD="$2"

if [  -z "${_USERNAME}" ] || [ -z "${_PASSWORD}" ]; then
  echo "Please source as retrieve_garmin_tokens <username> <password>"
else
  # Working directory
  mkdir -p .tmp
  # Shared headers
  echo "
    Accept: application/json, text/plain, */*
    Connection: keep-alive
    Priority: u=0
    Sec-Fetch-Dest: empty
    Sec-Fetch-Mode: cors
    Sec-Fetch-Site: same-origin
    Sec-Fetch-User: ?1
    TE: trailers
    Upgrade-Insecure-Requests: 1
    User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:131.0) Gecko/20100101 Firefox/131.0
    X-app-ver: 5.5.3.1
    X-lang: de-DE
  " | awk '{$1=$1};1' > .tmp/header.txt

  # Login and setup all the cookies
  curl 'https://sso.garmin.com/portal/api/login?clientId=GarminConnect&locale=en-US&service=https%3A%2F%2Fconnect.garmin.com%2Fmodern' -s \
    -H @.tmp/header.txt \
    -H 'Content-Type: application/json' \
    -H 'Origin: https://sso.garmin.com' \
    -H 'Referer: https://sso.garmin.com/portal/sso/en-US/sign-in?clientId=GarminConnect&service=https%3A%2F%2Fconnect.garmin.com%2Fmodern' \
    --cookie-jar .tmp/cookies.txt \
    --data-raw '{"username":"'"$_USERNAME"'","password":"'"$_PASSWORD"'","rememberMe":false,"captchaToken":""}' | \
  jq  --raw-output '.serviceURL  + "?ticket=" + .serviceTicketId' |\
  xargs -L 1 curl -sL \
    -H @.tmp/header.txt \
    --cookie .tmp/cookies.txt \
    --cookie-jar .tmp/cookies.txt > /dev/null

  # Exchange stuff for a bearer
  GARMIN_BACKEND_TOKEN=$(curl 'https://connect.garmin.com/modern/di-oauth/exchange' -s \
    -X POST \
    -H @.tmp/header.txt \
    -H 'Origin: https://connect.garmin.com' \
    -H 'Referer: https://connect.garmin.com/modern/' \
    --cookie .tmp/cookies.txt --cookie-jar .tmp/cookies.txt |\
    jq --raw-output .access_token
  )
  export GARMIN_BACKEND_TOKEN

  # And one more…
  GARMIN_JWT=$(awk '{if ($6=="JWT_FGP") {print $7}}' .tmp/cookies.txt)
  export GARMIN_JWT

  rm -rf .tmp
fi;