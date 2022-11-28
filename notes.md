
### DL Files

```bash
curl -L 'https://connect.garmin.com/download-service/files/activity/<id>' \
  -H 'Authorization: Bearer <token>' \
  -H 'Cookie: JWT_FGP=<jwt>; SESSIONID=<sessionId>' \
  -H 'DI-Backend: connectapi.garmin.com' \
  --output foo.zip
```

### Export / Convert files

```bash
curl 'https://connect.garmin.com/download-service/export/<gpx|tcx>/activity/<id>' \
    -H 'Authorization: Bearer <token>' \
    -H 'Accept-Encoding: gzip, deflate, br' \
    -H 'Referer: https://connect.garmin.com/modern/activity/8042666009' \
    -H 'Cookie: JWT_FGP=<jwt>; SESSIONID=<sessionId>' \
    -H 'DI-Backend: connectapi.garmin.com' \
    --output f.<gpx|tcx>
```

### Data loading, i.e. MariaDB

```sql
LOAD DATA INFILE 'test.csv'
INTO TABLE x
(@started_on)
SET p = str_to_date(@started_on, '%Y-%m-%dT%H:%i:%sZ');
```
