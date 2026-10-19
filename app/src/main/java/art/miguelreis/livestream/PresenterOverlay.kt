package art.miguelreis.livestream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Presenter control on top of other apps. Secure window prevents double capture. */
class PresenterOverlay(
 private val context:Context,
 private val getCamera:()->CameraInset?,
 private val getMic:()->Boolean,
 private val onToggleMic:()->Unit,
 private val onFlip:()->Unit,
 private val onHide:()->Unit,
 private val onMove:(Float,Float,Float)->Unit
){
 private val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
 private val dp=context.resources.displayMetrics.density
 private var width=(168*dp).toInt()
 private var height=(252*dp).toInt()
 private var root:LinearLayout?=null
 private var params:WindowManager.LayoutParams?=null
 private var image:View?=null
 private var soundButton:Button?=null
 private fun position(){
  val p=params?:return
  val metrics=context.resources.displayMetrics
  val xMax=(metrics.widthPixels-p.width).coerceAtLeast(1)
  val yMax=(metrics.heightPixels-p.height).coerceAtLeast(1)
  onMove((p.x.toFloat()/xMax).coerceIn(0f,1f),(p.y.toFloat()/yMax).coerceIn(0f,1f),(p.width.toFloat()/metrics.widthPixels).coerceIn(.14f,.5f))
 }
 fun show(){
  if(root!=null || !Settings.canDrawOverlays(context))return
  val box=LinearLayout(context).apply{
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(Color.rgb(15,23,42))
  }
  val bar=TextView(context).apply{
   text="⠿  APRESENTADOR  ·  arrastar"
   textSize=11f
   setTextColor(Color.WHITE)
   gravity=Gravity.CENTER
   setPadding(4,8,4,8)
  }
  box.addView(bar,LinearLayout.LayoutParams(-1,(35*dp).toInt()))
  val preview=object:View(context){
   private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
   override fun onDraw(canvas:Canvas){
    super.onDraw(canvas)
    canvas.drawColor(Color.rgb(30,41,59))
    val camera=getCamera()
    if(camera!=null)camera.drawPreview(canvas,width,height)
    else{
     paint.color=Color.WHITE;paint.textSize=14*dp
     canvas.drawText("Câmara desligada",12f,height/2f,paint)
    }
    postInvalidateDelayed(130)
   }
  }
  box.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
  image=preview
  val controls=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
  fun button(label:String,callback:()->Unit):Button=Button(context).apply{
   text=label;textSize=11f;isAllCaps=false;minWidth=0;minimumWidth=0
   setPadding(0,0,0,0);setOnClickListener{callback()}
  }
  val mic=button(if(getMic())"🎙 ON" else "🔇 OFF"){onToggleMic();refresh()}
  soundButton=mic
  controls.addView(mic,LinearLayout.LayoutParams(0,(42*dp).toInt(),1f))
  controls.addView(button("↺"){onFlip();refresh()},LinearLayout.LayoutParams(0,(42*dp).toInt(),1f))
  controls.addView(button("−"){resize(-24)},LinearLayout.LayoutParams(0,(42*dp).toInt(),1f))
  controls.addView(button("+"){resize(24)},LinearLayout.LayoutParams(0,(42*dp).toInt(),1f))
  controls.addView(button("×"){onHide();hide()},LinearLayout.LayoutParams(0,(42*dp).toInt(),1f))
  box.addView(controls)
  val lp=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   android.graphics.PixelFormat.TRANSLUCENT)
  lp.gravity=Gravity.TOP or Gravity.LEFT
  lp.x=(context.resources.displayMetrics.widthPixels-width-12*dp).toInt().coerceAtLeast(0)
  lp.y=(context.resources.displayMetrics.heightPixels-height-110*dp).toInt().coerceAtLeast(0)
  bar.setOnTouchListener(object:View.OnTouchListener{
   var initialX=0;var initialY=0;var downX=0f;var downY=0f
   override fun onTouch(v:View,event:MotionEvent):Boolean{
    when(event.actionMasked){
     MotionEvent.ACTION_DOWN->{initialX=lp.x;initialY=lp.y;downX=event.rawX;downY=event.rawY;return true}
     MotionEvent.ACTION_MOVE->{
      lp.x=(initialX+(event.rawX-downX).toInt()).coerceIn(0,(context.resources.displayMetrics.widthPixels-lp.width).coerceAtLeast(0))
      lp.y=(initialY+(event.rawY-downY).toInt()).coerceIn(0,(context.resources.displayMetrics.heightPixels-lp.height).coerceAtLeast(0))
      try{wm.updateViewLayout(box,lp)}catch(_:Exception){}
      position();return true
     }
     MotionEvent.ACTION_UP->{position();return true}
    }
    return false
   }
  })
  try{wm.addView(box,lp);root=box;params=lp;position()}catch(_:Exception){root=null}
 }
 private fun resize(change:Int){
  val lp=params?:return
  val box=root?:return
  val newWidth=(lp.width+(change*dp).toInt()).coerceIn((112*dp).toInt(),(context.resources.displayMetrics.widthPixels*.48f).toInt())
  lp.width=newWidth;lp.height=(newWidth*1.5f).toInt()
  lp.x=lp.x.coerceIn(0,(context.resources.displayMetrics.widthPixels-lp.width).coerceAtLeast(0))
  lp.y=lp.y.coerceIn(0,(context.resources.displayMetrics.heightPixels-lp.height).coerceAtLeast(0))
  try{wm.updateViewLayout(box,lp)}catch(_:Exception){}
  position()
 }
 fun refresh(){soundButton?.text=if(getMic())"🎙 ON" else "🔇 OFF";image?.invalidate()}
 fun hide(){
  val box=root?:return
  root=null;image=null;params=null
  try{wm.removeView(box)}catch(_:Exception){}
 }
}
