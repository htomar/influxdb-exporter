import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

fun main(args: Array<String>) {
    val exporterArgs = ArgParser(args).parseInto(::Args)
    println(
        "project: ${exporterArgs.project}, branch: ${exporterArgs.branch}, path: ${exporterArgs.reportPath}" +
                "influxDB Url: ${exporterArgs.influxDBUrl}, influxDB Database: ${exporterArgs.influxDBDatabase}"
    )
    when(exporterArgs.reportType) {
        ReportType.KARATE -> processKarateReports(exporterArgs)
        ReportType.CUCUMBER -> processCucumberReports(exporterArgs)
    }
}

class Args(parser: ArgParser) {
    val reportType by parser.mapping(
        "--karate" to ReportType.KARATE,
        "--cucumber" to ReportType.CUCUMBER,
        help = "type of report to be exported"
    )

    val project by parser.storing(
        "--project",
        help = "Project name"
    )

    val branch by parser.storing(
        "--branch",
        help = "Branch name"
    ).default("")

    val reportPath by parser.storing(
        "--path",
        help = "Report Path"
    )

    val influxDBUrl by parser.storing(
        "--influxDBUrl",
        help = "InfluxDB Url"
    )

    val influxDBUsername by parser.storing(
        "--influxDBUsername",
        help = "InfluxDB Username"
    ).default("")

    val influxDBPassword by parser.storing(
        "--influxDBPassword",
        help = "InfluxDB password"
    ).default("")

    val influxDBDatabase by parser.storing(
        "--influxDBDatabase",
        help = "InfluxDB database"
    )
}

enum class ReportType {
    KARATE,
    CUCUMBER
}