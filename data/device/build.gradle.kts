plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android {
 namespace = "com.qingyu.personalaios.data.device"
 compileSdk = 36
 defaultConfig { minSdk = 33 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 api(project(":domain"))
 implementation(libs.coroutines.android)
 implementation(libs.androidx.core)
 testImplementation(libs.junit)
 testImplementation(libs.coroutines.test)
}
