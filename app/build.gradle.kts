import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    alias(libs.plugins.compose.compiler)
}

val releaseAdMobAppId = providers.gradleProperty("ADMOB_APP_ID").map { it.trim() }.orElse("")
val releaseAdMobBannerId = providers.gradleProperty("ADMOB_BANNER_AD_UNIT_ID").map { it.trim() }.orElse("")

val validateReleaseAdMobIds by tasks.registering {
    group = "verification"
    description = "Reject missing, malformed, or Google test AdMob identifiers in release builds."
    doLast {
        val appId = releaseAdMobAppId.get()
        val bannerId = releaseAdMobBannerId.get()
        val testPublisher = "ca-app-pub-3940256099942544"
        require(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}").matches(appId) && !appId.startsWith(testPublisher)) {
            "Set a real ADMOB_APP_ID in Gradle properties; missing, malformed, and Google test IDs are forbidden for release."
        }
        require(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(bannerId) && !bannerId.startsWith(testPublisher)) {
            "Set a real ADMOB_BANNER_AD_UNIT_ID in Gradle properties; Google test IDs are forbidden for release."
        }
        require(appId.substringBefore('~') == bannerId.substringBefore('/')) { "AdMob App ID and Banner ID must use the same publisher." }
        require(!appId.contains("0000000000000000") && !appId.endsWith("0000000000") && !bannerId.endsWith("0000000000")) {
            "Placeholder AdMob identifiers are forbidden for release."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "generateReleaseBuildConfig" || name == "processReleaseMainManifest") {
        dependsOn(validateReleaseAdMobIds)
    }
}

android {
    namespace = "com.am24.imbrel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.am24.imbrel"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "UMP_FORCE_EEA",
                providers.gradleProperty("UMP_FORCE_EEA").map { (it == "true").toString() }.getOrElse("false"),
            )
            val umpDevice = providers.gradleProperty("UMP_TEST_DEVICE_HASH").getOrElse("").filter { it.isLetterOrDigit() }
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"$umpDevice\"")
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
        release {
            buildConfigField("boolean", "UMP_FORCE_EEA", "false")
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
            manifestPlaceholders["adMobAppId"] = releaseAdMobAppId.get().ifBlank { "UNCONFIGURED_ADMOB_APP_ID" }
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"${releaseAdMobBannerId.get().filter { it.isDigit() || it in "ca-app-pub-/" }}\"",
            )
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)
    implementation(libs.androidx.compose.material3)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    // Koin
    implementation("io.insert-koin:koin-android:3.5.6")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation("org.mockito:mockito-android:5.19.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
    source.setFrom(
        "src/main/kotlin",
        "src/main/java",
        "src/test/kotlin",
        "src/test/java",
        "src/androidTest/kotlin",
        "src/androidTest/java",
    )
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**", "**/ksp/**")
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/build/**", "**/generated/**", "**/ksp/**")
    }
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.am24.imbrel.R",
                    "com.am24.imbrel.R\$*",
                    "com.am24.imbrel.*ComposableSingletons*",
                    "com.am24.imbrel.data.local.*_Impl*",
                    "com.am24.imbrel.presentation.theme.*",
                )
            }
        }
    }
}
