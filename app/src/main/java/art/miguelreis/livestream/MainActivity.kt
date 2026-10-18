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
 private var session=""
 private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
 private fun endpoint()=prefs.getString("endpoint","")!!.trim().trimEnd('/')
 private fun button(label:String, action:()->Unit)=Button(this).apply {
  text=label; textSize=17f;setPadding(16,18,16,18);setOnClickListener{action()}
 }
 private fun configure(after:()->Unit={}) {
  val input=EditText(this).apply {
   hint="https://meu-servidor.pt";setSingleLine(true);setText(endpoint())
   inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI
  }
  AlertDialog.Builder(this).setTitle("Configurar servidor")
   .setMessage("Esta configuração é necessária uma única vez. O servidor tem de estar publicado na Internet com HTTPS.")
   .setView(input).setNegativeButton("Cancelar",null)
   .setPositiveButton("Guardar"){_,_->
    val url=input.text.toString().trim().trimEnd('/')
    if(url.startsWith("https://") && url.length>10 && !url.contains(" ")) {
     prefs.edit().putString("endpoint",url).apply();after()
    } else Toast.makeText(this,"Introduza um endereço HTTPS válido",Toast.LENGTH_LONG).show()
   }.show()
 }
 private fun startCapture(){
  if(endpoint().isBlank()){configure { startCapture() };return}
  session=UUID.randomUUID().toString()
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(mgr.createScreenCaptureIntent(),requestCode)
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(36,70,36,32)
   setBackgroundColor(Color.rgb(12,18,33))
  }
  fun title(t:String,size:Float)=TextView(this).apply {
   text=t;textSize=size;setTextColor(Color.WHITE);setPadding(0,0,0,28)
  }
  root.addView(title("LiveStream",32f))
  root.addView(title("O seu ecrã, em direto. Partilhe o link com quem quiser.",17f))
  status=title("● Transmissão parada",16f)
  root.addView(status)
  root.addView(button("●  INICIAR PARTILHA DO ECRÃ"){startCapture()})
  root.addView(button("PARTILHAR LINK POR WHATSAPP"){
   if(session.isBlank()){Toast.makeText(this,"Inicie primeiro a partilha",Toast.LENGTH_SHORT).show();return@button}
   val link=endpoint()+"/watch/"+session
   startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
    type="text/plain";putExtra(Intent.EXTRA_TEXT,"Veja o meu ecrã em direto: $link")
   },"Enviar link"))
  })
  root.addView(button("PARAR TRANSMISSÃO"){
   startService(Intent(this,CaptureService::class.java).apply{action="STOP"})
   session="";status.text="● Transmissão parada"
  })
  root.addView(button("⚙ Configuração do servidor"){configure()})
  root.addView(title("A captura exige autorização do Android. Aplicações protegidas podem bloquear imagem e áudio. Nesta versão, apenas a imagem é enviada.",13f))
  setContentView(root)
 }
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==this.requestCode && resultCode==RESULT_OK && data!=null){
   startForegroundService(Intent(this,CaptureService::class.java).apply {
    putExtra("resultCode",resultCode);putExtra("resultData",data)
    putExtra("endpoint",endpoint());putExtra("session",session)
   })
   status.text="● A partilhar o ecrã"
  } else {session="";status.text="● Captura não autorizada"}
 }
}
