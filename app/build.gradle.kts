/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Embeds the Reborn AMOLED starter theme (themes/amoled) as assets/starter/reborn_amoled.apk,
 * installed from the empty Themes tab.
 */
abstract class CopyStarterTheme : DefaultTask() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("starter")
        dir.mkdirs()
        apk.get().asFile.copyTo(dir.resolve("reborn_amoled.apk"), overwrite = true)
    }
}

val copyStarterTheme = tasks.register<CopyStarterTheme>("copyStarterTheme") {
    apk.set(project(":themes:amoled").layout.buildDirectory
        .file("outputs/apk/debug/amoled-debug.apk"))
    dependsOn(":themes:amoled:assembleDebug")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyStarterTheme, CopyStarterTheme::outputDir)
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")

fun gitHash(): String = try {
    providers.exec { commandLine("git", "describe", "--tags", "--always") }
        .standardOutput.asText.get().trim()
} catch (ignored: Exception) {
    ""
}

android {
    namespace = "projekt.substratum"
    compileSdk = 37
    buildFeatures {
        dataBinding = true
        buildConfig = true
        aidl = true
    }
    defaultConfig {
        applicationId = "projekt.substratum"
        minSdk = 26
        targetSdk = 28
        versionCode = 1101
        versionName = "reborn 1101"
        buildConfigField("java.util.Date", "buildTime", "new java.util.Date(${System.currentTimeMillis()}L)")
        buildConfigField("String", "GIT_HASH", "\"${gitHash()}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))

        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "META-INF/*.kotlin_module"
        // The bundled compiler binaries are extracted and executed at runtime
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(files("libs/libsubstratum.jar"))
    compileOnly("de.robv.android.xposed:api:82")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("cat.ereza:customactivityoncrash:2.4.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("com.android.tools.build:apksig:8.7.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.recruit-lifestyle:FloatingView:2.4.4")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.github.gowong:material-sheet-fab:v1.2.1")
    implementation("com.jaredrummler:animated-svg-view:1.0.6")
    implementation("com.mikepenz:aboutlibraries:7.0.0@aar") {
        isTransitive = true
    }
    implementation("com.github.stephentuso:welcome-android:v1.4.1:release@aar")
    implementation("com.github.CanHub:Android-Image-Cropper:2.2.2")
    implementation("com.github.thesurix:gesture-recycler:1.8.0")
    implementation("com.github.cachapa:ExpandableLayout:2.9.2")
    implementation("org.zeroturnaround:zt-zip:1.17")
}
