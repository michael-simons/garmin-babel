#!/usr/bin/env bash

#
# This script requires DuckDB on the path.
# Run with export_fitness_metrics.sh <path_to_your_garmin_archive>
# It will extract relevant fitness data and dump it as CSV to stdout.
# Change the query as needed for mor or less information.
#

set -euo pipefail
export LC_ALL=en_US.UTF-8

GARMIN_ARCHIVE=$1

duckdb -s "
COPY (
  WITH weights AS (
    SELECT metaData.calendarDate::date AS calendarDate,
           unnest(weight),
           row_number() OVER (PARTITION BY metaData.calendarDate::date ORDER BY metaData.sequence) AS rn
    FROM read_json('$GARMIN_ARCHIVE/DI_CONNECT/DI-Connect-Wellness/*_userBioMetrics.json', auto_detect=true)
    WHERE weight IS NOT NULL
    QUALIFY rn <= 1
  ), vo2MaxRunning AS (
    SELECT metaData.calendarDate::date AS calendarDate,
           row_number() OVER (PARTITION BY metaData.calendarDate::date ORDER BY metaData.sequence) AS rn,
           vo2MaxRunning AS value
    FROM read_json('$GARMIN_ARCHIVE/DI_CONNECT/DI-Connect-Wellness/*_userBioMetrics.json', auto_detect=true)
    WHERE vo2MaxRunning IS NOT NULL
    QUALIFY rn <= 1
  ), vo2MaxCycling AS (
    SELECT metaData.calendarDate::date AS calendarDate,
           row_number() OVER (PARTITION BY metaData.calendarDate::date ORDER BY metaData.sequence) AS rn,
           vo2MaxCycling AS value
    FROM read_json('$GARMIN_ARCHIVE/DI_CONNECT/DI-Connect-Wellness/*_userBioMetrics.json', auto_detect=true)
    WHERE vo2MaxCycling IS NOT NULL
    QUALIFY rn <= 1
  ), fitnessAge AS (
    SELECT asOfDateGmt::date AS calendarDate,
           row_number() OVER (PARTITION BY asOfDateGmt::date ORDER BY asOfDateGmt) AS rn,
           chronologicalAge,
           currentBioAge,
           biometricVo2Max,
    FROM read_json('$GARMIN_ARCHIVE/DI_CONNECT/DI-Connect-Wellness/*_fitnessAgeData.json', auto_detect=true)
    WHERE currentBioAge IS NOT NULL
    QUALIFY rn <= 1
  )
  SELECT uds.calendarDate                                                                      AS ref_date,
         fa.chronologicalAge                                                                   AS chronological_age,
         cast(fa.currentBioAge AS DECIMAL(5,2))                                                AS biological_age,
         cast(w.weight / 1000.0 AS DECIMAL(5,2))                                               AS weight,
         cast(w.bodyFat AS DECIMAL (5,2))                                                      AS body_fat,
         uds.restingHeartRate                                                                  AS resting_heart_rate,
         cast(fa.biometricVo2Max  AS DECIMAL(5,2))                                             AS vo2max_biometric,
         cast(vo2MaxRunning.value AS DECIMAL(5,2))                                             AS vo2max_running,
         cast(vo2MaxCycling.value AS DECIMAL(5,2))                                             AS vo2max_cycling,
         nullif(greatest(-1, list_filter(allDayStress.aggregatorList, i -> i.type = 'TOTAL')[1].averageStressLevel), -1)
                                                                                               AS avg_stress_level,
         uds.minHeartRate                                                                      AS min_heart_rate,
         uds.maxHeartRate                                                                      AS max_heart_rate,
         cast(w.bodyWater AS DECIMAL(5,2))                                                     AS body_water,
         cast(w.boneMass / 1000.0 AS DECIMAL(5,2))                                             AS bone_mass,
         cast(w.muscleMass / 1000.0 AS DECIMAL(5,2))                                           AS muscle_mass,
         uds.lowestSpo2Value                                                                   AS lowest_spo2_value,
  FROM read_json('$GARMIN_ARCHIVE/DI_CONNECT/DI-Connect-Aggregator/UDSFile_*.json', auto_detect=true, union_by_name=true) uds
  LEFT OUTER JOIN weights w ON w.calendarDate = uds.calendarDate
  LEFT OUTER JOIN fitnessAge fa ON fa.calendarDate = uds.calendarDate
  LEFT OUTER JOIN vo2MaxRunning ON vo2MaxRunning.calendarDate = uds.calendarDate
  LEFT OUTER JOIN vo2MaxCycling ON vo2MaxCycling.calendarDate = uds.calendarDate
  ORDER BY uds.calendarDate ASC
) TO '/dev/stdout' (FORMAT CSV)
"
