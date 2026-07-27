plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.pressureagent.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pressureagent.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        // ── Mock / Real 后端切换 ──────────────────────────────────────
        // USE_MOCK_AGENT=true  → 所有请求被本地 MockAgent 拦截，无需网络
        // USE_MOCK_AGENT=false → 连接 AGENT_API_BASE_URL 的真实 Agent API
        //
        // AGENT_API_BASE_URL 说明:
        //   模拟器: http://10.0.2.2:8000  (Android 模拟器到宿主机 localhost)
        //   真机:   http://<你电脑IP>:8000  (需在同一局域网)
        //
        // 要接真实后端:
        //   1. cd services/agent-api && .\start.ps1
        //   2. 把下面 USE_MOCK_AGENT 改成 "false"
        //   3. Build & run
        // ────────────────────────────────────────────────────────────────
        debug {
            buildConfigField("boolean", "USE_MOCK_AGENT", "false")
            buildConfigField("String", "AGENT_API_BASE_URL", "\"https://auri-agent-api.onrender.com\"")
            buildConfigField("String", "AGENT_API_TOKEN", "\"auri-team-7f3e2a91c8b64d40a5e96f17\"")
        }
        release {
            buildConfigField("boolean", "USE_MOCK_AGENT", "false")
            buildConfigField("String", "AGENT_API_BASE_URL", "\"https://auri-agent-api.onrender.com\"")
            buildConfigField("String", "AGENT_API_TOKEN", "\"auri-team-7f3e2a91c8b64d40a5e96f17\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Don't compress ONNX model files in assets (they're already compressed)
    androidResources {
        noCompress += listOf("onnx")
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle + Activity
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)

    // Ktor client (SSE streaming)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Sherpa ONNX — offline speech recognition (local AAR)
    implementation(fileTree("libs") { include("*.aar") })
}
