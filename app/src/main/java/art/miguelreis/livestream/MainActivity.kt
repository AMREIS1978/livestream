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
import android.provider.Settings
import android.net.Uri
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
 private val requestCode = 123
 private val microphoneRequestCode = 126
 private val cameraRequestCode = 127
 private val overlayRequestCode = 128
 private val overlayPrefs by lazy{getSharedPreferences("presenter",MODE_PRIVATE)}
 private fun askOverlayPreference(){
  if(overlayPrefs.contains("allowOverlay"))return
  AlertDialog.Builder(this).setTitle("Mostrar o apresentador sobre todas as aplicações?")
   .setMessage("Sim: quando iniciar uma transmissão com câmara, a sua imagem aparece automaticamente sobre as outras aplicações. Pode arrastá-la, redimensioná-la e desligá-la. Não: a transmissão continua sem a janela flutuante. Pode alterar esta escolha mais tarde.")
   .setPositiveButton("Sim, ativar"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",true).apply();requestOverlayPermission()}
   .setNegativeButton("Não"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",false).apply()}.show()
 }
 private fun requestOverlayPermission(){
  if(!Settings.canDrawOverlays(this@MainActivity))startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")),overlayRequestCode)
 }
 private lateinit var cameraMode:RadioGroup
 private lateinit var cameraPreview:CameraPreview
 private lateinit var placement:CameraPlacementView
 private lateinit var cameraSizeSeek:SeekBar
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
 private var live=false
 private var pendingStart=false
 private var awaitingOverlay=false
 private fun updateLiveControls(){
  if(!live)return
  cameraPreview.stop();cameraPreview.visibility=android.view.View.GONE
  previewLabel.text="Câmara integrada na transmissão · pré-visualização indisponível durante a partilha"
  startService(Intent(this,CaptureService::class.java).apply{action="CONTROL";putExtra("cameraMode",when(cameraMode.checkedRadioButtonId){3001->1;3002->2;else->0});putExtra("micEnabled",micCheck.isChecked);putExtra("cameraX",placement.xFraction);putExtra("cameraY",placement.yFraction);putExtra("cameraSize",placement.sizeFraction)})
 }
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
  pendingStart=true
  if(!overlayPrefs.contains("allowOverlay")){
   AlertDialog.Builder(this).setTitle("Controlos flutuantes durante a transmissão?")
    .setMessage("Sim: mostra uma janela sobre as outras aplicações para controlar câmara, microfone e terminar a transmissão. O Android pedirá autorização uma única vez.")
    .setPositiveButton("Sim"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",true).apply();awaitingOverlay=true;requestOverlayPermission()}
    .setNegativeButton("Não"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",false).apply();beginWithoutOverlay()}.show()
   return
  }
  if(overlayPrefs.getBoolean("allowOverlay",false) && !Settings.canDrawOverlays(this)){
   AlertDialog.Builder(this).setTitle("Autorizar a janela do apresentador")
    .setMessage("Escolheu mostrar a câmara sobre outras aplicações. O Android precisa de autorizar esta função. Pode continuar sem a janela.")
    .setPositiveButton("Autorizar"){_,_->awaitingOverlay=true;requestOverlayPermission()}
    .setNegativeButton("Continuar sem sobreposição"){_,_->pendingStart=false;beginWithoutOverlay()}.show()
   return
  }
  beginWithoutOverlay()
 }
 private fun beginWithoutOverlay(){
  pendingStart=true
  if(cameraMode.checkedRadioButtonId!=3000 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.CAMERA),cameraRequestCode)
   return
  }
  if(micCheck.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),microphoneRequestCode)
   return
  }
  if (!endpoint.startsWith("https://")) {
   pendingStart=false
   status.text = "Ligação ainda não configurada"
   AlertDialog.Builder(this)
    .setTitle("Falta ativar a ligação")
    .setMessage("Esta versão ainda não tem o endereço do servidor incluído. É necessário configurar o endereço público na compilação da APK.")
    .setPositiveButton("OK", null).show()
   return
  }
  pendingStart=false
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
  live=false
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
  val navy=Color.rgb(8,11,20)
  val cardColor=Color.rgb(23,27,40)
  val muted=Color.rgb(157,164,184)
  val accent=Color.rgb(255,26,91)
  fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  fun rounded(color:Int,radius:Int=18)=android.graphics.drawable.GradientDrawable().apply {
   setColor(color);cornerRadius=dp(radius).toFloat()
  }
  fun label(value:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply {
   text=value;textSize=size;setTextColor(color)
  }
  val scroll=ScrollView(this).apply {isFillViewport=true;setBackgroundColor(navy)}
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(32),dp(22),dp(24))
  }
  scroll.addView(root)
  root.addView(label("●  LiveStream",18f,accent).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  val title=label("O teu ecrã. Em direto.",29f).apply {
   setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,dp(16),0,dp(10))
  }
  root.addView(title)
  root.addView(label("Mostra, explica e partilha. Tudo num só link.",15f,muted).apply {setPadding(0,0,0,dp(12))})
  val card=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;background=rounded(cardColor)
   setPadding(dp(20),dp(20),dp(20),dp(20))
  }
  root.addView(card)
  card.addView(label("ESTADO DA TRANSMISSÃO",11f,muted).apply {setPadding(0,0,0,dp(10))})
  status=label("●  Pronto para começar",17f,accent).apply {setPadding(0,0,0,dp(18))}
  card.addView(status)
  startButton=Button(this).apply {
   text="▶  Iniciar transmissão";textSize=16f;isAllCaps=false
   setTextColor(Color.WHITE);background=rounded(accent,14)
   setOnClickListener {begin()}
  }
  captureMode=RadioGroup(this).apply{
   orientation=RadioGroup.VERTICAL
   addView(RadioButton(this@MainActivity).apply{id=2001;text="Ecrã completo";isChecked=true;setTextColor(Color.WHITE)})
   addView(RadioButton(this@MainActivity).apply{id=2002;text="Uma aplicação";setTextColor(Color.WHITE)})
  }
  card.addView(label("PARTILHAR",12f,muted).apply{setPadding(0,0,0,dp(6))})
  card.addView(captureMode)
  for(i in 0 until captureMode.childCount){(captureMode.getChildAt(i) as? RadioButton)?.buttonTintList=android.content.res.ColorStateList.valueOf(accent)}
  if(Build.VERSION.SDK_INT<34){
   captureMode.findViewById<RadioButton>(2002).isEnabled=false
   card.addView(label("A seleção de uma única aplicação requer Android 14 ou superior.",12f,muted))
  }
  card.addView(startButton,LinearLayout.LayoutParams(-1,dp(58)))
  saveLiveCheck=CheckBox(this).apply{text="Guardar gravação MP4";isChecked=false;setTextColor(Color.WHITE)}
  card.addView(saveLiveCheck)
  saveLiveCheck.visibility=android.view.View.GONE
  micCheck=CheckBox(this).apply{text="Microfone";isChecked=false;setTextColor(Color.WHITE)}
  micCheck.setOnCheckedChangeListener { _,checked ->
   if(checked && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),microphoneRequestCode)}else updateLiveControls()
  }
  micCheck.setTextColor(Color.WHITE)
  micCheck.buttonTintList=android.content.res.ColorStateList.valueOf(accent)
  root.addView(micCheck)
  val cameraHero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded(cardColor);setPadding(dp(14),dp(12),dp(14),dp(14))}
  root.addView(cameraHero)
  cameraHero.addView(label("A tua imagem",19f,accent).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  cameraHero.addView(label("Câmara opcional · podes alterar durante a transmissão.",12f,muted))
  cameraMode=RadioGroup(this).apply{
   orientation=RadioGroup.VERTICAL
   addView(RadioButton(this@MainActivity).apply{id=3000;text="Desligada";isChecked=true;setTextColor(Color.WHITE)})
   addView(RadioButton(this@MainActivity).apply{id=3001;text="Câmara frontal";setTextColor(Color.WHITE)})
   addView(RadioButton(this@MainActivity).apply{id=3002;text="Câmara traseira";setTextColor(Color.WHITE)})
  }
  cameraHero.addView(cameraMode)
  for(i in 0 until cameraMode.childCount){(cameraMode.getChildAt(i) as? RadioButton)?.buttonTintList=android.content.res.ColorStateList.valueOf(accent)}
  previewLabel=label("Pré-visualização da câmara (apenas nesta página)",12f,muted)
  cameraHero.addView(previewLabel)

  cameraPreview=CameraPreview(this).apply{visibility=android.view.View.GONE}
  cameraHero.addView(cameraPreview,LinearLayout.LayoutParams(-1,dp(180)))
  cameraHero.addView(label("Posição da câmara (opcional)",12f,muted))
  placement=CameraPlacementView(this)
  cameraHero.addView(placement,LinearLayout.LayoutParams(-1,dp(130)))
  placement.visibility=android.view.View.GONE
  placement.onPlacementChanged={updateLiveControls()}
  cameraHero.addView(label("TAMANHO DA CÂMARA",12f,muted))
  cameraSizeSeek=SeekBar(this).apply{max=100;progress=33;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(seekBar:SeekBar?,progress:Int,fromUser:Boolean){placement.sizeFraction=.14f+.36f*progress/100f;placement.invalidate();if(fromUser)updateLiveControls()}
   override fun onStartTrackingTouch(seekBar:SeekBar?){}
   override fun onStopTrackingTouch(seekBar:SeekBar?){}
  })}
  cameraHero.addView(cameraSizeSeek)
  cameraSizeSeek.visibility=android.view.View.GONE
  cameraMode.setOnCheckedChangeListener { _,selected ->
   if(live){
    if(selected!=3000 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),cameraRequestCode)}else updateLiveControls()
    return@setOnCheckedChangeListener
   }
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
  root.addView(label("ENVIAR LINK",12f,muted).apply {setPadding(0,dp(28),0,dp(12))})
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
  recordCard.visibility=android.view.View.GONE
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
  contentCard.visibility=android.view.View.GONE
  contentCard.addView(label("Vê isto 👀",21f).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
  contentCard.addView(label("Partilha o link original de um vídeo, canal ou página. O destinatário abre-o na aplicação ou site oficial.",13f,muted).apply{setPadding(0,dp(8),0,dp(10))})
  contentInput=EditText(this).apply{
   hint="Colar link do conteúdo";textSize=15f;setSingleLine(true)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
   setTextColor(Color.WHITE);setHintTextColor(muted)
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
  hint.visibility=android.view.View.GONE
  hint.addView(label("COMO FUNCIONA",12f,accent).apply {setPadding(0,0,0,dp(10))})
  hint.addView(label("1  Escolhe partilhar o ecrã ou um link.\\n2  Autoriza a captura, se necessário.\\n3  Envia o convite.\\n4  Continua a utilizar o telemóvel.",14f))
  if(sharedText.isNotBlank())hint.addView(label("\\nRecebemos um conteúdo de outra aplicação. Podes iniciar a partilha agora.",13f,muted))
  root.addView(label("Pode partilhar o ecrã completo ou uma aplicação compatível. Vídeos protegidos podem surgir a preto. O microfone transmite voz quando ativado; a gravação MP4 ainda não inclui áudio.",12f,muted).apply {setPadding(0,dp(22),0,dp(8))})
  val overlayOption=Button(this).apply{text="Controlos sobre outras apps: "+if(overlayPrefs.getBoolean("allowOverlay",false))"SIM" else "NÃO";setTextColor(Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(48,56,77));isAllCaps=false;setOnClickListener{
   AlertDialog.Builder(this@MainActivity).setTitle("Sobrepor a todas as aplicações?")
    .setPositiveButton("Sim"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",true).apply();text="Controlos sobre outras apps: SIM";requestOverlayPermission();if(live && Settings.canDrawOverlays(this@MainActivity))startService(Intent(this@MainActivity,CaptureService::class.java).apply{action="CONTROL";putExtra("overlayEnabled",true);putExtra("showOverlay",true)})}
    .setNegativeButton("Não"){_,_->overlayPrefs.edit().putBoolean("allowOverlay",false).apply();text="Controlos sobre outras apps: NÃO";if(live)startService(Intent(this@MainActivity,CaptureService::class.java).apply{action="CONTROL";putExtra("overlayEnabled",false);putExtra("hideOverlay",true)})}.show()
  }}
  root.addView(overlayOption)
  root.addView(label("Durante a transmissão, arraste a janela para mover a imagem. Use + / − para ajustar, o microfone para falar e TERMINAR para parar.",13f,muted))
  setContentView(scroll)
  // Do not interrupt screen sharing with a permissions dialog at app launch.
 }

 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==cameraRequestCode){
   if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED){
    if(cameraMode.checkedRadioButtonId!=3000){if(live)updateLiveControls() else{cameraPreview.visibility=android.view.View.VISIBLE;cameraPreview.showCamera(cameraMode.checkedRadioButtonId==3001)}}
   }else{cameraMode.check(3000);Toast.makeText(this,"Câmara desativada",Toast.LENGTH_SHORT).show()}
   if(pendingStart)beginWithoutOverlay()
   return
  }
  if(requestCode==microphoneRequestCode){
   if(grantResults.firstOrNull()!=PackageManager.PERMISSION_GRANTED){micCheck.isChecked=false;Toast.makeText(this,"Microfone desativado",Toast.LENGTH_SHORT).show()}
   if(pendingStart)beginWithoutOverlay()
  }
 }
 override fun onDestroy(){cameraPreview.release();try{unregisterReceiver(connectionReceiver)}catch(_:Exception){};super.onDestroy()}
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  super.onActivityResult(requestCode, resultCode, data)
  if(requestCode==overlayRequestCode){
   Toast.makeText(this,if(Settings.canDrawOverlays(this))"Janela do apresentador autorizada" else "Sobreposição não autorizada",Toast.LENGTH_SHORT).show()
   if(awaitingOverlay && pendingStart){awaitingOverlay=false;beginWithoutOverlay()}
   else if(live && Settings.canDrawOverlays(this) && overlayPrefs.getBoolean("allowOverlay",false)){startService(Intent(this,CaptureService::class.java).apply{action="CONTROL";putExtra("overlayEnabled",true);putExtra("showOverlay",true)})}
   return
  }
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
   live=true
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
    putExtra("overlayEnabled",overlayPrefs.getBoolean("allowOverlay",false) && Settings.canDrawOverlays(this))
   })
   status.text = if(saveLiveCheck.isChecked) "A transmitir e gravar · a estabelecer ligação" else "Captura iniciada · a estabelecer ligação"
   startButton.isEnabled = false
   saveLiveCheck.isEnabled=false
   micCheck.isEnabled=true
   for(i in 0 until cameraMode.childCount)cameraMode.getChildAt(i).isEnabled=true
   captureMode.isEnabled=false
   for(i in 0 until captureMode.childCount)captureMode.getChildAt(i).isEnabled=false
   recordButton.isEnabled=false
   stopRecordButton.isEnabled=false
   stopButton.isEnabled = true
   inviteAction.isEnabled=true;copyAction.isEnabled=true
   Toast.makeText(this,"Transmissão iniciada. Use Enviar convite para partilhar o link.",Toast.LENGTH_LONG).show()
  } else {
   session = ""
   publisherKey = ""
   status.text = "Partilha cancelada"
   startButton.isEnabled = true
   stopButton.isEnabled = false
  }
 }
}
