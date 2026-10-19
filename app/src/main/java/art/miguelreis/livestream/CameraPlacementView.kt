package art.miguelreis.livestream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Drag the camera rectangle to choose its broadcast position. */
class CameraPlacementView(context:Context):View(context){
 var xFraction=.78f
 var yFraction=.14f
 var sizeFraction=.26f
 var onPlacementChanged:(()->Unit)?=null
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 init{setBackgroundColor(Color.rgb(237,243,251));minimumHeight=(180*resources.displayMetrics.density).toInt()}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas)
  val w=width.toFloat();val h=height.toFloat()
  paint.color=Color.WHITE;canvas.drawRoundRect(8f,8f,w-8,h-8,12f,12f,paint)
  paint.color=Color.rgb(80,99,120);paint.textSize=15*resources.displayMetrics.scaledDensity
  canvas.drawText("Ecrã partilhado",20f,32f,paint)
  paint.color=Color.rgb(219,229,240)
  for(i in 1..5)canvas.drawRect(20f,40f+i*19f,w-24f,47f+i*19f,paint)
  val rw=w*sizeFraction
  val rh=(rw*1.28f).coerceAtMost(h*.60f)
  val left=(w-rw)*xFraction;val top=(h-rh)*yFraction
  paint.color=Color.rgb(24,119,242);canvas.drawRoundRect(left,top,left+rw,top+rh,13f,13f,paint)
  paint.color=Color.WHITE;paint.textSize=12*resources.displayMetrics.scaledDensity
  canvas.drawText("Câmara",left+9f,top+25f,paint)
 }
 override fun onTouchEvent(event:MotionEvent):Boolean{
  when(event.actionMasked){
   MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE->{
    val rw=width*sizeFraction;val rh=(rw*1.28f).coerceAtMost(height*.60f)
    xFraction=((event.x-rw/2)/(width-rw)).coerceIn(0f,1f)
    yFraction=((event.y-rh/2)/(height-rh)).coerceIn(0f,1f)
    invalidate();onPlacementChanged?.invoke();return true
   }
   MotionEvent.ACTION_UP->{performClick();return true}
  }
  return true
 }
 override fun performClick():Boolean{super.performClick();return true}
}
