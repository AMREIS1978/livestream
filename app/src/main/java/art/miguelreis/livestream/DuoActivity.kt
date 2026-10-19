package art.miguelreis.livestream

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*

/** Foreground-only camera mode. Screen sharing remains in MainActivity. */
class DuoActivity : Activity() {
 private lateinit var web: WebView
 private val origin="https://reis-live-duo.a-miguel-reis.chatgpt.site"
 private var mediaRequest: PermissionRequest?=null
 private var geoCallback: GeolocationPermissions.Callback?=null
 private var geoOrigin: String?=null
 override fun onCreate(state: Bundle?) {
  super.onCreate(state)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  web=WebView(this)
  web.settings.javaScriptEnabled=true
  web.settings.domStorageEnabled=true
  web.settings.mediaPlaybackRequiresUserGesture=false
  web.settings.setGeolocationEnabled(true)
  web.settings.allowFileAccess=false
  web.settings.allowContentAccess=false
  web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
  web.addJavascriptInterface(object {
   @JavascriptInterface fun send(text:String) {runOnUiThread {
    val send=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,text.take(4000))}
    startActivity(Intent.createChooser(send,"Enviar link por WhatsApp"))
   }}
  },"AndroidShare")
  web.webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
    if(!request.isForMainFrame)return false
    val u=request.url
    if(u.scheme=="https" && u.host==Uri.parse(origin).host)return false
    if(u.scheme=="https" || u.scheme=="http"){startActivity(Intent(Intent.ACTION_VIEW,u))}
    return true
   }
  }
  web.webChromeClient=object:WebChromeClient(){
   override fun onPermissionRequest(request:PermissionRequest){runOnUiThread {
    if(request.origin.toString().trimEnd('/')!=origin){request.deny();return@runOnUiThread}
    mediaRequest?.deny();mediaRequest=request
    val needed=mutableListOf<String>()
    if(request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE))needed.add(Manifest.permission.CAMERA)
    if(request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE))needed.add(Manifest.permission.RECORD_AUDIO)
    val missing=needed.filter{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
    if(missing.isEmpty())grantMedia() else requestPermissions(missing.toTypedArray(),401)
   }}
   override fun onPermissionRequestCanceled(request:PermissionRequest){if(mediaRequest===request)mediaRequest=null}
   override fun onGeolocationPermissionsShowPrompt(o:String,callback:GeolocationPermissions.Callback){
    if(o.trimEnd('/')!=origin){callback.invoke(o,false,false);return}
    geoOrigin=o;geoCallback=callback
    if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(o,true,false);geoCallback=null}
    else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),402)
   }
  }
  setContentView(web)
  web.loadUrl(origin)
 }
 private fun grantMedia(){
  val r=mediaRequest?:return
  val granted=r.resources.filter{(it==PermissionRequest.RESOURCE_VIDEO_CAPTURE && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)||(it==PermissionRequest.RESOURCE_AUDIO_CAPTURE && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
  if(granted.isEmpty())r.deny() else r.grant(granted.toTypedArray())
  mediaRequest=null
 }
 override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){
  super.onRequestPermissionsResult(code,permissions,results)
  if(code==401)grantMedia()
  if(code==402){geoCallback?.invoke(geoOrigin,checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED,false);geoCallback=null}
 }
 override fun onDestroy(){mediaRequest?.deny();geoCallback?.invoke(geoOrigin,false,false);web.loadUrl("about:blank");web.destroy();super.onDestroy()}
}
