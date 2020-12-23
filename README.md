# influxdb-exporter
A InfluxDB exporter for functional tests that supports following frameworks:
- **Karate** (Only *XML* format is supported)
- **Cucumber** (Only *JSON* format is supported)

## Dependencies
- InfluxDB (uses v1 API)
- Java 11+
- Gradle (Gradle wrapper included)

## Parameters
- **--karate | --cucumber** - Report type to be exported
- **--project** - Project Name (for CI/CD)
- **--branch** (optional) -  Branch name (for CI/CD)
- **--path** - Report base path
- **--influxDBUrl** - InfluxDB URL
- **--influxDBUsername** (optional) - Database Username
- **--influxDBPassword** (optional) - InfluxDB Password
- **--influxDBDatabase** - InfluxDB Database name
