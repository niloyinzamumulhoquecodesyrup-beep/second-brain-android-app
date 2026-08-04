plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.secondbrain.lock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.secondbrain.lock"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Override with -PSB_BASE_URL=https://your-domain.vercel.app or by editing gradle.properties.
        val baseUrl = (project.findProperty("SB_BASE_URL") as String?)
            ?: "https://second-brain-pi-six.vercel.app"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        // Supabase Realtime (Mindcord live messages/participants) — the anon key is public by
        // design (RLS on mindcord_messages/mindcord_participants is read-only for it, see the web
        // repo's 022_mindcord.sql), same as it being embedded in the web app's client bundle.
        // Override with -PSB_SUPABASE_URL=... / -PSB_SUPABASE_ANON_KEY=... if the project changes.
        val supabaseUrl = (project.findProperty("SB_SUPABASE_URL") as String?)
            ?: "https://uzscrxlcrxchyckumwls.supabase.co"
        val supabaseAnonKey = (project.findProperty("SB_SUPABASE_ANON_KEY") as String?)
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6c2NyeGxjcnhjaHlja3Vtd2xzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM0MTAxNjgsImV4cCI6MjA5ODk4NjE2OH0.uDWMKhBxGlTlJu6vInBtLCDx4127IUVi7mg0tjItnn4"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Pinned to 1.0.0 (not the latest) — newer releases pull in an androidx.activity transitive
    // dependency that requires compileSdk 36 / AGP 8.9.1+, well ahead of this project's AGP
    // 8.5.2 / compileSdk 34. 1.0.0's own activity-compose pin (1.9.3) is compatible.
    implementation("dev.chrisbanes.haze:haze:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Mindcord's voice/video mesh (Phase 2) — GetStream's actively maintained WebRTC AAR, since
    // Google's own org.webrtc:google-webrtc was pulled from Maven/JCenter years ago. Same
    // org.webrtc.* API surface either way (this fork just repackages upstream releases).
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
