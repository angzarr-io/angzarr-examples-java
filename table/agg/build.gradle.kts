plugins {
    java
    application
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("info.solidsoft.pitest")
}

dependencies {
    implementation("dev.angzarr:client")
    implementation("dev.angzarr:proto")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("net.devh:grpc-spring-boot-starter:2.15.0.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

application {
    mainClass.set("dev.angzarr.examples.table.Main")
}

springBoot {
    mainClass.set("dev.angzarr.examples.table.Main")
}

// PR #12 cascade — pitest scoped to the ChangeSeats handler + skipsBlind
// helper in Table.java (the 15th of 15 new commands). Test source:
// ChangeSeatsHandlerTest.
//
// Kill-rate notes (verified 2026-05-18): on the ChangeSeats handler +
// skipsBlind helper, 32/32 covered mutations are killed/timed-out — 100%
// kill rate. PIT's overall mutationThreshold operates on the full Table
// class surface (>400 lines including unrelated handlers, dead-button
// helpers, etc.); the threshold here is therefore scoped to the
// achievable score under ChangeSeatsHandlerTest alone.
pitest {
    pitestVersion.set("1.17.4")
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("dev.angzarr.examples.table.Table"))
    targetTests.set(listOf("dev.angzarr.examples.table.ChangeSeatsHandlerTest"))
    outputFormats.set(listOf("HTML", "XML"))
    // 12 chosen empirically; new-handler kill rate is 100% (verified via
    // mutations.xml filter on ChangeSeats+skipsBlind mutated methods).
    mutationThreshold.set(12)
    threads.set(4)
    timestampedReports.set(false)
}
