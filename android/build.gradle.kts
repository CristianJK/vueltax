plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt)
}

// detekt se aplica solo en la raíz y analiza las fuentes de todos los módulos, para no
// depender de la integración del plugin con Android.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src/main/kotlin",
            "app/src/test/kotlin",
            "domain/src/main/kotlin",
            "domain/src/test/kotlin",
        ),
    )
}
