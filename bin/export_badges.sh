#!/usr/bin/env bash

#
# This script requires DuckDB on the path.
# First, use `source retrieve_garmin_tokens.sh` to retrieve a Garmin token,
# than run `export_fitness_metrics.sh`.
# It will extract relevant fitness data and dump it as CSV to stdout.
# Change the query as needed for mor or less information, columns have been
# enumerated upfront so that the script won't fail if there are no badges yet.
#

set -euo pipefail
export LC_ALL=en_US.UTF-8

CREATE_SECRET_QUERY="
  CREATE OR REPLACE SECRET garmin (
    TYPE HTTP,
    EXTRA_HTTP_HEADERS MAP {
        'Authorization': 'Bearer ' || getenv('GARMIN_BACKEND_TOKEN')
    },
    SCOPE 'https://connectapi.garmin.com'
  )
"

COPY_BADGES_QUERY="
  COPY (
    WITH attributes AS (
      SELECT * FROM read_json('https://connectapi.garmin.com/badge-service/badge/attributes')
    ), types AS (
      SELECT unnest(badgeTypes, recursive:=true) FROM attributes
    ), categories AS (
      SELECT unnest(badgeCategories, recursive:=true) FROM attributes
    ), difficulties AS (
      SELECT unnest(badgeDifficulties, recursive:=true) FROM attributes
    ), units AS (
      SELECT unnest(badgeUnits, recursive:=true) FROM attributes
    )
    SELECT id           : earned.badgeId,
           uuid         : earned.badgeUuid,
           key          : earned.badgeKey,
           name         : earned.badgeName,
           earned_at    : date_trunc('second', earned.badgeEarnedDate),
           earned_times : earned.badgeEarnedNumber,
           starts_at    : CASE WHEN list_contains(earned.badgeTypeIds, 6) THEN date_trunc('second', earned.badgeStartDate::datetime) ELSE null END,
           ends_at      : CASE WHEN list_contains(earned.badgeTypeIds, 6) THEN date_trunc('second', earned.badgeEndDate::datetime) ELSE null END,
           category_id  : categories.badgeCategoryId,
           category     : categories.badgeCategoryKey,
           difficulty_id: difficulties.badgeDifficultyId,
           difficulty   : difficulties.badgeDifficultyKey,
           points       : difficulties.badgePoints
    FROM read_json('https://connectapi.garmin.com/badge-service/badge/earned', columns = {
      badgeId: 'BIGINT',
      badgeUuid: 'VARCHAR',
      badgeKey: 'VARCHAR',
      badgeName: 'VARCHAR',
      badgeEarnedDate: 'DATETIME',
      badgeEarnedNumber: 'BIGINT',
      badgeStartDate: 'DATETIME',
      badgeEndDate: 'DATETIME',
      badgePoints: 'BIGINT',
      badgeTypeIds: 'BIGINT[]',
      badgeCategoryId: 'BIGINT',
      badgeDifficultyId: 'BIGINT'
    }) earned
    NATURAL LEFT OUTER JOIN categories
    NATURAL LEFT OUTER JOIN difficulties
    ORDER BY earned_at
  ) TO '/dev/stdout' (FORMAT CSV)
"

 duckdb \
    -c "LOAD ICU" \
    -c "SET TimeZone='Europe/Berlin'" \
    -c ".mode trash" \
    -c "$CREATE_SECRET_QUERY" \
    -c "$COPY_BADGES_QUERY"
