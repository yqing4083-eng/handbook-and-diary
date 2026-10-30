plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.qingyu.personalaios.core.glass"
    compileSdk = 36
    defaultConfig { minSdk = 33 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
dependencies {
    implementation(libs.androidx.activity.compose)
    api(libs.liquid.backdrop)
    implementation(libs.liquid.shapes)
    implementation(libs.coroutines.android)
    api(project(":core:theme"))
    implementation(project(":core:shape"))
    implementation(project(":core:ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    testImplementation(libs.junit)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
