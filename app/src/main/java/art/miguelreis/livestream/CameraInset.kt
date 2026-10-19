package art.miguelreis.livestream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.util.concurrent.atomic.AtomicReference

/** Optional in-stream picture-in-picture camera; no Android system overlay permission. */
class CameraInset(private val context:Context, private val front:Boolean) {
 private val latest=AtomicReference<Bitmap?>()
 private val frameLock=Any()
 private val thread=HandlerThread("LiveStreamCamera")
 private var handler:Handler?=null
 private var camera:CameraDevice?=null
 private var session:CameraCaptureSession?=null
 private var reader:ImageReader?=null
 @Volatile private var closed=false
 @Volatile var positionX=0.78f
 @Volatile var positionY=0.14f
 @Volatile var sizeFraction=0.26f
 private var sensorOrientation=0

 fun start():Boolean {
  if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return false
  try {
   val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
   val facing=if(front)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
   val id=manager.cameraIdList.firstOrNull {
    manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==facing
   }?:return false
   sensorOrientation=manager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION)?:0
   thread.start()
   handler=Handler(thread.looper)
   reader=ImageReader.newInstance(320,240,ImageFormat.JPEG,2).apply {
    setOnImageAvailableListener({ r ->
     val image=try{r.acquireLatestImage()}catch(_:Exception){null}
     if(image!=null)try {
      val buffer=image.planes[0].buffer
      val data=ByteArray(buffer.remaining());buffer.get(data)
      val bitmap=BitmapFactory.decodeByteArray(data,0,data.size)
      if(bitmap!=null){
       val rotation=Matrix().apply{postRotate(sensorOrientation.toFloat());if(front)postScale(-1f,1f)}
       val oriented=try{Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,rotation,true)}catch(_:Exception){bitmap}
       if(oriented!==bitmap)bitmap.recycle()
       synchronized(frameLock){latest.getAndSet(oriented)?.recycle()}
      }
     }catch(_:Exception){}finally{image.close()}
    },handler)
   }
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(device:CameraDevice){
     if(closed){device.close();return}
     camera=device
     val surface=reader?.surface?:return
     try{
      device.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
       override fun onConfigured(captureSession:CameraCaptureSession){
        if(closed){captureSession.close();return}
        session=captureSession
        try{
         val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(surface)}.build()
         captureSession.setRepeatingRequest(request,null,handler)
        }catch(_:Exception){}
       }
       override fun onConfigureFailed(captureSession:CameraCaptureSession){captureSession.close()}
      },handler)
     }catch(_:Exception){}
    }
    override fun onDisconnected(device:CameraDevice){device.close();camera=null}
    override fun onError(device:CameraDevice,error:Int){device.close();camera=null}
   },handler)
   return true
  }catch(_:Exception){stop();return false}
 }

 fun drawPreview(canvas:Canvas,width:Int,height:Int){
  synchronized(frameLock){val frame=latest.get()?:return;try{if(!frame.isRecycled)canvas.drawBitmap(frame,null,Rect(0,0,width,height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}catch(_:Exception){}}
 }
 fun drawOn(target:Bitmap) {
  synchronized(frameLock){
  val inset=latest.get()?:return
  if(inset.isRecycled)return
  try {
   val canvas=Canvas(target)
   // Overlay with preserved screen geometry; camera is rotated at capture time.
   val width=(target.width*sizeFraction.coerceIn(.14f,.50f)).toInt().coerceAtLeast(64)
   val height=(width*inset.height.toFloat()/inset.width).toInt().coerceAtLeast(64).coerceAtMost(target.height/2)
   val left=((target.width-width)*positionX.coerceIn(0f,1f)).toInt()
   val top=((target.height-height)*positionY.coerceIn(0f,1f)).toInt()
   val dst=Rect(left,top,left+width,top+height)
   val border=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE}
   canvas.drawRoundRect(left-3f,top-3f,(left+width+3).toFloat(),(top+height+3).toFloat(),10f,10f,border)
   canvas.drawBitmap(inset,null,dst,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
  }catch(_:Exception){}
  }
 }

 fun stop(){
  closed=true
  try{session?.close()}catch(_:Exception){}
  try{camera?.close()}catch(_:Exception){}
  try{reader?.close()}catch(_:Exception){}
  thread.quitSafely()
  synchronized(frameLock){latest.getAndSet(null)?.recycle()}
 }
}
