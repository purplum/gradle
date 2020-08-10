plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedForStartup()

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":coreApi"))
    implementation(project(":logging"))
}
