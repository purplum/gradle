/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":plugins"))
    implementation(project(":workers"))
    implementation(project(":dependency-management")) // Required by JavaScriptExtension#getGoogleApisRepository()

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.rhino)
    implementation(libs.gson) // used by JsHint.coordinates
    implementation(libs.simple) // used by http package in envjs.coordinates

    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/plugins/javascript/coffeescript/**"))
}
