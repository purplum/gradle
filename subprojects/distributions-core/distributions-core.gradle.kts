plugins {
    id("gradlebuild.distribution.packaging")
}

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    pluginsRuntimeOnly(project(":pluginUse")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":dependency-management")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":workers")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":compositeBuilds")) {
        because("We always need a BuildStateRegistry service implementation for certain code in ':core' to work.")
    }
    pluginsRuntimeOnly(project(":tooling-api-builders")) {
        because("We always need a BuildEventListenerFactory service implementation for ':launcher' to create global services.")
    }
    pluginsRuntimeOnly(project(":versionControl")) {
        because("We always need a VcsMappingsStore service implementation to create 'ConfigurationContainer' in ':dependency-management'.")
    }
    pluginsRuntimeOnly(project(":instant-execution")) {
        because("We always need a BuildLogicTransformStrategy service implementation.")
    }
    pluginsRuntimeOnly(project(":testing-junit-platform")) {
        because("All test workers have JUnit platform on their classpath (see ForkingTestClassProcessor.getTestWorkerImplementationClasspath).")
    }
    pluginsRuntimeOnly(project(":kotlin-dsl-provider-plugins")) {
        because("We need a KotlinScriptBasePluginsApplicator service implementation to use Kotlin DSL scripts.")
    }
}
