package art.miguelreis.livestream

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.content.ContentValues
import android.net.Uri
import java.io.FileDescriptor

class RecordService: Service() {
 private var projection:MediaProjection?=null
 private var virtualDisplay:android.hardware.display.VirtualDisplay?=null
 private var recorder:MediaRecorder?=null
 private var output:Uri?=null
 private var file:android.os.ParcelFileDescriptor?=null
 private var started=false
 private var stopping=false
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  if(started || projection!=null)return START_NOT_STICKY
  val channel=NotificationChannel("record","Gravação do ecrã",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
  val stop=PendingIntent.getService(this,5,Intent(this,RecordService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val notification=Notification.Builder(this,"record").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("LiveStream · a gravar ecrã").setContentText("Toque em Parar para guardar o vídeo").addAction(Notification.Action.Builder(null,"Parar e guardar",stop).build()).build()
  startForeground(6,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("resultData",Intent::class.java) else intent?.getParcelableExtra("resultData")
  if(data==null){stopSelf();return START_NOT_STICKY}
  try {
   val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection=manager.getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
   projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},Handler(Looper.getMainLooper()))
   val metrics=resources.displayMetrics
   val factor=(1920f/metrics.widthPixels.coerceAtLeast(metrics.heightPixels)).coerceAtMost(1f)
   val width=(((metrics.widthPixels*factor).toInt().coerceAtLeast(2))/2)*2
   val height=(((metrics.heightPixels*factor).toInt().coerceAtLeast(2))/2)*2
   val values=ContentValues().apply {
    put(MediaStore.Video.Media.DISPLAY_NAME,"LiveStream_"+System.currentTimeMillis()+".mp4")
    put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
    put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/LiveStream")
    put(MediaStore.Video.Media.IS_PENDING,1)
   }
   output=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)
   file=output?.let{contentResolver.openFileDescriptor(it,"w")}
   val fd=file?.fileDescriptor ?: throw IllegalStateException("Não foi possível abrir o ficheiro")
   recorder=if(Build.VERSION.SDK_INT>=31)MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
   recorder!!.apply {
    setVideoSource(MediaRecorder.VideoSource.SURFACE)
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
    setVideoSize(width,height)
    setVideoFrameRate(30)
    setVideoEncodingBitRate(6000000)
    setOutputFile(fd)
    prepare()
   }
   virtualDisplay=projection?.createVirtualDisplay("LiveStreamRecording",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder!!.surface,null,null)
   recorder!!.start()
   started=true
  }catch(e:Exception){stopSelf()}
  return START_NOT_STICKY
 }
 override fun onDestroy(){
  if(stopping){super.onDestroy();return}
  stopping=true
  var valid=started
  if(started)try{recorder?.stop()}catch(_:Exception){valid=false}
  try{recorder?.reset();recorder?.release()}catch(_:Exception){}
  try{virtualDisplay?.release()}catch(_:Exception){}
  try{projection?.stop()}catch(_:Exception){}
  try{file?.close()}catch(_:Exception){}
  output?.let{uri->
   try{
    if(valid)contentResolver.update(uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)
    else contentResolver.delete(uri,null,null)
   }catch(_:Exception){}
  }
  super.onDestroy()
 }
}
