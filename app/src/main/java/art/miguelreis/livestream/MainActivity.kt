package art.miguelreis.livestream

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
 private val requestCode = 123
 private val microphoneRequestCode = 126
 private val cameraRequestCode = 127
 private lateinit var cameraMode:RadioGroup
 private lateinit var cameraPreview:CameraPreview
 private lateinit var previewLabel:TextView
 private lateinit var micCheck:CheckBox
 private val recordRequestCode = 125
 private lateinit var recordButton:Button
 private lateinit var stopRecordButton:Button
 private lateinit var status: TextView
 private lateinit var startButton: Button
 private lateinit var stopButton: Button
 private lateinit var inviteAction: Button
 private lateinit var copyAction: Button
 private var session = ""
 private var publisherKey = ""
 private var sharedText = ""
 private lateinit var contentInput:EditText
 private lateinit var saveLiveCheck:CheckBox
 private lateinit var captureMode:RadioGroup
 private var shareEntireScreen=true
 private fun shareContent(){
  val raw=contentInput.text.toString().trim()
  if(raw.isBlank()){Toast.makeText(this,"Cole primeiro o link do conteúdo",Toast.LENGTH_SHORT).show();return}
  val match=Regex("https?://[^\\s]+").find(raw)
  if(match==null){Toast.makeText(this,"Insira um endereço https:// válido",Toast.LENGTH_SHORT).show();return}
  val url=match.value
  val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Vê isto 👀\\n$url")}
  startActivity(Intent.createChooser(send,"Partilhar conteúdo"))
 }
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
  if(cameraMode.checkedRadioButtonId!=3000 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.CAMERA),cameraRequestCode)
   return
  }
  if(micCheck.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),microphoneRequestCode)
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
  cameraPreview.stop()
  shareEntireScreen = captureMode.checkedRadioButtonId == 2001
  val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(if(Build.VERSION.SDK_INT>=34 && shareEntireScreen)mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else mgr.createScreenCaptureIntent(), requestCode)
 }

 private fun beginRecording(){
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(if(Build.VERSION.SDK_INT>=34)mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else mgr.createScreenCaptureIntent(),recordRequestCode)
 }
 private fun stopRecording(){
  startService(Intent(this,RecordService::class.java).apply{action="STOP"})
  recordButton.isEnabled=true;stopRecordButton.isEnabled=false
  startButton.isEnabled=true;saveLiveCheck.isEnabled=true
  Toast.makeText(this,"A guardar em Filmes / LiveStream",Toast.LENGTH_LONG).show()
 }
 private fun stopSharing() {
  startService(Intent(this, CaptureService::class.java).apply { action = "STOP" })
  session = ""
  publisherKey = ""
  status.text = if(saveLiveCheck.isChecked) "Partilha terminada · vídeo guardado em Filmes/LiveStream" else "Partilha terminada"
  startButton.isEnabled = true
  saveLiveCheck.isEnabled=true
  micCheck.isEnabled=true
  for(i in 0 until cameraMode.childCount)cameraMode.getChildAt(i).isEnabled=true
  for(i in 0 until captureMode.childCount)captureMode.getChildAt(i).isEnabled=Build.VERSION.SDK_INT>=34 || i==0
  recordButton.isEnabled=true
  stopRecordButton.isEnabled=false
  if(cameraMode.checkedRadioButtonId!=3000 && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){cameraPreview.visibility=android.view.View.VISIBLE;cameraPreview.showCamera(cameraMode.checkedRadioButtonId==3001)}
  stopButton.isEnabled = false
  inviteAction.isEnabled=false;copyAction.isEnabled=false
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val filter=IntentFilter("art.miguelreis.livestream.STATUS")
  if(Build.VERSION.SDK_INT>=33)registerReceiver(connectionReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else { @Suppress("DEPRECATION") registerReceiver(connectionReceiver,filter) }
  if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") sharedText=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(500)
  val navy=Color.rgb(246,248,252)
  val cardColor=Color.WHITE
  val muted=Color.rgb(91,105,126)
  val accent=Color.rgb(24,119,242)
  fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  fun rounded(color:Int,radius:Int=18)=android.graphics.drawable.GradientDrawable().apply {
   setColor(color);cornerRadius=dp(radius).toFloat()
  }
  fun label(value:String,size:Float,color:Int=Color.rgb(24,36,56))=TextView(this).apply {
   text=value;textSize=size;setTextColor(color)
  }
  val scroll=ScrollView(this).apply {isFillViewport=true;setBackgroundColor(navy)}
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(32),dp(22),dp(24))
  }
  scroll.addView(root)
  root.addView(label("●  LiveStream",16f,accent).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  val title=label("Vê isto. Em direto.",29f).apply {
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
   text="▶  Partilhar o meu ecrã";textSize=16f;isAllCaps=false
   setTextColor(Color.WHITE);background=rounded(accent,14)
   setOnClickListener {begin()}
  }
  captureMode=RadioGroup(this).apply{
   orientation=RadioGroup.VERTICAL
   addView(RadioButton(this@MainActivity).apply{id=2001;text="Todo o telemóvel";isChecked=true;setTextColor(Color.rgb(24,36,56))})
   addView(RadioButton(this@MainActivity).apply{id=2002;text="Apenas uma aplicação";setTextColor(Color.rgb(24,36,56))})
  }
  card.addView(label("O QUE QUER PARTILHAR?",12f,muted).apply{setPadding(0,0,0,dp(6))})
  card.addView(captureMode)
  if(Build.VERSION.SDK_INT<34){
   captureMode.findViewById<RadioButton>(2002).isEnabled=false
   card.addView(label("A seleção de uma única aplicação requer Android 14 ou superior.",12f,muted))
  }
  card.addView(startButton,LinearLayout.LayoutParams(-1,dp(58)))
  saveLiveCheck=CheckBox(this).apply{text="Gravar também esta transmissão (MP4)";isChecked=false;setTextColor(Color.rgb(24,36,56))}
  card.addView(saveLiveCheck)
  micCheck=CheckBox(this).apply{text="Transmitir som do microfone";isChecked=false;setTextColor(Color.rgb(24,36,56))}
  card.addView(micCheck)
  card.addView(label("CÂMARA NA TRANSMISSÃO (OPCIONAL)",12f,muted))
  cameraMode=RadioGroup(this).apply{
   orientation=RadioGroup.VERTICAL
   addView(RadioButton(this@MainActivity).apply{id=3000;text="Desligada";isChecked=true;setTextColor(Color.rgb(24,36,56))})
   addView(RadioButton(this@MainActivity).apply{id=3001;text="Câmara frontal";setTextColor(Color.rgb(24,36,56))})
   addView(RadioButton(this@MainActivity).apply{id=3002;text="Câmara traseira";setTextColor(Color.rgb(24,36,56))})
  }
  card.addView(cameraMode)
  previewLabel=label("Pré-visualização da câmara (apenas nesta página)",12f,muted)
  card.addView(previewLabel)
  card.addView(label("A câmara aparece nesta página antes de começar e será incorporada no vídeo partilhado. Não é uma janela flutuante.",12f,muted))
  cameraPreview=CameraPreview(this).apply{visibility=android.view.View.GONE}
  card.addView(cameraPreview,LinearLayout.LayoutParams(-1,dp(190)))
  cameraMode.setOnCheckedChangeListener { _,selected ->
   cameraPreview.stop()
   if(selected==3000){cameraPreview.visibility=android.view.View.GONE;previewLabel.text="Câmara desligada"}
   else if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
    cameraPreview.visibility=android.view.View.VISIBLE
    previewLabel.text=if(selected==3001)"Pré-visualização · câmara frontal" else "Pré-visualização · câmara traseira"
    cameraPreview.showCamera(selected==3001)
   }else{previewLabel.text="Autorize a câmara para ver a pré-visualização";requestPermissions(arrayOf(Manifest.permission.CAMERA),cameraRequestCode)}
  }
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
  val recordCard=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(18),dp(18),dp(18),dp(18))
  }
  root.addView(recordCard,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(24)})
  recordCard.addView(label("Gravar ecrã",21f).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  recordCard.addView(label("Guarda um vídeo MP4 no telemóvel, sem ligação à internet e sem custos. Sem áudio nesta versão.",13f,muted).apply{setPadding(0,dp(8),0,dp(10))})
  recordButton=Button(this).apply{
   text="●  Começar gravação";isAllCaps=false
   setOnClickListener{beginRecording()}
  }
  recordCard.addView(recordButton,LinearLayout.LayoutParams(-1,dp(52)))
  stopRecordButton=Button(this).apply{
   text="■  Parar e guardar";isAllCaps=false;isEnabled=false
   setOnClickListener{stopRecording()}
  }
  recordCard.addView(stopRecordButton,LinearLayout.LayoutParams(-1,dp(52)))
  val contentCard=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(18),dp(18),dp(18),dp(18))
  }
  root.addView(contentCard,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(24)})
  contentCard.addView(label("Vê isto 👀",21f).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  contentCard.addView(label("Partilha o link original de um vídeo, canal ou página. O destinatário abre-o na aplicação ou site oficial.",13f,muted).apply{setPadding(0,dp(8),0,dp(10))})
  contentInput=EditText(this).apply{
   hint="Colar link do conteúdo";textSize=15f;setSingleLine(true)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
   setTextColor(Color.rgb(24,36,56));setHintTextColor(muted)
   setText(Regex("https?://[^\\s]+").find(sharedText)?.value.orEmpty())
  }
  contentCard.addView(contentInput)
  contentCard.addView(Button(this).apply{
   text="↗  Enviar «Vê isto»";isAllCaps=false
   setOnClickListener{shareContent()}
  },LinearLayout.LayoutParams(-1,dp(52)))
  val hint=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(18),dp(18),dp(18),dp(18))
  }
  root.addView(hint,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(26)})
  hint.addView(label("COMO FUNCIONA",12f,accent).apply {setPadding(0,0,0,dp(10))})
  hint.addView(label("1  Escolhe partilhar o ecrã ou um link.\\n2  Autoriza a captura, se necessário.\\n3  Envia o convite.\\n4  Continua a utilizar o telemóvel.",14f))
  if(sharedText.isNotBlank())hint.addView(label("\\nRecebemos um conteúdo de outra aplicação. Podes iniciar a partilha agora.",13f,muted))
  root.addView(label("Pode partilhar o ecrã completo ou uma aplicação compatível. Vídeos protegidos podem surgir a preto. O microfone transmite voz quando ativado; a gravação MP4 ainda não inclui áudio.",12f,muted).apply {setPadding(0,dp(22),0,dp(8))})
  setContentView(scroll)
 }

 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==cameraRequestCode){
   if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED){
    if(cameraMode.checkedRadioButtonId!=3000){cameraPreview.visibility=android.view.View.VISIBLE;cameraPreview.showCamera(cameraMode.checkedRadioButtonId==3001)}
   }else{cameraMode.check(3000);Toast.makeText(this,"Câmara desativada",Toast.LENGTH_SHORT).show()}
  }
  if(requestCode==microphoneRequestCode){
   if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)begin()
   else{micCheck.isChecked=false;Toast.makeText(this,"Microfone desativado",Toast.LENGTH_SHORT).show();begin()}
  }
 }
 override fun onDestroy(){cameraPreview.release();try{unregisterReceiver(connectionReceiver)}catch(_:Exception){};super.onDestroy()}
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  super.onActivityResult(requestCode, resultCode, data)
  if(requestCode==recordRequestCode){
   if(resultCode==RESULT_OK && data!=null){
    startForegroundService(Intent(this,RecordService::class.java).apply{putExtra("resultCode",resultCode);putExtra("resultData",data)})
    recordButton.isEnabled=false;stopRecordButton.isEnabled=true
    startButton.isEnabled=false;saveLiveCheck.isEnabled=false
    Toast.makeText(this,"Gravação iniciada",Toast.LENGTH_SHORT).show()
   }
   return
  }
  if (requestCode != this.requestCode) return
  if (resultCode == RESULT_OK && data != null) {
   cameraPreview.stop()
   startForegroundService(Intent(this, CaptureService::class.java).apply {
    putExtra("resultCode", resultCode)
    putExtra("resultData", data)
    putExtra("endpoint", endpoint)
    putExtra("session", session)
    putExtra("publisherKey", publisherKey)
    putExtra("sharedText",sharedText)
    putExtra("saveAlongside",saveLiveCheck.isChecked)
    putExtra("micEnabled",micCheck.isChecked)
    putExtra("cameraMode",when(cameraMode.checkedRadioButtonId){3001->1;3002->2;else->0})
   })
   status.text = if(saveLiveCheck.isChecked) "A transmitir e gravar · a estabelecer ligação" else "Captura iniciada · a estabelecer ligação"
   startButton.isEnabled = false
   saveLiveCheck.isEnabled=false
   micCheck.isEnabled=false
   for(i in 0 until cameraMode.childCount)cameraMode.getChildAt(i).isEnabled=false
   captureMode.isEnabled=false
   for(i in 0 until captureMode.childCount)captureMode.getChildAt(i).isEnabled=false
   recordButton.isEnabled=false
   stopRecordButton.isEnabled=false
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
