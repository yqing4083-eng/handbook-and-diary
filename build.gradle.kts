plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        val asciiRoot = providers.gradleProperty("personalaios.testClasspathRoot")
        doFirst {
            asciiRoot.orNull?.let { root ->
                classpath = files(classpath.files.map { file ->
                    if (file.toPath().startsWith(rootDir.toPath())) File(root, file.relativeTo(rootDir).path)
                    else file
                })
            }
        }
    }
}
