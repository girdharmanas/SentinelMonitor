import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sentinel.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sentinel.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("sentinel-release.jks")
            storePassword = "sentinelmonitor"
            keyAlias = "sentinel"
            keyPassword = "sentinelmonitor"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variant = this
        val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null) {
                val commit = getGitCommitHash()
                val pkgName = applicationId
                output.outputFileName = "$pkgName-$commit.apk"
            }
        }

        val copyTask = tasks.register("copy${variantNameCapitalized}ApkToArtifacts") {
            doLast {
                val artifactsDir = rootProject.file("artifacts")
                if (artifactsDir.exists()) {
                    artifactsDir.listFiles()?.forEach { it.delete() }
                } else {
                    artifactsDir.mkdirs()
                }

                val gdrivePath = project.findProperty("gdrive.apk.dir")?.toString()
                    ?: System.getenv("SENTINEL_GDRIVE_DIR")
                    ?: "H:/My Drive/Sentinel/Monitor"
                val gdriveDir = File(gdrivePath)
                val isGdriveAvailable = gdriveDir.exists()

                if (isGdriveAvailable) {
                    try {
                        gdriveDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                val deleted = file.delete()
                                if (deleted) {
                                    println("Purged old file from Google Drive: ${file.name}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Warning: Could not purge some files from Google Drive: ${e.message}")
                    }
                }

                variant.outputs.forEach { output ->
                    val apkFile = output.outputFile
                    if (apkFile != null && apkFile.exists()) {
                        val dest = File(artifactsDir, apkFile.name)
                        apkFile.copyTo(dest, overwrite = true)
                        println("Published APK to artifacts/: ${apkFile.name}")

                        if (isGdriveAvailable) {
                            try {
                                val gdriveDest = File(gdriveDir, apkFile.name)
                                apkFile.copyTo(gdriveDest, overwrite = true)
                                println("🚀 Uploaded latest APK to Google Drive: ${gdriveDest.absolutePath}")
                            } catch (e: Exception) {
                                println("Error copying to Google Drive: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
        variant.assembleProvider.configure {
            finalizedBy(copyTask)
        }
    }
}

fun getGitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(project.rootDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        val hash = process.inputStream.bufferedReader().readText().trim()
        if (hash.isNotEmpty()) hash else "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.ui.tooling)
}
