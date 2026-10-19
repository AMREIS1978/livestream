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
   putExtra(Intent.EXTRA_TEXT, "Estou a partilhar o meu ecrã. Abra este link para acompanhar: $link")
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
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(36, 64, 36, 36)
   setBackgroundColor(Color.rgb(12, 18, 33))
  }
  fun label(textValue: String, size: Float) = TextView(this).apply {
   text = textValue
   textSize = size
   setTextColor(Color.WHITE)
   setPadding(0, 0, 0, 24)
  }
  root.addView(label("LiveStream", 32f))
  root.addView(label("Partilhe o ecrã como numa chamada.", 18f))
  status = label("Pronta para partilhar", 16f)
  root.addView(status)
  startButton = Button(this).apply {
   text = "PARTILHAR ECRÃ"
   textSize = 20f
   setPadding(16, 24, 16, 24)
   setOnClickListener { begin() }
  }
  root.addView(startButton)
  stopButton = Button(this).apply {
   text = "PARAR PARTILHA"
   textSize = 17f
   isEnabled = false
   setOnClickListener { stopSharing() }
  }
  root.addView(stopButton)
  val inviteButton=Button(this).apply { text="ENVIAR LINK / WHATSAPP";isEnabled=false;setOnClickListener { sendInvite() } }
  root.addView(inviteButton)
  val copyButton=Button(this).apply { text="COPIAR LINK";isEnabled=false;setOnClickListener { copyInvite() } }
  root.addView(copyButton)
  inviteAction=inviteButton;copyAction=copyButton
  root.addView(label("Depois de autorizar a captura, escolha a pessoa a quem quer enviar o convite. A partilha continua ao abrir outras aplicações.", 15f))
  root.addView(label("Versão experimental: imagens sucessivas, sem áudio. Algumas aplicações bloqueiam conteúdos protegidos.", 13f))
  setContentView(root)
 }

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
