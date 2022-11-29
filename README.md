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

## Weights

### Exporting

The following will export weights for one year, in kg, to a file name `weights.csv`, formatted for MySQL:

```bash
./bundle/target/maven-jlink/default/bin/garmin-babel \
  --csv-format=mysql \
  --unit-weight=kilogram \
  --start-date=2021-01-01 \
  --end-date=2022-01-01 \
   ~/tmp/Garmin_Archive dump-weights \
   weights.csv
```

### Loading

As it is possible to store multiple weight measurements per day, you need to filter that file afterwards.
Here, we do this by a unique constraint in MySQL while loading:

```sql
CREATE TABLE IF NOT EXISTS weights_in(measured_on date, value DECIMAL(6,3), unique(measured_on));
LOAD DATA LOCAL INFILE 'weights.csv'
INTO TABLE weights_in
IGNORE 1 LINES
(@measured_on, value)
SET measured_on = date(str_to_date(@measured_on, '%Y-%m-%dT%H:%i:%sZ'))
;
```