plugins {  
    id("com.android.application")  
    id("org.jetbrains.kotlin.android")  
    id("com.google.dagger.hilt.android")  
    id("com.google.devtools.ksp")  
}  
  
android {  
    namespace = "com.pcrjjc.app"  
    compileSdk = 34  
  
    defaultConfig {  
        applicationId = "com.pcrjjc.app"  
        minSdk = 26  
        targetSdk = 34  
        versionCode = 4  
        versionName = "3.0.7"  
  
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"  
        vectorDrawables {  
            useSupportLibrary = true  
        }  
  
        ksp {  
            arg("room.schemaLocation", "$projectDir/schemas")  
        }  
    }  
  
    signingConfigs {  
        create("release") {  
            storeFile = file("../keystore/release.jks")  
            storePassword = "android123"  
            keyAlias = "release"  
            keyPassword = "android123"  
        }  
    }  
  
    buildTypes {  
        debug {  
            signingConfig = signingConfigs.getByName("release")  
        }  
        release {  
            signingConfig = signingConfigs.getByName("release")  
            isMinifyEnabled = false  
            proguardFiles(  
                getDefaultProguardFile("proguard-android-optimize.txt"),  
                "proguard-rules.pro"  
            )  
        }  
    }
	
	buildTypes {  
        release {  
            isMinifyEnabled = false  
            proguardFiles(  
                getDefaultProguardFile("proguard-android-optimize.txt"),  
                "proguard-rules.pro"  
            )  
        }  
    }  
    compileOptions {  
        sourceCompatibility = JavaVersion.VERSION_17  
        targetCompatibility = JavaVersion.VERSION_17  
    }  
    kotlinOptions {  
		jvmTarget = "17"  
		freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"  
	} 
    buildFeatures {  
        compose = true  
        buildConfig = true  // ★ 新增：让代码能访问 BuildConfig.VERSION_NAME  
    }  
    composeOptions {  
        kotlinCompilerExtensionVersion = "1.5.8"  
    }  
    packaging {  
        resources {  
            excludes += "/META-INF/{AL2.0,LGPL2.1}"  
        }  
    }  
}  
  
dependencies {  
    // Compose BOM  
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")  
    implementation(composeBom)  
    implementation("androidx.compose.ui:ui")  
    implementation("androidx.compose.ui:ui-graphics")  
    implementation("androidx.compose.ui:ui-tooling-preview")  
    implementation("androidx.compose.material3:material3")  
    implementation("androidx.compose.material:material-icons-extended")  
    implementation("androidx.activity:activity-compose:1.8.2") 
    implementation("org.opencv:opencv:4.9.0")	
	implementation("androidx.compose.foundation:foundation")  // HorizontalPager
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")  
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")  
  
    // Navigation Compose  
    implementation("androidx.navigation:navigation-compose:2.7.6")  
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")  
  
    // Room  
    implementation("androidx.room:room-runtime:2.6.1")  
    implementation("androidx.room:room-ktx:2.6.1")  
    ksp("androidx.room:room-compiler:2.6.1")  
  
    // Hilt  
    implementation("com.google.dagger:hilt-android:2.50")  
    ksp("com.google.dagger:hilt-compiler:2.50")  
  
    // WorkManager  
    implementation("androidx.work:work-runtime-ktx:2.9.0")  
    implementation("androidx.hilt:hilt-work:1.1.0")  
    ksp("androidx.hilt:hilt-compiler:1.1.0")  
  
    // OkHttp  
    implementation("com.squareup.okhttp3:okhttp:4.12.0")  
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") 
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")  
  
    // Coil (图片加载)  
    implementation("io.coil-kt:coil-compose:2.6.0") 
  
    // MessagePack  
    implementation("org.msgpack:msgpack-core:0.9.8")  
  
    // DataStore  
    implementation("androidx.datastore:datastore-preferences:1.0.0")  
  
    // Kotlin Coroutines  
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")  
  
    // Core KTX  
    implementation("androidx.core:core-ktx:1.12.0")  
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")  
  
    // JSON  
    debugImplementation("androidx.compose.ui:ui-tooling")  
    debugImplementation("androidx.compose.ui:ui-test-manifest")  
}