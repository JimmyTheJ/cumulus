import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // iOS compiles on macOS. The source set is shared so an Xcode shell can be added later.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Cumulus"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

val cumulusVersion = getVersionName(fallback = "1.0.0-dev.1")
base.archivesName.set("cumulus-v$cumulusVersion")

android {
    namespace = "app.cumulus"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.cumulus"
        minSdk = 26
        targetSdk = 36
        versionName = cumulusVersion
        versionCode = getVersionCode(cumulusVersion)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.cumulus.MainKt"
        nativeDistributions {
            packageName = "Cumulus"
            packageVersion = "1.0.0"
            description = "Ad-free open-source weather"
        }
    }
}

val flavoredApkPackage = Regex("""^package([A-Z][A-Za-z]+)(Debug|Release)$""")
val plainApkPackage = Regex("""^package(Debug|Release)$""")

tasks.configureEach {
    val flavored = flavoredApkPackage.matchEntire(name)
    val plain = plainApkPackage.matchEntire(name)
    val flavor = flavored?.groupValues?.get(1)?.replaceFirstChar { it.lowercase() }.orEmpty()
    val buildType = (flavored?.groupValues?.get(2) ?: plain?.groupValues?.get(1))?.lowercase() ?: return@configureEach
    doLast {
        val versionName = android.defaultConfig.versionName ?: return@doLast
        val versionCode = android.defaultConfig.versionCode ?: return@doLast
        val apkDir = if (flavor.isEmpty()) {
            layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
        } else {
            layout.buildDirectory.dir("outputs/apk/$flavor/$buildType").get().asFile
        }
        val apkFile = apkDir.listFiles()
            ?.filter { it.isFile && it.extension == "apk" }
            ?.maxByOrNull { it.lastModified() }
            ?: run {
                logger.warn("Package task $name produced no APK in $apkDir; local version was not advanced")
                return@doLast
            }
        DevBuildMetadata.recordApkBuild(
            project = project,
            versionName = versionName,
            versionCode = versionCode,
            apkFile = apkFile,
            variant = buildType,
            flavor = flavor.ifEmpty { "none" },
        )
    }
}
