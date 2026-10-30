plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.qingyu.personalaios"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.qingyu.personalaios"
        minSdk = 33
        targetSdk = 36
        versionCode = 115
        versionName = "0.19.83"
        testInstrumentationRunner = "com.qingyu.personalaios.VulkanRotationInstrumentation"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testBuildType = providers.gradleProperty("doki.testBuildType").getOrElse("debug")
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}
dependencies {
    implementation(project(":domain"))
    implementation(project(":core:theme"))
    implementation(project(":core:weather"))
    implementation(project(":core:glass"))
    implementation(libs.compose.animation)
    implementation(project(":data:weather"))
    implementation(project(":data:device"))
    implementation(project(":data:local"))
    implementation(project(":core:ui"))
    implementation(project(":core:shape"))
    implementation(project(":feature:today"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:diary"))
    implementation(project(":feature:me"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

