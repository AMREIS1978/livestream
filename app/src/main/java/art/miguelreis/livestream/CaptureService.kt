package art.miguelreis.livestream
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService: Service() {
 private var projection:MediaProjection?=null
 private var micEnabled=false
 private var cameraMode=0
 private var cameraInset:CameraInset?=null
 private var audioRecorder:AudioRecord?=null
 private var audioThread:Thread?=null
 @Volatile private var audioRunning=false
 private var reader:ImageReader?=null
 private var display:android.hardware.display.VirtualDisplay?=null
 private val worker=Executors.newSingleThreadExecutor()
 private val networkWorker=Executors.newSingleThreadExecutor()
 private val uploadBusy=AtomicBoolean(false)
 private val busy=AtomicBoolean(false)
 private var endpoint="";private var session="";private var publisherKey=""
 private var lastFrameAt=0L
 private var captureThread:HandlerThread?=null
  private var sharedText=""
 private var saveAlongside=false
 private var sharedRecorder:SharedFrameRecorder?=null
 private var failures=0
 private var jpegQuality=60
 private var intervalMs=180L
 private var lastUploadMs=0L
 private var lastHttpStatus=0
 private var lastError=""
 private val mainHandler=Handler(Looper.getMainLooper())
 private fun reportState(ok:Boolean) {
  mainHandler.post {
   val i=Intent("art.miguelreis.livestream.STATUS").setPackage(packageName).putExtra("connected",ok).putExtra("http",lastHttpStatus).putExtra("error",lastError)
   sendBroadcast(i)
  }
 }
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  if(projection!=null)return START_NOT_STICKY
  endpoint=intent?.getStringExtra("endpoint")?:"";session=intent?.getStringExtra("session")?:"";publisherKey=intent?.getStringExtra("publisherKey")?:"";sharedText=intent?.getStringExtra("sharedText")?:"";saveAlongside=intent?.getBooleanExtra("saveAlongside",false)==true;micEnabled=intent?.getBooleanExtra("micEnabled",false)==true && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  cameraMode=intent?.getIntExtra("cameraMode",0)?:0
  val channel=NotificationChannel("capture","Partilha de ecrã",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  val stop=PendingIntent.getService(this,0,Intent(this,CaptureService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"capture").setContentTitle("LiveStream em direto").setContentText("O ecrã está a ser partilhado").setSmallIcon(android.R.drawable.ic_menu_share).addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
  try{startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or (if(micEnabled)ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) or (if(cameraMode!=0)ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0))}catch(e:Exception){lastError="Permissões da transmissão: "+e.javaClass.simpleName;reportState(false);stopSelf();return START_NOT_STICKY}
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("resultData",Intent::class.java) else intent?.getParcelableExtra("resultData")
  if(data==null){stopSelf();return START_NOT_STICKY}
  try{projection=mgr.getMediaProjection((intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED),data)}catch(e:Exception){lastError="Captura não autorizada: "+e.javaClass.simpleName;reportState(false);stopSelf();return START_NOT_STICKY}
  val handler=Handler(Looper.getMainLooper())
  captureThread=HandlerThread("LiveStreamCapture",android.os.Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
  val captureHandler=Handler(captureThread!!.looper)
  projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
  val metrics=resources.displayMetrics
  val scale=(800f / metrics.widthPixels.coerceAtLeast(metrics.heightPixels)).coerceAtMost(1f)
  val w=((metrics.widthPixels*scale).toInt().coerceAtLeast(2)/2)*2;val h=((metrics.heightPixels*scale).toInt().coerceAtLeast(2)/2)*2
  if(saveAlongside)sharedRecorder=SharedFrameRecorder(this,w,h).also{if(!it.start()){sharedRecorder=null;lastError="Não foi possível iniciar a gravação"}}
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  reader?.setOnImageAvailableListener({ r ->
   val image=try{r.acquireLatestImage()}catch(_:Exception){null}?:return@setOnImageAvailableListener
   val now=SystemClock.elapsedRealtime()
   if(now-lastFrameAt<intervalMs || !busy.compareAndSet(false,true) || uploadBusy.get()){
    image.close()
    return@setOnImageAvailableListener
   }
   lastFrameAt=now
   try {
    worker.execute {
     try {
      val plane=image.planes[0]
      val rowWidth=plane.rowStride/plane.pixelStride
      val source=Bitmap.createBitmap(rowWidth,h,Bitmap.Config.ARGB_8888)
      try {
       source.copyPixelsFromBuffer(plane.buffer)
       val cropped=Bitmap.createBitmap(source,0,0,w,h)
       try {
        cameraInset?.drawOn(cropped)
        sharedRecorder?.addFrame(cropped)
        val out=ByteArrayOutputStream()
        var q=jpegQuality
        do {
         out.reset()
         cropped.compress(Bitmap.CompressFormat.JPEG,q,out)
         if(out.size()<=650000)break
         q-=10
        }while(q>=25)
        val bytes=out.toByteArray()
        if(bytes.size<100 || bytes.size>690000){
         lastError="Imagem demasiado grande";reportState(false)
        }else{
         if(uploadBusy.compareAndSet(false,true)){
          try{
           networkWorker.execute {
            try{
          var delivered=false
          val started=SystemClock.elapsedRealtime()
          for(attempt in 0..1){
           var conn:HttpURLConnection?=null
           try{
            conn=URL("$endpoint/api/frame/$session").openConnection() as HttpURLConnection
            conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=1800;conn.readTimeout=1800
            conn.setRequestProperty("Content-Type","image/jpeg")
            conn.setRequestProperty("X-Publish-Key",publisherKey)
            conn.outputStream.use{it.write(bytes)}
            val code=conn.responseCode
            lastHttpStatus=code
            if(code in 200..299){delivered=true;lastError="";break}
            lastError="HTTP $code"
            if(code in 400..499 && code!=429)break
           }catch(e:Exception){lastError=e.javaClass.simpleName}
           finally{conn?.disconnect()}
           if(attempt==0)Thread.sleep(80)
          }
          val duration=SystemClock.elapsedRealtime()-started
          lastUploadMs=duration
          failures=if(delivered)0 else (failures+1).coerceAtMost(100)
          if(delivered && duration<350){
           jpegQuality=(jpegQuality+2).coerceAtMost(78);intervalMs=170L
          }else if(delivered && duration<800){
           jpegQuality=jpegQuality.coerceAtMost(68);intervalMs=240L
          }else{
           jpegQuality=(jpegQuality-8).coerceAtLeast(30)
           intervalMs=if(failures>2)850L else 480L
          }
          reportState(delivered)
            }catch(_:Exception){lastError="Falha de envio";reportState(false)}
            finally{uploadBusy.set(false)}
           }
          }catch(_:java.util.concurrent.RejectedExecutionException){uploadBusy.set(false)}
         }

        }
       }finally{cropped.recycle()}
      }finally{source.recycle()}
     }catch(e:Exception){
      lastError=e.javaClass.simpleName
      reportState(false)
     }finally{
      image.close()
      busy.set(false)
     }
    }
   }catch(_:java.util.concurrent.RejectedExecutionException){
    image.close();busy.set(false)
   }
  },captureHandler)
  try{display=projection?.createVirtualDisplay("LiveStream",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)}catch(e:Exception){lastError="Não foi possível iniciar o ecrã: "+e.javaClass.simpleName;reportState(false);stopSelf();return START_NOT_STICKY}
  if(micEnabled)startMicrophone()
  if(cameraMode!=0){try{cameraInset=CameraInset(this,cameraMode==1).also{if(!it.start()){cameraInset=null;lastError="Câmara indisponível"}}}catch(_:Exception){cameraInset=null;lastError="Câmara não disponível"}}
  return START_NOT_STICKY
 }
  private fun startMicrophone(){
  try{
   val rate=16000
   val minimum=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
   if(minimum<=0)return
   val recorder=AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,8192))
   if(recorder.state!=AudioRecord.STATE_INITIALIZED){recorder.release();return}
   audioRecorder=recorder
   recorder.startRecording()
   audioRunning=true
   audioThread=Thread({
    val chunk=ByteArray(8192)
    while(audioRunning && !Thread.currentThread().isInterrupted){
     val count=try{recorder.read(chunk,0,chunk.size)}catch(_:Exception){break}
     if(count>0){
      var connection:HttpURLConnection?=null
      try{
       connection=URL("$endpoint/api/audio/$session").openConnection() as HttpURLConnection
       connection.requestMethod="POST";connection.doOutput=true
       connection.connectTimeout=900;connection.readTimeout=900
       connection.setRequestProperty("Content-Type","application/octet-stream")
       connection.setRequestProperty("X-Publish-Key",publisherKey)
       connection.outputStream.use{it.write(chunk,0,count)}
       connection.responseCode
      }catch(_:Exception){}finally{connection?.disconnect()}
     }
    }
   },"LiveStreamAudio").apply{isDaemon=true;start()}
  }catch(_:Exception){audioRunning=false}
 }
 override fun onDestroy(){if(lastError.isBlank())lastError="Transmissão terminada";reportState(false);cameraInset?.stop();cameraInset=null;audioRunning=false;try{audioRecorder?.stop()}catch(_:Exception){};try{audioRecorder?.release()}catch(_:Exception){};audioRecorder=null;audioThread?.interrupt();reader?.setOnImageAvailableListener(null,null);display?.release();reader?.close();projection?.stop();captureThread?.quitSafely();val recorderToFinish=sharedRecorder;sharedRecorder=null;try{worker.execute { try{recorderToFinish?.finish()}catch(_:Exception){} }}catch(_:java.util.concurrent.RejectedExecutionException){};worker.shutdown();networkWorker.shutdownNow();super.onDestroy()}
}