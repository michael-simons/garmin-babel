# garmin-babel

A Java program to massage Garmin data exported via [Garmin GDPR management](https://www.garmin.com/de-DE/account/datamanagement)
into usable CSV files for further processing.

⚠️ This program is not in any way, shape or form associated, supported or sponsored by Garmin. 
It's a personal project suiting my needs. It may or may not be helpful to other people.

## Installation

For copy&pasting the examples further down, you need to either build this tool yourself or pick one of the pre-build artifacts fitting your operating system and architecture.

### Pre-build artifacts

I have a limited, untested set of packages for various operating systems on the [release page](https://github.com/michael-simons/garmin-babel/releases).
They come either as plain Java distribution requiring a locally installed JDK, with batteries included (that is, with a JDK for a specific operating system and architecture) or as a native distribution not requiring a JDK at all. You can use them as follows (the example is based on the plain Java distribution):

```bash
mkdir -p target
cd target
curl -LO https://github.com/michael-simons/garmin-babel/releases/download/early-access/garmin-babel-1.0.0-SNAPSHOT.zip
unzip garmin-babel-1.0.0-SNAPSHOT.zip && mv garmin-babel-1.0.0-SNAPSHOT garmin-babel
cd - 
./target/garmin-babel/bin/garmin-babel --version
```

ℹ️ All archives with _native_ in their name can be run standalone, without a Java virtual machine. The ones with an operating system in their name are custom JVM bundles and OS specific. In case your operating system is not in the list, grab the one named `garmin-babel-x.y.z.zip`. That one requires [OpenJDK](https://adoptium.net/de/) or any other Java 17 installed.

### Building the tool yourself

You need Java 17 or higher installed to build the program. Build should be quite fast, there are no tests ;)

```bash
./mvnw clean package -no-transfer-progress
unzip -q target/artifacts/garmin-babel-*.zip -d target/garmin-babel
./target/garmin-babel/bin/garmin-babel --version
```

If you want to build a native binary for an architecture and operating system for which no binaries are available, you need to install [GraalVM](https://www.graalvm.org), including native-image and run the following:

```bash
./mvnw clean package -Dnative -pl app
mkdir -p target/garmin-babel/bin && mv ./app/target/garmin-babel target/garmin-babel/bin
./target/garmin-babel/bin/garmin-babel --version
```
## Usage

### Activities

The `dump-activities` command requires a username (your Garmin username), as recorded in the archive:

#### Dump all

The following will export all activities into a csv file.

```bash
./target/garmin-babel/bin/garmin-babel \
   ~/tmp/Garmin_Archive \
   dump-activities \
   --user-name=michael.simons \
   target/demo/activities.csv
```

You can create a SQLite database with it like this:

```bash
sqlite3 target/demo/activities.sqlite \
  'CREATE TABLE IF NOT EXISTS activities (
     garmin_id number, name varchar(256), started_on timestamp, activity_type varchar(32), sport_type varchar(32), distance number, elevation_gain number, avg_speed number, max_speed number, duration number, elapsed_duration, moving_duration number, v_o_2_max number, start_longitude decimal(12,8), start_latitude decimal(12,8), end_longitude decimal(12,8), end_latitude decimal(12,8), gear varchar(256),
     unique(garmin_id)
  )' \
  '.mode csv' \
  '.import target/demo/activities.csv activities' \
  '.q'
```

In the examples further down the line I have omitted a proper table for the imports and just worked in memory, accepting that I need to cast the types manually.

#### Filtering

##### Filter by date and type

The date filters are generally available, the types are only for the activities

```bash
./target/garmin-babel/bin/garmin-babel \
  --start-date=2022-01-01 --end-date=2022-02-01 ~/tmp/Garmin_Archive \
  dump-activities \
  --user-name=michael.simons \
  --sport-type=CYCLING,SWIMMING
```

##### Combining filters

Get all runs longer than 15km prior to second half of 2022, displace speed as pace, use MySQL format

```bash
./target/garmin-babel/bin/garmin-babel \
  --csv-format=MySQL --speed-to-pace --end-date=2022-07-01 ~/tmp/Garmin_Archive \
  dump-activities \
  --user-name=michael.simons \
  --sport-type=RUNNING \
  --min-distance=15 \
  target/demo/long-runs.csv
```

Get all cycling activities longer than 75km prior to second half of 2022, prepared for loading into MySQL

```bash
./target/garmin-babel/bin/garmin-babel \
  --csv-format=MySQL --end-date=2022-07-01 ~/tmp/Garmin_Archive \
  dump-activities \
  --user-name=michael.simons \
  --sport-type=CYCLING \
  --activity-type=road_biking,gravel_cycling,cycling,mountain_biking,virtual_ride,indoor_cycling \
  --min-distance 75 \
  target/demo/long-rides.csv
```

#### Change units

```bash
./target/garmin-babel/bin/garmin-babel \
  --start-date=2022-01-01 --end-date=2022-01-10 --unit-distance=metre --speed-to-pace \
  ~/tmp/Garmin_Archive \
  dump-activities \
  --user-name=michael.simons \
  --sport-type=RUNNING
```

#### Downloading activities

⚠️ There is no guarantee whatsoever that this feature keeps working. It's basically doing the same what a user would manually click the download link on Garmin Connect. I added a bit of jitter when downloading things to not hammer the service like bot would do, but use this feature of the tool on your own risk!

While all activities are actually contained in the GDPR archive dump I didn't find any indicator which file belongs to which activity. This is sad, as I wanted to have them for my personal archive, at least some of them (Yes, I can go through the devices or the Garmin Connect page, but you know ;)).

Do download things you have to log in to [Garmin Connect](https://connect.garmin.com). Once done, open your Browsers developer tools and find the cookie jar. Find a cookie named `JWT_FGP` and copy its value.

Export it in your shell like this:

```bash
export GARMIN_JWT=jwt_token_from_your_cookie_store_for_garmin
```

Then in the _network_ tab of your Browsers developer tools (name might be different), clear all requests (or leave them, if you really want to search to the ton of requests the UI does). Then go to [activities](https://connect.garmin.com/modern/activities) for example and look in the requests tab for a request to `activities`. Look for something that says `HEADER` and in those headers look for `Authorization: Bearer ` and copy that (very long) very long header and export it, too.

```bash
export GARMIN_BACKEND_TOKEN=long_gibberish_token_from_one_of_the_requests
```

Then, the `--download` option can be used like this:

```bash
./target/garmin-babel/bin/garmin-babel \
  --csv-format=MySQL --speed-to-pace --end-date=2022-07-01 ~/tmp/Garmin_Archive \
  dump-activities \
  --user-name=michael.simons \
  --sport-type=RUNNING \
  --min-distance=15 \
  --download=fit \
  target/demo/long-runs.csv
```

#### Fun with SQLite

For aggregating I recommend importing the CSV data into a proper database. SQLite is a pretty good choice for ad-hoc queries on CSV.
Here are two examples that I found useful:

##### Getting the 10 fastest long runs

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./target/garmin-babel/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
 "WITH 
   runs AS (
     SELECT name, 
            3600.0/cast(avg_speed as number) AS pace, 
            cast(distance AS number) AS distance
     FROM activities
     WHERE sport_type = 'RUNNING'
       AND cast(distance AS number) > 20
   ),
   paced_runs AS (
     SELECT name, distance, cast(pace/60 AS int) || ':' || cast(pace%60 AS int) AS pace FROM runs
   ),
   ranked_runs AS (
     SELECT name, distance, pace, dense_rank() OVER (ORDER BY pace ASC) AS rnk
     FROM paced_runs
     GROUP BY name, distance
   )
SELECT rnk, name, distance, pace
FROM ranked_runs WHERE rnk <= 10"
```

##### What gear did I use the most

Note: If you have more than one item used per activity, the following want work:

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./target/garmin-babel/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
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

##### Distribution of mileages

```bash
sqlite3 :memory: \
 '.mode csv' \
 '.import "|./target/garmin-babel/bin/garmin-babel ~/tmp/Garmin_Archive dump-activities --user-name=michael.simons" activities' \
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

### Gear

Exporting all your gear:

```bash
./target/garmin-babel/bin/garmin-babel ~/tmp/Garmin_Archive \
  dump-gear \
  --user-name=michael.simons
```

### Weights

#### Exporting

The following will export weights prior to a given date, in kg, to a file name `weights.csv`, formatted for MySQL:

```bash
./target/garmin-babel/bin/garmin-babel \
  --csv-format=mysql --unit-weight=kilogram --end-date=2022-07-01 ~/tmp/Garmin_Archive \
  dump-weights \
  target/demo/weights.csv
```

#### Loading

As it is possible to store multiple weight measurements per day, you need to filter that file afterwards.
Here, I do this by a unique constraint in MySQL while loading:

```sql
CREATE TABLE IF NOT EXISTS weights_in(measured_on date, value DECIMAL(6,3), unique(measured_on));
LOAD DATA LOCAL INFILE 'target/demo/weights.csv'
INTO TABLE weights_in
IGNORE 1 LINES
(@measured_on, value)
SET measured_on = date(str_to_date(@measured_on, '%Y-%m-%dT%H:%i:%sZ'))
;
```
