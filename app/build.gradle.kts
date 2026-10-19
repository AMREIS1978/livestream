plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val relayUrl = (System.getenv("LIVESTREAM_SERVER_URL") ?: "").trim().trimEnd('/')
android {
 namespace = "art.miguelreis.livestream"
 compileSdk = 35
 defaultConfig {
  applicationId = "art.miguelreis.livestream"
  minSdk = 29
  targetSdk = 35
  versionCode = 10
  versionName = "1.0.0"
  buildConfigField("String", "RELAY_URL", "\""+relayUrl.replace("\\","\\\\").replace("\"","\\\"")+"\"")
 }
 buildFeatures { buildConfig = true }
}
kotlin { jvmToolchain(17) }