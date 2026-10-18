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
 private var endpoint="";private var session=""
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  if(projection!=null)return START_NOT_STICKY
  endpoint=intent?.getStringExtra("endpoint")?:"";session=intent?.getStringExtra("session")?:""
  val channel=NotificationChannel("capture","Partilha de ecrã",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  val stop=PendingIntent.getService(this,0,Intent(this,CaptureService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"capture").setContentTitle("LiveStream em direto").setContentText("O ecrã está a ser partilhado").setSmallIcon(android.R.drawable.ic_menu_share).addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
  startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("resultData",Intent::class.java) else intent?.getParcelableExtra("resultData")
  if(data==null){stopSelf();return START_NOT_STICKY}
  projection=mgr.getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
  val handler=Handler(Looper.getMainLooper())
  projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
  val metrics=resources.displayMetrics
  val w=(metrics.widthPixels/2).coerceAtLeast(320);val h=(metrics.heightPixels/2).coerceAtLeast(320)
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  reader?.setOnImageAvailableListener({ r ->
   val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
   if(!busy.compareAndSet(false,true)){image.close();return@setOnImageAvailableListener}
   try {
    val plane=image.planes[0];val pixelStride=plane.pixelStride;val rowStride=plane.rowStride
    val bmp=Bitmap.createBitmap(w+((rowStride-pixelStride*w)/pixelStride),h,Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(plane.buffer)
    val cropped=Bitmap.createBitmap(bmp,0,0,w,h)
    val out=ByteArrayOutputStream();cropped.compress(Bitmap.CompressFormat.JPEG,55,out)
    val bytes=out.toByteArray();bmp.recycle();cropped.recycle()
    worker.execute { try {
     val conn=URL("$endpoint/api/frame/$session").openConnection() as HttpURLConnection
     conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=5000;conn.readTimeout=5000
     conn.setRequestProperty("Content-Type","image/jpeg")
     conn.outputStream.use{it.write(bytes)}
     conn.inputStream.close();conn.disconnect()
    } catch(_:Exception){} finally {busy.set(false)} }
   } catch(_:Exception){busy.set(false)} finally{image.close()}
  },handler)
  display=projection?.createVirtualDisplay("LiveStream",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
  return START_NOT_STICKY
 }
 override fun onDestroy(){reader?.setOnImageAvailableListener(null,null);display?.release();reader?.close();projection?.stop();worker.shutdownNow();super.onDestroy()}
}