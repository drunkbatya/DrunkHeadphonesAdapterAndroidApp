plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.wire)
}

fun git(vararg arguments: String): String? {
    val result = providers.exec {
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }
    return result.standardOutput.asText.get().trim().takeIf { result.result.get().exitValue == 0 }
}

val versionOrder = compareBy<List<Int>>({ it.getOrElse(0) { 0 } }, { it.getOrElse(1) { 0 } }, { it.getOrElse(2) { 0 } })

val releaseVersionName: String = git("tag", "--points-at", "HEAD", "--list", "[0-9]*")
    ?.lines()
    ?.filter { it.isNotBlank() }
    ?.maxWithOrNull(compareBy(versionOrder) { tag -> tag.split(".").map { it.toIntOrNull() ?: -1 } })
    ?: git("describe", "--tags", "--abbrev=0", "--match", "[0-9]*")
    ?: "0.0.0"

val releaseVersionCode: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
  namespace = "com.drunkbatya.drunkheadphonesadapter"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.drunkbatya.drunkheadphonesadapter"
    minSdk = 34
    targetSdk = 37
    versionCode = releaseVersionCode
    versionName = releaseVersionName
  }
  signingConfigs {
    create("release") {
      storeFile = providers.environmentVariable("ANDROID_KEYSTORE").orNull?.let(::file)
      storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
      keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
      keyPassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
  }
}

kotlin {
  jvmToolchain(25)
}

wire {
  sourcePath {
    srcDir("../lib/firmware/protobuf")
  }
  kotlin {}
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
}
