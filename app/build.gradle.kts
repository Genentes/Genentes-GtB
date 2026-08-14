plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ch.ecoandco.enfantsDesCopains" // <--- REMPLACEZ CECI par votre vrai nom de package trouvé plus tôt !
    compileSdk = 34 // Ou la version installée chez vous (33, 34, 35...)

    defaultConfig {
        applicationId = "ch.ecoandco.enfantsDesCopains" // <--- REMPLACEZ CECI aussi par votre vrai nom de package
        minSdk = 26 // Version minimum d'Android (26 = Android 8.0)
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    // Les librairies de base nécessaires pour l'interface (Views)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // La librairie pour le RecyclerView (indispensable pour votre tableau)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}