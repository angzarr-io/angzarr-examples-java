plugins {
    java
    application
}

dependencies {
    implementation("dev.angzarr:client")
    implementation("dev.angzarr:proto")
}

application {
    mainClass.set("dev.angzarr.examples.hand.HandUpcasterMain")
}
