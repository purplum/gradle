plugins {
    id("gradlebuild.distribution.packaging")
}

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    pluginsRuntimeOnly(platform(project(":distributions-core")))

    pluginsRuntimeOnly(project(":resources-http"))
    pluginsRuntimeOnly(project(":resources-sftp"))
    pluginsRuntimeOnly(project(":resources-s3"))
    pluginsRuntimeOnly(project(":resources-gcs"))
    pluginsRuntimeOnly(project(":resources-http"))
    pluginsRuntimeOnly(project(":build-cache-http"))

    pluginsRuntimeOnly(project(":tooling-api-builders"))
    pluginsRuntimeOnly(project(":kotlin-dsl-tooling-builders"))

    pluginsRuntimeOnly(project(":testKit"))
}
