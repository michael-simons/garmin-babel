#!/bin/false

#
# This script requires Python3 and will install Garth on your behalf. Source it like this:
#
# source retrieve_garmin_tokens.sh <username> <password>
#
# It will export a Bearer token for the Garmin Connect API under GARMIN_BACKEND_TOKEN
#

LC_ALL=en_US.UTF-8

_USERNAME="$1"
_PASSWORD="$2"

if [  -z "${_USERNAME}" ] || [ -z "${_PASSWORD}" ]; then
  echo "Please source as retrieve_garmin_tokens.sh <username> <password>"
else
  mkdir -p .tmp

  DIR="$(dirname "$(realpath "$0")")"

  login_python="
import garth

garth.login('$1', '$2')

with open('.tmp/token', 'w') as file:
  file.write(garth.client.oauth2_token.access_token)
"
  python3 -m venv "$DIR/.venv"
  (source "$DIR/.venv/bin/activate" && pip3 install --quiet garth==0.5.17 && python3 -c "$login_python")

  GARMIN_BACKEND_TOKEN=$(more .tmp/token)
  export GARMIN_BACKEND_TOKEN

  rm -rf .tmp
fi;
