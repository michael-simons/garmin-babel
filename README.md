# garmin-babel

A Java program to massage Garmin data exported via [Garmin GDPR management](https://www.garmin.com/de-DE/account/datamanagement)
into usable CSV files for further processing.

*NOTE* This program is not in any way, shape or form associated, supported or sponsored by Garmin. 
It's a personal project suiting my needs. It may or may not be helpful to other people.

## Building the tool

You need Java 17 or higher installed to build the program. Build should be quite fast, there are not tests ;)

```bash
./mvnw package
```

## Prebuild artifacts

I have a limited, untested set of packages for various operating systems on the [release page](https://github.com/michael-simons/garmin-babel/releases).
They all come with batteries - aka Java - included. You can use them as follow (exampel from macOS, on Windows it will look a bit different):

```bash
curl -LO https://github.com/michael-simons/garmin-babel/releases/download/early-access/garmin-babel-1.0.0-SNAPSHOT-osx-x86_64.zip
unzip garmin-babel-1.0.0-SNAPSHOT-osx-x86_64.zip -d garmin-babel
./garmin-babel/bin/garmin-babel --version 
```

## Activities

The `dump-activities` command requires a username (your Garmin username), as recorded in the archive:

### Dump all

The following will export all activities into a csv file.

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
   ~/tmp/Garmin_Archive \
   dump-activities -u michael.simons \
   activities.csv
```
### Filtering

#### Filter by date and type

The date filters are generally available, the types are only for the activities

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --start-date=2022-01-01 \
  --end-date=2022-02-01 \
  ~/tmp/Garmin_Archive \
  dump-activities -u michael.simons \
  --sport-type=CYCLING,SWIMMING
```

#### Combining filters

Get all runs longer than 15km prior to second half of 2022, displace speed as pace, use MySQL format

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --csv-format=MySQL \
  --speed-to-pace \
  --end-date=2022-07-01 \
  ~/tmp/Garmin_Archive \
  dump-activities -u michael.simons \
  --sport-type=RUNNING \
  --min-distance 15 \
  long-runs.csv
```

Get all cycling activities longer than 75km prior to second half of 2022, prepared for loading into MySQL

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --csv-format=MySQL \
  --end-date=2022-07-01 \
  ~/tmp/Garmin_Archive \
  dump-activities -u michael.simons \
  --sport-type=CYCLING \
  --activity-type=road_biking,gravel_cycling,cycling,mountain_biking,virtual_ride,indoor_cycling \
  --min-distance 75 \
  long-rides.csv
```

### Change units

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --start-date=2022-01-01 \
  --end-date=2022-01-10 \
  --unit-distance=metre \
  --speed-to-pace \
  ~/tmp/Garmin_Archive \
  dump-activities -u michael.simons \
  --sport-type=RUNNING
```

## Fun with SQLite

For aggregating I recommend importing the CSV data into a proper database. SQLite is a pretty good choice for ad-hoc queries on CSV.
Here are two examples that I found useful:

### Getting the fast half-marathon:

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./bundle/target/maven-jlink/default/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
 "WITH 
   runs AS (
     SELECT name, 
            cast(avg_speed as number) as avg_speed, 
            3600.0/cast(avg_speed as number) AS pace, 
            cast(distance AS number) AS distance
     FROM activities
     WHERE sport_type = 'RUNNING'
       AND cast(distance AS number) > 20
   ),
   m AS (SELECT max(avg_speed) AS max_speed FROM runs)
SELECT runs.name, runs.distance, cast(pace/60 AS int) || ':' || cast(pace%60 AS int) 
FROM m JOIN runs ON runs.avg_speed = m.max_speed"
```

### What gear did I use the most

Note: If you have more than one item used per activity, the following want work:

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./bundle/target/maven-jlink/default/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
 "WITH 
    cycle_activities AS (
      SELECT *
      FROM activities
      WHERE (sport_type = 'CYCLING' OR activity_type in ('road_biking', 'gravel_cycling', 'cycling', 'mountain_biking', 'virtual_ride', 'indoor_cycling'))
        AND gear IS NOT NULL AND gear <> ''
    ),
    durations AS (
      SELECT sum(cast(moving_duration AS NUMBER)) AS total_duration, gear
      FROM cycle_activities
      GROUP by gear
    ),
    distances AS (
      SELECT sum(cast(distance AS NUMBER)) AS total_distance, gear
      FROM cycle_activities
      GROUP by gear
    )
  SELECT durations.gear, distances.total_distance, (total_duration/3600) || ':' || (total_duration%3600/60) || ':' || (total_duration%3600%60) 
  FROM durations JOIN distances ON distances.gear = durations.gear
  ORDER BY total_duration DESC"
```

### Distribution of mileages

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./bundle/target/maven-jlink/default/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
 "WITH 
    cycle_activities AS (
      SELECT cast(distance AS NUMBER) as distance
      FROM activities
      WHERE (sport_type = 'CYCLING' OR activity_type in ('road_biking', 'gravel_cycling', 'cycling', 'mountain_biking', 'virtual_ride', 'indoor_cycling'))
        AND gear IS NOT NULL AND gear <> ''
    ),
    distances AS (
      SELECT CASE
        WHEN distance >= 300 THEN '1. More than 300km'
        WHEN distance >= 200 THEN '2. More than 200km'
        WHEN distance >= 100 THEN '3. More than 100km'
        WHEN distance >=  75 THEN '4. More than 75'
        ELSE '5. Between 0 and 75' END AS value
      FROM cycle_activities
    )
  SELECT substr(value, 4) || ': ' || count(*) || ' times' FROM distances
  GROUP BY value
  ORDER BY value ASC"
```

## Weights

### Exporting

The following will export weights prior to a given date, in kg, to a file name `weights.csv`, formatted for MySQL:

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --csv-format=mysql \
  --unit-weight=kilogram \
  --end-date=2022-07-01 \
   ~/tmp/Garmin_Archive dump-weights \
   weights.csv
```

### Loading

As it is possible to store multiple weight measurements per day, you need to filter that file afterwards.
Here, I do this by a unique constraint in MySQL while loading:

```sql
CREATE TABLE IF NOT EXISTS weights_in(measured_on date, value DECIMAL(6,3), unique(measured_on));
LOAD DATA LOCAL INFILE 'weights.csv'
INTO TABLE weights_in
IGNORE 1 LINES
(@measured_on, value)
SET measured_on = date(str_to_date(@measured_on, '%Y-%m-%dT%H:%i:%sZ'))
;
```
