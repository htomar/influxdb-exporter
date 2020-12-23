import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.WriteApi
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

internal val mapper = jacksonObjectMapper()
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
internal val currentTime = Instant.now()

fun processCucumberReports(args: Args) {
    InfluxDBClientFactory.createV1(
        args.influxDBUrl, args.influxDBUsername, args.influxDBPassword.toCharArray(),
        args.influxDBDatabase, null
    ).use { influxDBClient ->
        val writeApi = influxDBClient.writeApi
        val features = mutableListOf<Feature>()
        File(args.reportPath).walk()
            .filter { file -> file.isFile && file.path.endsWith(".json") }
            .forEach { file ->
                println("Processing file: ${file.absolutePath}")
                processFile(file, features)
            }

        if (features.isEmpty())
            error("No files found for export!")
        val overallResult = getOverallResult(features)
        println("Overall Stats: ${overallResult.stats}")
        writePoint(args.project, args.branch, overallResult, writeApi)
        writeApi.flush()
    }
}

private fun getOverallResult(features: MutableList<Feature>): OverallResult {
    val overallStats = Stats(0, 0, 0, 0, BigDecimal.ZERO)
    features.forEach { feature ->
        val scenarioStat = Stats(0, 0, 0, 0, BigDecimal.ZERO)
        overallStats.total++
        feature.scenarios.forEach {
            scenarioStat.total++
            when {
                it.stats.failed > 0 -> scenarioStat.failed++
                it.stats.passed > 0 -> scenarioStat.passed++
                else -> scenarioStat.skipped++
            }
        }
        calculatePercentage(scenarioStat)
        feature.stats = scenarioStat

        when {
            scenarioStat.failed > 0 -> overallStats.failed++
            scenarioStat.passed > 0 -> overallStats.passed++
            else -> overallStats.skipped++
        }
    }
    calculatePercentage(overallStats)
    return OverallResult(features, overallStats)
}

private fun calculatePercentage(stat: Stats) {
    if (stat.total > 0)
        stat.passPercentage = BigDecimal.valueOf(stat.passed)
            .divide(BigDecimal.valueOf(stat.total), 3, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1)
}

private fun processFile(file: File, features: MutableList<Feature>) {
    val reportData = parseAs(file.absolutePath)
    reportData.forEach { feature ->
        val scenarios = mutableListOf<Scenario>()
        feature.elements?.forEach { scenario ->
            val steps = Stats(0, 0, 0, 0, null)
            scenario.steps?.forEach { step ->
                steps.total++
                when (step.result?.status) {
                    Status.PASSED -> steps.passed++
                    Status.FAILED -> steps.failed++
                    Status.SKIPPED -> steps.skipped++
                }
            }
            scenarios.add(Scenario(scenario.name, steps))
        }
        val featureName = (feature.uri?.split("/")?.last() ?: feature.name)
        features.add(Feature(featureName, scenarios, null))
    }
}

internal fun parseAs(path: String): List<ReportData> {
    val file = File(path)
    return mapper.readValue(file)
}

internal fun writePoint(
    project: String, branch: String, overallResult: OverallResult, writeApi: WriteApi
) {
    writeApi.writePoint(getPoint(project, branch, "All", overallResult.stats))
    overallResult.features.forEach {
        it.stats?.let { stats -> writeApi.writePoint(getPoint(project, branch, it.name, stats)) }
    }
}

internal fun getPoint(project: String, branch: String, featureName: String, stats: Stats): Point {
    val point = Point.measurement("cucumber")
        .time(currentTime, WritePrecision.MS)
        .addTag("project", project)
        .addTag("feature", featureName)
        .addField("total", stats.total)
        .addField("failed", stats.failed)
        .addField("passed", stats.passed)
        .addField("skipped", stats.skipped)
        .addField("passPercentage", stats.passPercentage)

    if (branch.isNotBlank())
        point.addTag("branch", branch)
    return point
}

data class ReportData(
    var id: String?,
    var keyword: String,
    var name: String,
    var uri: String?,
    var elements: List<ReportData>?,
    var steps: List<ReportData>?,
    var result: Result?
)

data class Result(
    var status: Status,
    var duration: BigDecimal
)

enum class Status {
    PASSED,
    FAILED,
    SKIPPED
}

data class Stats(
    var total: Long,
    var passed: Long,
    var failed: Long,
    var skipped: Long,
    var passPercentage: BigDecimal?
)

data class OverallResult(var features: List<Feature>, var stats: Stats)
data class Feature(var name: String, var scenarios: List<Scenario>, var stats: Stats?)
data class Scenario(var name: String, var stats: Stats)