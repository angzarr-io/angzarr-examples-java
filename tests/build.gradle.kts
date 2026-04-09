plugins {
    java
}

dependencies {
    // Composite build dependencies - use implementation to ensure dependency
    // substitution works correctly (testImplementation alone can fail in CI
    // when the module has no main sources)
    implementation("dev.angzarr:client")
    implementation("dev.angzarr:proto")

    // Project dependencies
    testImplementation(project(":player-agg"))
    testImplementation(project(":table-agg"))
    testImplementation(project(":hand-agg"))

    // Cucumber
    testImplementation("io.cucumber:cucumber-java:7.15.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.15.0")
    testImplementation("io.cucumber:cucumber-spring:7.15.0")

    // Spring Boot Test (for DI in tests)
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.0")

    // JUnit Platform
    testImplementation("org.junit.platform:junit-platform-suite:1.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")

    // Assertions
    testImplementation("org.assertj:assertj-core:3.24.2")

    // gRPC for Status
    testImplementation("io.grpc:grpc-api:1.60.0")
    // PicoContainer for acceptance tests (avoids Spring Boot context)

}

tasks.test {
    useJUnitPlatform {
        excludeTags("grpc-acceptance")
    }
    // Run only the unit Cucumber runner (AcceptanceTest) and exclude acceptance runners
    exclude("**/CucumberAcceptanceTest*")
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    // No PLAYER_URL -> InProcessCommandClient for unit tests
}

tasks.register<Test>("cucumberAcceptanceTest") {
    description = "Run Cucumber acceptance tests (gRPC when PLAYER_URL is set, in-process otherwise)"
    group = "verification"
    useJUnitPlatform()
    // Run only the acceptance Cucumber runner
    include("**/CucumberAcceptanceTest*")
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.execution.strict", "false")
    // Disable Spring backend for acceptance tests (use PicoContainer instead)
    // Pass domain URLs through to the test JVM for gRPC mode
    // Use providers to evaluate at execution time, not configuration time
    environment("PLAYER_URL", providers.environmentVariable("PLAYER_URL").orElse("localhost:1310"))
    environment("TABLE_URL", providers.environmentVariable("TABLE_URL").orElse("localhost:1311"))
    environment("HAND_URL", providers.environmentVariable("HAND_URL").orElse("localhost:1312"))
}

// Feature files are symlinked from angzarr-project/features/
// Unit step definitions in steps/, acceptance step definitions in acceptance/
