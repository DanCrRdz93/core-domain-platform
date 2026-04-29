plugins {
    kotlin("multiplatform") version "2.3.10"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

// Expose group/version on Project so Gradle composite-build substitution
// matches "io.github.dancrrdz93:coredomainplatform" automatically when this
// build is included via includeBuild(...) by a consumer.
group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}
