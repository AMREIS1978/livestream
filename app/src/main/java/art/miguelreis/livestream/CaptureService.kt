package art.miguelreis.livestream
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
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
 private var reader:ImageReader?=null
 private var display:android.hardware.display.VirtualDisplay?=null
 private val worker=Executors.newSingleThreadExecutor()
 private val busy=AtomicBoolean(false)
 private var endpoint="";private var session="";private var publisherKey=""
 private var lastFrameAt=0L
 private var captureThread:HandlerThread?=null
 private var overlayView:LinearLayout?=null
 private var sharedText=""
 private var overlayManager:WindowManager?=null
 private var failures=0
 private var lastHttpStatus=0
 private var lastError=""
 private var liveLabel:TextView?=null
 private val mainHandler=Handler(Looper.getMainLooper())
 private fun reportState(ok:Boolean) {
  mainHandler.post {
   liveLabel?.text=if(ok) "● EM DIRETO" else "● A LIGAR…"
   val i=Intent("art.miguelreis.livestream.STATUS").setPackage(packageName).putExtra("connected",ok).putExtra("http",lastHttpStatus).putExtra("error",lastError)
   sendBroadcast(i)
  }
 }
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  if(projection!=null)return START_NOT_STICKY
  endpoint=intent?.getStringExtra("endpoint")?:"";session=intent?.getStringExtra("session")?:"";publisherKey=intent?.getStringExtra("publisherKey")?:"";sharedText=intent?.getStringExtra("sharedText")?:""
  val channel=NotificationChannel("capture","Partilha de ecrã",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  val stop=PendingIntent.getService(this,0,Intent(this,CaptureService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"capture").setContentTitle("LiveStream em direto").setContentText("O ecrã está a ser partilhado").setSmallIcon(android.R.drawable.ic_menu_share).addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
  startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  if(Settings.canDrawOverlays(this)) {
   try {
    overlayManager=getSystemService(WINDOW_SERVICE) as WindowManager
    val panel=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;setBackgroundColor(Color.rgb(160,22,38));setPadding(8,4,8,4) }
    val live=TextView(this).apply { text="● EM DIRETO";textSize=12f;setTextColor(Color.WHITE);setPadding(10,10,10,10) }
    val send=Button(this).apply { text="PARTILHAR";textSize=11f;setOnClickListener {
     val link="$endpoint/watch/$session"
     val msg="Vê isto 👀 Estou a partilhar o meu ecrã em direto: $link"+if(sharedText.isNotBlank()) "\\nConteúdo: $sharedText" else ""
     val share=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,msg);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
     try {startActivity(Intent.createChooser(share,"Enviar link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}catch(_:Exception){}
    } }
    liveLabel=live
    panel.addView(live);panel.addView(send)
    val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.END;x=8;y=80 }
    overlayManager?.addView(panel,params);overlayView=panel
   }catch(_:Exception) { overlayView=null }
  }
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("resultData",Intent::class.java) else intent?.getParcelableExtra("resultData")
  if(data==null){stopSelf();return START_NOT_STICKY}
  projection=mgr.getMediaProjection((intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED),data)
  val handler=Handler(Looper.getMainLooper())
  captureThread=HandlerThread("LiveStreamCapture",android.os.Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
  val captureHandler=Handler(captureThread!!.looper)
  projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
  val metrics=resources.displayMetrics
  val scale=(720f / metrics.widthPixels.coerceAtLeast(metrics.heightPixels)).coerceAtMost(1f)
  val w=(metrics.widthPixels*scale).toInt().coerceAtLeast(2);val h=(metrics.heightPixels*scale).toInt().coerceAtLeast(2)
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  reader?.setOnImageAvailableListener({ r ->
   val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
   val now=android.os.SystemClock.elapsedRealtime()
   if(now-lastFrameAt<350 || !busy.compareAndSet(false,true)){image.close();return@setOnImageAvailableListener}
   lastFrameAt=now
   try {
    val plane=image.planes[0];val pixelStride=plane.pixelStride;val rowStride=plane.rowStride
    val bmp=Bitmap.createBitmap(rowStride/pixelStride,h,Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(plane.buffer)
    val cropped=Bitmap.createBitmap(bmp,0,0,w,h)
    val out=ByteArrayOutputStream();cropped.compress(Bitmap.CompressFormat.JPEG,48,out)
    val bytes=out.toByteArray();bmp.recycle();cropped.recycle()
    if(bytes.size<100 || bytes.size>690000){busy.set(false);image.close();return@setOnImageAvailableListener}
    worker.execute { try {
     var delivered=false
     for(attempt in 0..1) {
      var conn:HttpURLConnection?=null
      try {
       conn=URL("$endpoint/api/frame/$session").openConnection() as HttpURLConnection
       conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=3500;conn.readTimeout=3500
       conn.setRequestProperty("Content-Type","image/jpeg")
       conn.setRequestProperty("X-Publish-Key",publisherKey)
       conn.outputStream.use{it.write(bytes)}
       val code=conn.responseCode
       lastHttpStatus=code
       if(code in 200..299) {delivered=true;lastError="";break}
       lastError="HTTP $code"
       if(code in 400..499 && code!=429) break
      }catch(e:Exception){lastError=e.javaClass.simpleName} finally {conn?.disconnect()}
      if(attempt==0) Thread.sleep(180)
     }
     failures=if(delivered) 0 else (failures+1).coerceAtMost(100)
     reportState(delivered)
    } catch(_:InterruptedException){Thread.currentThread().interrupt()} finally {busy.set(false)} }
   } catch(_:Exception){busy.set(false)} finally{image.close()}
  },captureHandler)
  display=projection?.createVirtualDisplay("LiveStream",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
  return START_NOT_STICKY
 }
 override fun onDestroy(){liveLabel=null;overlayView?.let { try {overlayManager?.removeView(it)}catch(_:Exception){} };overlayView=null;reader?.setOnImageAvailableListener(null,null);display?.release();reader?.close();projection?.stop();captureThread?.quitSafely();worker.shutdownNow();super.onDestroy()}
}