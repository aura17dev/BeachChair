/*
 * Copyright (C) 2025 The Android Open Source Project
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
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.launcher3.widgetpicker"
    buildFeatures {
        compose = true
    }
    sourceSets {
        named("main") {
            java.directories.add("src")
            kotlin.directories.add("src")
            manifest.srcFile("AndroidManifest.xml")
            res.directories.add("res")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)
    ksp(libs.dagger.android.processor)

    // Compose UI dependencies
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Other UI dependencies
    implementation(libs.compose.material3.windowSizeClass)
    implementation(libs.androidx.window)

    // Compose android studio preview support
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)

    implementation(projects.concurrent)
    implementation(projects.dagger)
}
