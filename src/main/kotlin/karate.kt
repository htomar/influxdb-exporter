import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import java.io.File
import java.math.BigDecimal

internal val xmlMapper = XmlMapper(JacksonXmlModule().apply {
    setDefaultUseWrapper(false)
}).registerKotlinModule()
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

internal fun processKarateReports(args: Args) {
    val influxDBClient = InfluxDBClientFactory.createV1(
        args.influxDBUrl, args.influxDBUsername, args.influxDBPassword.toCharArray(),
        args.influxDBDatabase, null
    )
    val writeApi = influxDBClient.writeApi
    var totalTests: Long = 0
    var totalSkipped: Long = 0
    var totalFailures: Long = 0
    var totalTime: BigDecimal = BigDecimal.ZERO
    try {
        File(args.reportPath).walk()
            .filter { file -> file.isFile && file.path.endsWith(".xml") }
            .forEach {
                println("Processing file: ${it.absolutePath}")
                val testSuite = parseXMLAs(it.absolutePath)
                if (testSuite.tests > 0) {
                    writeApi.writePoint(buildPointFromTestSuite(testSuite, args.project, args.branch))
                    totalTests += testSuite.tests
                    totalSkipped += testSuite.skipped
                    totalFailures += testSuite.failures
                    totalTime = totalTime.add(testSuite.time)
                }
            }
        if (totalTests > 0)
            writeApi.writePoint(
                getPoint(
                    args.project, args.branch, "ALL", totalTests, totalSkipped,
                    totalFailures, totalTime
                )
            )
        else
            error("No files found for export!")
        writeApi.flush()
    } finally {
        influxDBClient.close()
    }
}

internal fun parseXMLAs(path: String): TestSuite {
    val file = File(path)
    return xmlMapper.readValue(file, TestSuite::class.java)
}

internal fun buildPointFromTestSuite(testSuite: TestSuite, project: String, branch: String): Point {
    val test = testSuite.name.substring(testSuite.name.lastIndexOf('/') + 1)
    return getPoint(
        project, branch, test, testSuite.tests, testSuite.skipped, testSuite.failures,
        testSuite.time
    )
}

internal fun getPoint(
    project: String, branch: String, test: String, tests: Long,
    skipped: Long, failures: Long, time: BigDecimal
): Point {
    val passedTests = tests - skipped - failures
    val point = Point.measurement("functionalTests")
        .time(currentTime, WritePrecision.MS)
        .addTag("project", project)
        .addTag("testName", test)
        .addField("tests", tests)
        .addField("skipped", skipped)
        .addField("failures", failures)
        .addField("pass", passedTests)
        .addField(
            "passPercentage", passedTests.div(tests).times(100)
        )
        .addField("timeTaken", time)
    if (branch.isNotBlank())
        point.addTag("branch", branch)
    return point
}

@JacksonXmlRootElement(localName = "testsuite")
data class TestSuite(
    @field:JacksonXmlProperty(localName = "failures", isAttribute = true)
    var failures: Long,

    @field:JacksonXmlProperty(localName = "name", isAttribute = true)
    var name: String,

    @field:JacksonXmlProperty(localName = "skipped", isAttribute = true)
    var skipped: Long,

    @field:JacksonXmlProperty(localName = "tests", isAttribute = true)
    var tests: Long,

    @field:JacksonXmlProperty(localName = "time", isAttribute = true)
    var time: BigDecimal
)