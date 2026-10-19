plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val relayUrl = (System.getenv("LIVESTREAM_SERVER_URL") ?: "").trim().trimEnd('/')
android {
 namespace = "art.miguelreis.livestream"
 compileSdk = 35
 defaultConfig {
  applicationId = "art.miguelreis.livestream.duo"
  minSdk = 29
  targetSdk = 35
  versionCode = 22
  versionName = "1.8.1"
  buildConfigField("String", "RELAY_URL", "\""+relayUrl.replace("\\","\\\\").replace("\"","\\\"")+"\"")
 }
 buildFeatures { buildConfig = true }
}
kotlin { jvmToolchain(17) }