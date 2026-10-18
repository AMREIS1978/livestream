package art.miguelreis.livestream
import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import android.view.View
import java.util.UUID

class MainActivity: Activity() {
 private val requestCode = 123
 private lateinit var status: TextView
 private lateinit var endpoint: EditText
 private var session = ""
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("settings", MODE_PRIVATE)
  val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,32) }
  layout.addView(TextView(this).apply { text="LiveStream"; textSize=30f })
  layout.addView(TextView(this).apply { text="Partilhe o ecrã através de um link. Não partilhe dados privados." })
  endpoint=EditText(this).apply { hint="Servidor HTTPS (ex.: https://seu-servidor.com)"; setSingleLine(true); setText(prefs.getString("endpoint","")) }
  layout.addView(endpoint)
  status=TextView(this).apply { text="Transmissão parada" }
  layout.addView(status)
  layout.addView(Button(this).apply { text="Iniciar transmissão"; setOnClickListener {
   val url=endpoint.text.toString().trim().trimEnd('/')
   if(!url.startsWith("https://")) { Toast.makeText(this@MainActivity,"Introduza um servidor HTTPS válido",Toast.LENGTH_LONG).show();return@setOnClickListener }
   prefs.edit().putString("endpoint",url).apply()
   session=UUID.randomUUID().toString()
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   startActivityForResult(mgr.createScreenCaptureIntent(),requestCode)
  } })
  layout.addView(Button(this).apply { text="Partilhar link por WhatsApp"; setOnClickListener {
   if(session.isEmpty()) { Toast.makeText(this@MainActivity,"Inicie a transmissão",Toast.LENGTH_SHORT).show();return@setOnClickListener }
   val link=endpoint.text.toString().trim().trimEnd('/')+"/watch/"+session
   val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,link) }
   startActivity(Intent.createChooser(intent,"Partilhar transmissão"))
  } })
  layout.addView(Button(this).apply { text="Parar transmissão";setOnClickListener {
   startService(Intent(this@MainActivity,CaptureService::class.java).apply { action="STOP" })
   status.text="Transmissão parada";session=""
  } })
  setContentView(layout)
 }
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==this.requestCode && resultCode==RESULT_OK && data!=null) {
   val intent=Intent(this,CaptureService::class.java).apply {
    putExtra("resultCode",resultCode);putExtra("resultData",data)
    putExtra("endpoint",endpoint.text.toString().trim().trimEnd('/'));putExtra("session",session)
   }
   startForegroundService(intent)
   status.text="Transmissão iniciada: "+session
  } else { session="";status.text="Captura não autorizada" }
 }
}