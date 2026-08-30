plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.beachchair.sexyspaces"

    defaultConfig {
        applicationId = "app.beachchair.sexyspaces"
        versionCode = 2
        versionName = "0.1.1"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        named("main") {
            kotlin.directories.add("src")
            manifest.srcFile("AndroidManifest.xml")
            res.directories.add("res")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
}
