package art.miguelreis.livestream

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteOrder
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import android.os.SystemClock
import java.nio.ByteBuffer

/** Encodes the same captured frames used for live sharing. No second MediaProjection. */
class SharedFrameRecorder(private val context:Context,private val width:Int,private val height:Int) {
 private var codec:MediaCodec?=null
 private var muxer:MediaMuxer?=null
 private var uri:android.net.Uri?=null
 private var fd:android.os.ParcelFileDescriptor?=null
 private var track=-1
 private var muxerStarted=false
 private var startedAt=0L
 private var frames=0L
 private val fps=10
 private var inputSurface:Surface?=null
 private var eglDisplay=EGL14.EGL_NO_DISPLAY
 private var eglContext=EGL14.EGL_NO_CONTEXT
 private var eglSurface=EGL14.EGL_NO_SURFACE
 private var program=0
 private var texture=0
 private val info=MediaCodec.BufferInfo()
 fun start():Boolean {
  try {
   val list=MediaCodecList(MediaCodecList.REGULAR_CODECS)
   val encoder=list.codecInfos.firstOrNull { c ->
    c.isEncoder && c.supportedTypes.any{it.equals("video/avc",true)} &&
    c.getCapabilitiesForType("video/avc").colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
   } ?: return false
   val format=MediaFormat.createVideoFormat("video/avc",width,height).apply {
    setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    setInteger(MediaFormat.KEY_BIT_RATE,2500000)
    setInteger(MediaFormat.KEY_FRAME_RATE,fps)
    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)
   }
   codec=MediaCodec.createByCodecName(encoder.name).apply {configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);inputSurface=createInputSurface();start()}
   initGl()
   val values=ContentValues().apply {
    put(MediaStore.Video.Media.DISPLAY_NAME,"LiveStream_"+System.currentTimeMillis()+".mp4")
    put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
    put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/LiveStream")
    put(MediaStore.Video.Media.IS_PENDING,1)
   }
   uri=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)
   fd=uri?.let{context.contentResolver.openFileDescriptor(it,"w")}
   muxer=MediaMuxer(fd?.fileDescriptor ?: throw IllegalStateException("No output"),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   startedAt=SystemClock.elapsedRealtime()
   return true
  }catch(_:Exception){finish(false);return false}
 }
 private fun initGl(){
  val d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
  if(d==EGL14.EGL_NO_DISPLAY)throw IllegalStateException("EGL display")
  eglDisplay=d
  if(!EGL14.eglInitialize(d,IntArray(2),0,IntArray(2),0))throw IllegalStateException("EGL init")
  val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
  val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
  if(!EGL14.eglChooseConfig(d,attrs,0,configs,0,1,count,0)||count[0]==0)throw IllegalStateException("EGL config")
  eglContext=EGL14.eglCreateContext(d,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
  eglSurface=EGL14.eglCreateWindowSurface(d,configs[0],inputSurface,intArrayOf(EGL14.EGL_NONE),0)
  if(!EGL14.eglMakeCurrent(d,eglSurface,eglSurface,eglContext))throw IllegalStateException("EGL current")
  val vertex="attribute vec4 aPosition;attribute vec2 aTex;varying vec2 vTex;void main(){gl_Position=aPosition;vTex=aTex;}"
  val fragment="precision mediump float;varying vec2 vTex;uniform sampler2D uTex;void main(){gl_FragColor=texture2D(uTex,vTex);}"
  fun shader(type:Int,source:String):Int{
   val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
   val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
   if(status[0]==0)throw IllegalStateException("Shader compile")
   return id
  }
  val vs=shader(GLES20.GL_VERTEX_SHADER,vertex);val fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
  program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program)
  GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
  val linked=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0)
  if(linked[0]==0)throw IllegalStateException("Shader link")
  val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);texture=ids[0]
  GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
  GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
  GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
  GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
  GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
 }
 fun addFrame(bitmap:Bitmap){
  if(codec==null || eglDisplay==EGL14.EGL_NO_DISPLAY)return
  try{
   EGL14.eglMakeCurrent(eglDisplay,eglSurface,eglSurface,eglContext)
   GLES20.glViewport(0,0,width,height)
   GLES20.glUseProgram(program)
   GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
   GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
   GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
   val vertices=floatArrayOf(-1f,-1f,0f,1f,1f,-1f,1f,1f,-1f,1f,0f,0f,1f,1f,1f,0f)
   val indices=shortArrayOf(0,1,2,1,3,2)
   val data=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(vertices);position(0)}
   val elements=ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply{put(indices);position(0)}
   val pos=GLES20.glGetAttribLocation(program,"aPosition")
   val tex=GLES20.glGetAttribLocation(program,"aTex")
   GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(tex)
   data.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,data)
   data.position(2);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,16,data)
   GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTex"),0)
   GLES20.glDrawElements(GLES20.GL_TRIANGLES,6,GLES20.GL_UNSIGNED_SHORT,elements)
   GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(tex)
   EGLExt.eglPresentationTimeANDROID(eglDisplay,eglSurface,System.nanoTime())
   EGL14.eglSwapBuffers(eglDisplay,eglSurface)
   frames++
   drain(false)
  }catch(_:Exception){}
 }
 private fun drain(end:Boolean){
  val c=codec ?: return
  if(end){
   c.signalEndOfInputStream()
  }
  for(attempt in 0 until (if(end)80 else 8)){
   val index=c.dequeueOutputBuffer(info,if(end)50000 else 0)
   if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
    if(!muxerStarted){track=muxer!!.addTrack(c.outputFormat);muxer!!.start();muxerStarted=true}
   }else if(index>=0){
    if(info.size>0 && muxerStarted && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
     val data=c.getOutputBuffer(index)!!
     data.position(info.offset);data.limit(info.offset+info.size)
     muxer?.writeSampleData(track,data,info)
    }
    val eos=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
    c.releaseOutputBuffer(index,false)
    if(eos)break
   }else if(!end)break
  }
 }
 fun finish(save:Boolean=true){
  try{if(save && frames>0)drain(true)}catch(_:Exception){}
  try{if(eglDisplay!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(eglDisplay,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(eglDisplay,eglSurface);if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(eglDisplay,eglContext);EGL14.eglTerminate(eglDisplay)}}catch(_:Exception){}
  try{inputSurface?.release()}catch(_:Exception){}
  try{codec?.stop()}catch(_:Exception){}
  try{codec?.release()}catch(_:Exception){}
  codec=null
  try{if(muxerStarted)muxer?.stop()}catch(_:Exception){}
  try{muxer?.release()}catch(_:Exception){}
  muxer=null
  try{fd?.close()}catch(_:Exception){}
  val good=save && frames>0 && muxerStarted
  uri?.let{u->try{
   if(good)context.contentResolver.update(u,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)
   else context.contentResolver.delete(u,null,null)
  }catch(_:Exception){}}
 }
}
