/*
 * Copyright 2020 the original author or authors.
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
description = "API extraction for Java"

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

dependencies {
    implementation(project(":baseAnnotations"))
    implementation(project(":hashing"))
    implementation(project(":files"))
    implementation(project(":snapshots"))

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(project(":base-services"))
    testImplementation(project(":internalTesting"))
}
