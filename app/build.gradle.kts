plugins { id("com.android.application") }
android {
    namespace = "dev.xenoah.hud"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.xenoah.rokidhud"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-preview"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true }
}
dependencies { implementation(project(":core")) }
