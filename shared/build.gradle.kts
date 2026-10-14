import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "app.tasca.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Only for running commonTest on the desktop JVM: it compiles the same jvmAndAndroid code the app runs.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withCompilations { it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            api("androidx.room:room-runtime:2.8.5")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("androidx.datastore:datastore-preferences:1.2.1")
            api("org.jetbrains.compose.runtime:runtime:1.12.1")
            api("org.jetbrains.compose.foundation:foundation:1.12.1")
            api("org.jetbrains.compose.ui:ui:1.12.1")
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.1")
            // The last stable Material 3; the releases paired with Compose 1.10 and later are alphas.
            api("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            // The last stable release; the one paired with Compose 1.12 is still a release candidate.
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
        }
        androidMain.dependencies {
            implementation("androidx.sqlite:sqlite-framework:2.6.2")
        }
        iosMain.dependencies {
            implementation("androidx.sqlite:sqlite-bundled:2.6.2")
        }
        jvmMain.dependencies {
            implementation("androidx.sqlite:sqlite-bundled:2.6.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    for (target in listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64")) add(target, "androidx.room:room-compiler:2.8.5")
}
