plugins {
    id("com.android.application")
}

android {
    namespace = "projekt.substratum.theme.amoledstatic"
    compileSdk = 37
    defaultConfig {
        applicationId = "projekt.substratum.theme.amoledstatic"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Overlay sources are compiled on the device by Substratum, keep them as-is
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:.*:!CVS:!thumbs.db:!picasa.ini:!*.scc:*~"
    }
}
