package art.miguelreis.livestream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView

/** Camera preview visible in the LiveStream app before screen sharing starts. */
class CameraPreview(context:Context):TextureView(context) {
 private val cameraThread=HandlerThread("LiveStreamPreview")
 private var handler:Handler?=null
 private var camera:CameraDevice?=null
 private var capture:CameraCaptureSession?=null
 private var facing=CameraCharacteristics.LENS_FACING_FRONT
 private var active=false

 init {
  surfaceTextureListener=object:SurfaceTextureListener {
   override fun onSurfaceTextureAvailable(surface:SurfaceTexture,width:Int,height:Int){if(active)open()}
   override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,width:Int,height:Int){}
   override fun onSurfaceTextureDestroyed(surface:SurfaceTexture):Boolean{stop();return true}
   override fun onSurfaceTextureUpdated(surface:SurfaceTexture){}
  }
 }
 fun showCamera(front:Boolean){
  stop()
  facing=if(front)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
  active=true
  if(isAvailable)open()
 }
 private fun open(){
  if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
  try{
   val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
   val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==facing}?:return
   if(!cameraThread.isAlive)cameraThread.start()
   handler=Handler(cameraThread.looper)
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(device:CameraDevice){
     if(!active){device.close();return}
     camera=device
     val texture=surfaceTexture?:return
     texture.setDefaultBufferSize(640,480)
     val surface=Surface(texture)
     try{
      device.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
       override fun onConfigured(session:CameraCaptureSession){
        if(!active){session.close();surface.release();return}
        capture=session
        try{
         val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(surface)}.build()
         session.setRepeatingRequest(request,null,handler)
        }catch(_:Exception){}
       }
       override fun onConfigureFailed(session:CameraCaptureSession){session.close();surface.release()}
      },handler)
     }catch(_:Exception){surface.release()}
    }
    override fun onDisconnected(device:CameraDevice){device.close();camera=null}
    override fun onError(device:CameraDevice,error:Int){device.close();camera=null}
   },handler)
  }catch(_:Exception){}
 }
 fun stop(){
  active=false
  try{capture?.close()}catch(_:Exception){}
  capture=null
  try{camera?.close()}catch(_:Exception){}
  camera=null
 }
 fun release(){stop();if(cameraThread.isAlive)cameraThread.quitSafely()}
}
