plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.darly.alarm"; compileSdk = 35
 defaultConfig { applicationId = "com.darly.alarm"; minSdk = 26; targetSdk = 35; versionCode = 23; versionName = "3.5.1"; testInstrumentationRunner = "com.darly.alarm.RailSmokeInstrumentation"; resValue("string","app_name","DARLY") }
 testBuildType = "qa"
 buildTypes { create("qa") { initWith(getByName("debug")); applicationIdSuffix = ".qa"; versionNameSuffix = "-qa"; resValue("string","app_name","DARLY QA") } }
 sourceSets.getByName("qa").java.srcDir("src/androidTest/java")
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies { testImplementation("junit:junit:4.13.2") }
