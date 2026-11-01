import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val appId: String = providers.gradleProperty("APP_ID").getOrElse("uz.robutpit.znatoki")
val serverUrl: String = providers.gradleProperty("SERVER_BASE_URL").getOrElse("https://game.robutpit.com")

// Release-подпись: keystore.properties (в .gitignore) или переменные окружения (CI)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "uz.robutpit.znatoki"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("APP_VERSION_CODE").getOrElse("1").toInt()
        versionName = providers.gradleProperty("APP_VERSION_NAME").getOrElse("1.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SERVER_BASE_URL", "\"$serverUrl\"")
        buildConfigField("String", "APP_TITLE", "\"Клуб знатоков\"")
        // false = настоящий сервер game.robutpit.com; в debug можно включить мок в «Настройки → Отладка»
        buildConfigField("boolean", "USE_MOCK", "false")
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("ru", "uz")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "ZNATOKI_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "ZNATOKI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ZNATOKI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ZNATOKI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rc = signingConfigs.getByName("release")
            signingConfig = if (rc.storeFile != null) rc else signingConfigs.getByName("debug")
            buildConfigField("boolean", "USE_MOCK", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
    implementation(libs.livekit.android)
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Robolectric: заранее скачиваем android-all через Gradle-репозитории (кэш/зеркало) и запускаем тесты офлайн
val robolectricRuntime: Configuration by configurations.creating
dependencies { robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7") }
val copyRobolectricJars = tasks.register<Copy>("copyRobolectricJars") {
    from(robolectricRuntime)
    into(layout.buildDirectory.dir("robolectric-jars"))
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-jars").get().asFile.absolutePath)
    maxHeapSize = "3g"
}
