package art.miguelreis.livestream
import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import java.util.UUID

class MainActivity: Activity() {
 private val requestCode=123
 private lateinit var status:TextView
 private lateinit var shareButton:Button
 private var session=""
 private var publisherKey=""
 private val endpoint get()=BuildConfig.RELAY_URL
 private fun button(label:String,action:()->Unit)=Button(this).apply{
  text=label;textSize=17f;setPadding(16,18,16,18);setOnClickListener{action()}
 }
 private fun begin(){
  if(!endpoint.startsWith("https://")){
   status.text="● Servidor gratuito ainda não publicado"
   AlertDialog.Builder(this).setTitle("Ligação por configurar")
    .setMessage("A APK ainda não tem um servidor de transmissão associado. É necessário publicar o relay gratuito e configurar LIVESTREAM_SERVER_URL no GitHub antes de compilar.")
    .setPositiveButton("OK",null).show()
   return
  }
  session=UUID.randomUUID().toString()
  publisherKey=UUID.randomUUID().toString()+UUID.randomUUID().toString()
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(mgr.createScreenCaptureIntent(),requestCode)
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(36,64,36,32)
   setBackgroundColor(Color.rgb(12,18,33))
  }
  fun label(t:String,size:Float)=TextView(this).apply{
   text=t;textSize=size;setTextColor(Color.WHITE);setPadding(0,0,0,24)
  }
  root.addView(label("LiveStream",32f))
  root.addView(label("O seu telemóvel funciona normalmente enquanto partilha o ecrã.",17f))
  status=label("● Transmissão parada",16f);root.addView(status)
  root.addView(button("INICIAR PARTILHA DO ECRÃ"){begin()})
  shareButton=button("ENVIAR LINK PELO WHATSAPP"){
   if(session.isBlank()){Toast.makeText(this,"Inicie primeiro a partilha",Toast.LENGTH_SHORT).show();return@button}
   val link="$endpoint/watch/$session"
   startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
    type="text/plain";putExtra(Intent.EXTRA_TEXT,"Veja o meu ecrã em direto: $link")
   },"Enviar link"))
  }
  shareButton.isEnabled=false
  root.addView(shareButton)
  root.addView(button("PARAR TRANSMISSÃO"){
   startService(Intent(this,CaptureService::class.java).apply{action="STOP"})
   session="";publisherKey="";shareButton.isEnabled=false;status.text="● Transmissão parada"
  })
  root.addView(label("A transmissão requer autorização do Android. Conteúdos protegidos podem bloquear a captura. Versão de teste: imagem, ainda sem áudio.",13f))
  setContentView(root)
 }
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==this.requestCode && resultCode==RESULT_OK && data!=null){
   startForegroundService(Intent(this,CaptureService::class.java).apply{
    putExtra("resultCode",resultCode);putExtra("resultData",data)
    putExtra("endpoint",endpoint);putExtra("session",session);putExtra("publisherKey",publisherKey)
   })
   status.text="● Captura autorizada · a enviar imagem"
   shareButton.isEnabled=true
  } else {session="";publisherKey="";shareButton.isEnabled=false;status.text="● Captura não autorizada"}
 }
}