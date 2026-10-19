package art.miguelreis.livestream

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
 private val requestCode = 123
 private val overlayRequestCode = 124
 private var overlayPrompted = false
 private lateinit var status: TextView
 private lateinit var startButton: Button
 private lateinit var stopButton: Button
 private lateinit var inviteAction: Button
 private lateinit var copyAction: Button
 private var session = ""
 private var publisherKey = ""
 private var sharedText = ""
 private val connectionReceiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context?,intent:Intent?) {
   if(session.isBlank())return
   val ok=intent?.getBooleanExtra("connected",false)==true
   val http=intent?.getIntExtra("http",0)?:0
   val error=intent?.getStringExtra("error").orEmpty()
   status.text=if(ok) "● EM DIRETO · ligação ativa" else "A tentar restabelecer ligação…"+if(http>0) " (HTTP $http)" else if(error.isNotBlank()) " ($error)" else ""
  }
 }
 private val endpoint get() = BuildConfig.RELAY_URL.trimEnd('/')

 private fun shareLink() = "$endpoint/watch/$session"
 private fun copyInvite() {
  if (session.isBlank()) return
  (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("LiveStream", shareLink()))
  Toast.makeText(this,"Link copiado",Toast.LENGTH_SHORT).show()
 }
 private fun sendInvite() {
  if (session.isBlank()) return
  val link = shareLink()
  val send = Intent(Intent.ACTION_SEND).apply {
   type = "text/plain"
   putExtra(Intent.EXTRA_TEXT, "Vê isto 👀 Estou a partilhar o meu ecrã em direto: $link" + if(sharedText.isNotBlank()) "\\nConteúdo: $sharedText" else "")
  }
  // O utilizador escolhe o WhatsApp ou outra aplicação, sem precisar de copiar o link.
  startActivity(Intent.createChooser(send, "Enviar convite"))
 }

 private fun begin() {
  if (!Settings.canDrawOverlays(this) && !overlayPrompted) {
   overlayPrompted = true
   AlertDialog.Builder(this).setTitle("Aviso de transmissão").setMessage("Para mostrar o aviso EM DIRETO sobre outras aplicações, autorize a apresentação sobre outras aplicações. Pode continuar sem esta opção.").setPositiveButton("Autorizar") { _, _ -> startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")),overlayRequestCode) }.setNegativeButton("Continuar sem aviso") { _, _ -> begin() }.show()
   return
  }
  if (!endpoint.startsWith("https://")) {
   status.text = "Ligação ainda não configurada"
   AlertDialog.Builder(this)
    .setTitle("Falta ativar a ligação")
    .setMessage("Esta versão ainda não tem o endereço do servidor incluído. É necessário configurar o endereço público na compilação da APK.")
    .setPositiveButton("OK", null).show()
   return
  }
  session = UUID.randomUUID().toString()
  publisherKey = UUID.randomUUID().toString() + UUID.randomUUID().toString()
  val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
 }

 private fun stopSharing() {
  startService(Intent(this, CaptureService::class.java).apply { action = "STOP" })
  session = ""
  publisherKey = ""
  status.text = "Partilha terminada"
  startButton.isEnabled = true
  stopButton.isEnabled = false
  inviteAction.isEnabled=false;copyAction.isEnabled=false
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val filter=IntentFilter("art.miguelreis.livestream.STATUS")
  if(Build.VERSION.SDK_INT>=33)registerReceiver(connectionReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else { @Suppress("DEPRECATION") registerReceiver(connectionReceiver,filter) }
  if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") sharedText=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(500)
  val navy=Color.rgb(10,17,31)
  val cardColor=Color.rgb(24,35,56)
  val muted=Color.rgb(164,180,205)
  val accent=Color.rgb(57,196,161)
  fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  fun rounded(color:Int,radius:Int=18)=android.graphics.drawable.GradientDrawable().apply {
   setColor(color);cornerRadius=dp(radius).toFloat()
  }
  fun label(value:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply {
   text=value;textSize=size;setTextColor(color)
  }
  val scroll=ScrollView(this).apply {fillViewport=true;setBackgroundColor(navy)}
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(32),dp(22),dp(24))
  }
  scroll.addView(root)
  root.addView(label("●  LIVESTREAM",13f,accent))
  val title=label("Mostra o que estás a ver.",30f).apply {
   setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,dp(16),0,dp(10))
  }
  root.addView(title)
  root.addView(label("Partilha o teu ecrã com um simples link. Quem recebe só precisa de abrir.",15f,muted).apply {setPadding(0,0,0,dp(24))})
  val card=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(20),dp(20),dp(20),dp(20))
  }
  root.addView(card)
  card.addView(label("ESTADO DA TRANSMISSÃO",11f,muted).apply {setPadding(0,0,0,dp(10))})
  status=label("●  Pronto para começar",17f,accent).apply {setPadding(0,0,0,dp(18))}
  card.addView(status)
  startButton=Button(this).apply {
   text="▶  COMEÇAR A PARTILHAR";textSize=16f;isAllCaps=false
   setTextColor(navy);background=rounded(accent,14)
   setOnClickListener {begin()}
  }
  card.addView(startButton,LinearLayout.LayoutParams(-1,dp(58)))
  stopButton=Button(this).apply {
   text="■  Parar transmissão";textSize=15f;isAllCaps=false;isEnabled=false
   setOnClickListener {stopSharing()}
  }
  card.addView(stopButton,LinearLayout.LayoutParams(-1,dp(54)).apply {topMargin=dp(12)})
  root.addView(label("CONVIDAR ALGUÉM",12f,muted).apply {setPadding(0,dp(28),0,dp(12))})
  val actions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
  root.addView(actions)
  val inviteButton=Button(this).apply {
   text="↗  Enviar convite";isAllCaps=false;textSize=13f;isEnabled=false
   setOnClickListener {sendInvite()}
  }
  val copyButton=Button(this).apply {
   text="⧉  Copiar link";isAllCaps=false;textSize=13f;isEnabled=false
   setOnClickListener {copyInvite()}
  }
  actions.addView(inviteButton,LinearLayout.LayoutParams(0,dp(54),1f))
  actions.addView(copyButton,LinearLayout.LayoutParams(0,dp(54),1f).apply {marginStart=dp(8)})
  inviteAction=inviteButton;copyAction=copyButton
  val hint=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(18),dp(18),dp(18),dp(18))
  }
  root.addView(hint,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(26)})
  hint.addView(label("COMO FUNCIONA",12f,accent).apply {setPadding(0,0,0,dp(10))})
  hint.addView(label("1  Toca em Começar a partilhar.\\n2  Autoriza a captura do Android.\\n3  Envia o link pelo WhatsApp.\\n4  Continua a usar o telemóvel normalmente.",14f))
  if(sharedText.isNotBlank())hint.addView(label("\\nRecebemos um conteúdo de outra aplicação. Podes iniciar a partilha agora.",13f,muted))
  root.addView(label("O indicador EM DIRETO aparece sobre outras aplicações, se autorizado. Vídeos protegidos, como alguns canais de TV, podem surgir a preto. Esta versão não transmite áudio.",12f,muted).apply {setPadding(0,dp(22),0,dp(8))})
  setContentView(scroll)
 }

 override fun onDestroy(){try{unregisterReceiver(connectionReceiver)}catch(_:Exception){};super.onDestroy()}
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  super.onActivityResult(requestCode, resultCode, data)
  if(requestCode==overlayRequestCode){begin();return}
  if (requestCode != this.requestCode) return
  if (resultCode == RESULT_OK && data != null) {
   startForegroundService(Intent(this, CaptureService::class.java).apply {
    putExtra("resultCode", resultCode)
    putExtra("resultData", data)
    putExtra("endpoint", endpoint)
    putExtra("session", session)
    putExtra("publisherKey", publisherKey)
    putExtra("sharedText",sharedText)
   })
   status.text = "Captura iniciada · a estabelecer ligação"
   startButton.isEnabled = false
   stopButton.isEnabled = true
   inviteAction.isEnabled=true;copyAction.isEnabled=true
   sendInvite()
  } else {
   session = ""
   publisherKey = ""
   status.text = "Partilha cancelada"
   startButton.isEnabled = true
   stopButton.isEnabled = false
  }
 }
}
