package art.miguelreis.livestream

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
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
 private val fps=5
 private val info=MediaCodec.BufferInfo()
 fun start():Boolean {
  try {
   val list=MediaCodecList(MediaCodecList.REGULAR_CODECS)
   val encoder=list.codecInfos.firstOrNull { c ->
    c.isEncoder && c.supportedTypes.any{it.equals("video/avc",true)} &&
    c.getCapabilitiesForType("video/avc").colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
   } ?: return false
   val format=MediaFormat.createVideoFormat("video/avc",width,height).apply {
    setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
    setInteger(MediaFormat.KEY_BIT_RATE,2500000)
    setInteger(MediaFormat.KEY_FRAME_RATE,fps)
    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)
   }
   codec=MediaCodec.createByCodecName(encoder.name).apply {configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);start()}
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
 fun addFrame(bitmap:Bitmap){
  val c=codec ?: return
  try {
   val index=c.dequeueInputBuffer(0)
   if(index>=0){
    val buffer=c.getInputBuffer(index) ?: return
    buffer.clear()
    val pixels=IntArray(width*height)
    bitmap.getPixels(pixels,0,width,0,0,width,height)
    val ySize=width*height
    if(buffer.capacity()<ySize*3/2){c.queueInputBuffer(index,0,0,0,0);return}
    for(row in 0 until height){
     for(col in 0 until width){
      val rgb=pixels[row*width+col]
      val r=(rgb shr 16) and 255;val g=(rgb shr 8) and 255;val b=rgb and 255
      val y=((66*r+129*g+25*b+128) shr 8)+16
      buffer.put(y.coerceIn(0,255).toByte())
     }
    }
    for(row in 0 until height step 2){
     for(col in 0 until width step 2){
      var rs=0;var gs=0;var bs=0
      for(dy in 0..1)for(dx in 0..1){
       val rgb=pixels[(row+dy)*width+col+dx]
       rs+=(rgb shr 16) and 255;gs+=(rgb shr 8) and 255;bs+=rgb and 255
      }
      val r=rs/4;val g=gs/4;val b=bs/4
      val u=(((-38*r-74*g+112*b+128) shr 8)+128).coerceIn(0,255)
      val v=(((112*r-94*g-18*b+128) shr 8)+128).coerceIn(0,255)
      buffer.put(u.toByte());buffer.put(v.toByte())
     }
    }
    val pts=(SystemClock.elapsedRealtime()-startedAt)*1000L
    c.queueInputBuffer(index,0,ySize*3/2,pts,0)
    frames++
   }
   drain(false)
  }catch(_:Exception){}
 }
 private fun drain(end:Boolean){
  val c=codec ?: return
  if(end){
   val index=c.dequeueInputBuffer(100000)
   if(index>=0)c.queueInputBuffer(index,0,0,(SystemClock.elapsedRealtime()-startedAt)*1000L,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
  }
  repeat(if(end)80 else 8){
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
    if(eos)return
   }else if(!end)return
  }
 }
 fun finish(save:Boolean=true){
  try{if(save && frames>0)drain(true)}catch(_:Exception){}
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
