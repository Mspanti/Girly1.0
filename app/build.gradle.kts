plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.pant.girly"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pant.girly"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a") // for TFLite
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
    aaptOptions {
        noCompress("tflite")
    }



    buildFeatures {
        dataBinding = true
        viewBinding = true

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.10.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-database:20.2.2")
    implementation("com.google.firebase:firebase-messaging:23.2.1")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.0.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.androidx.junit.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.common)
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Google Play Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.2.0")
    implementation("androidx.camera:camera-lifecycle:1.2.0")
    implementation("androidx.camera:camera-view:1.2.0")

    // QR Scanner
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Room - use a consistent version
    // Room - choose only ONE version, not both 2.5.1 and 2.6.1
    val room_version = "2.6.1"

    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")



    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.0")

    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation ("com.google.code.gson:gson:2.10.1")


    implementation("com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.1.9")

    //AIML
    implementation("com.google.firebase:firebase-ml-modeldownloader:24.0.3")
    implementation ("org.tensorflow:tensorflow-lite:2.13.0")
    implementation ("org.tensorflow:tensorflow-lite-gpu:2.3.0")
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.3") // for preprocessing
    implementation ("org.tensorflow:tensorflow-lite-task-vision:0.4.3") // optional, for vision use cases
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.1.9")
    implementation ("androidx.camera:camera-core:1.3.1")
    implementation ("androidx.camera:camera-camera2:1.3.1")
    implementation ("androidx.camera:camera-lifecycle:1.3.1")
    implementation ("androidx.camera:camera-view:1.3.1")
    implementation ("com.google.firebase:firebase-storage-ktx:20.3.0")

        implementation("androidx.core:core-ktx:1.13.0-alpha02")
        implementation("androidx.appcompat:appcompat:1.7.0-alpha03")
        implementation("com.google.android.material:material:1.12.0-alpha03")
        implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.1")// Recycler View
        implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0") // Pull-to-refresh
        implementation("com.google.android.gms:play-services-location:21.1.0") // Location Services
        implementation("com.google.firebase:firebase-database-ktx") // Firebase Realtime Database
        implementation("com.google.firebase:firebase-auth-ktx") // Firebase Authentication

        // If you plan to use ViewModel and LiveData (good practice for complex apps)
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0-alpha02")
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.0-alpha02")

        // If you need to load images (e.g., user profile pics, not directly used in this example but good to have)
        implementation("com.github.bumptech.glide:glide:4.16.0")
        annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")







}