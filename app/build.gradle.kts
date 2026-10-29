plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.elkomy.app"
    compileSdk = 34
    buildFeatures { buildConfig = true }
    signingConfigs { getByName("debug") {
        storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    defaultConfig {
        applicationId = "com.elkomy.app"; minSdk = 21; targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt(); versionName = "1.0"
        val srv = file("../server_url.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""
        buildConfigField("String", "SERVER_URL", "\"$srv\"")
        val repo = file("../repo.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""
        buildConfigField("String", "REPO", "\"$repo\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
