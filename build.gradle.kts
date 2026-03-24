plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            failOnNonReproducibleResolution()
        }
    }
}
