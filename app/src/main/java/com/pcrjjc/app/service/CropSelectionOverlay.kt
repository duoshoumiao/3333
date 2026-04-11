package com.pcrjjc.app.service  
  
import android.annotation.SuppressLint  
import android.content.Context  
import android.graphics.*  
import android.util.TypedValue  
import android.view.Gravity  
import android.view.MotionEvent  
import android.view.View  
import android.view.ViewGroup  
import android.widget.FrameLayout  
import android.widget.LinearLayout  
import android.widget.TextView  
  
/**  
 * 全屏截图选区覆盖层。  
 * 显示截图并让用户手动框选头像区域。  
 */  
@SuppressLint("ViewConstructor")  
class CropSelectionOverlay(  
    context: Context,  
    private val screenshot: Bitmap,  
    private val onConfirm: (Rect) -> Unit,  
    private val onCancel: () -> Unit  
) : FrameLayout(context) {  
  
    private val selectionRect = RectF()  
    private var isDrawing = false  
    private var startX = 0f  
    private var startY = 0f  
  
    // 截图显示的缩放/偏移（截图可能和屏幕尺寸不同）  
    private val imageRect = RectF()  
    private var scaleX = 1f  
    private var scaleY = 1f  
  
    private val dimPaint = Paint().apply {  
        color = 0x88000000.toInt()  
        style = Paint.Style.FILL  
    }  
    private val borderPaint = Paint().apply {  
        color = 0xFF4CAF50.toInt()  
        style = Paint.Style.STROKE  
        strokeWidth = dp(3f)  
        isAntiAlias = true  
    }  
    private val hintPaint = Paint().apply {  
        color = Color.WHITE  
        textSize = dp(16f)  
        isAntiAlias = true  
        textAlign = Paint.Align.CENTER  
    }  
  
    private val drawingView: DrawingView  
    private val buttonsLayout: LinearLayout  
  
    init {  
        // 截图背景  
        val imageView = View(context)  
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))  
  
        // 绘制层  
        drawingView = DrawingView(context)  
        addView(drawingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))  
  
        // 按钮栏  
        buttonsLayout = LinearLayout(context).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER  
            setBackgroundColor(0xCC222222.toInt())  
            setPadding(dp(16).toInt(), dp(10).toInt(), dp(16).toInt(), dp(10).toInt())  
        }  
  
        val hintText = TextView(context).apply {  
            text = "请框选防守阵容头像区域"  
            setTextColor(Color.WHITE)  
            textSize = 14f  
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)  
        }  
  
        val cancelBtn = TextView(context).apply {  
            text = "取消"  
            setTextColor(Color.LTGRAY)  
            textSize = 15f  
            setPadding(dp(16).toInt(), dp(8).toInt(), dp(16).toInt(), dp(8).toInt())  
            setOnClickListener { onCancel() }  
        }  
  
        val confirmBtn = TextView(context).apply {  
            text = "确认"  
            setTextColor(0xFF90CAF9.toInt())  
            textSize = 15f  
            setPadding(dp(16).toInt(), dp(8).toInt(), dp(16).toInt(), dp(8).toInt())  
            setOnClickListener {  
                if (selectionRect.width() > 20 && selectionRect.height() > 20) {  
                    // 将屏幕坐标转换回截图坐标  
                    val cropRect = Rect(  
                        ((selectionRect.left - imageRect.left) / scaleX).toInt().coerceAtLeast(0),  
                        ((selectionRect.top - imageRect.top) / scaleY).toInt().coerceAtLeast(0),  
                        ((selectionRect.right - imageRect.left) / scaleX).toInt().coerceAtMost(screenshot.width),  
                        ((selectionRect.bottom - imageRect.top) / scaleY).toInt().coerceAtMost(screenshot.height)  
                    )  
                    onConfirm(cropRect)  
                }  
            }  
        }  
  
        buttonsLayout.addView(hintText)  
        buttonsLayout.addView(cancelBtn)  
        buttonsLayout.addView(confirmBtn)  
  
        val btnParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {  
            gravity = Gravity.BOTTOM  
        }  
        addView(buttonsLayout, btnParams)  
    }  
  
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {  
        super.onSizeChanged(w, h, oldw, oldh)  
        // 计算截图在屏幕上的显示区域（保持比例填满）  
        scaleX = w.toFloat() / screenshot.width  
        scaleY = h.toFloat() / screenshot.height  
        imageRect.set(0f, 0f, w.toFloat(), h.toFloat())  
    }  
  
    @SuppressLint("ClickableViewAccessibility")  
    private inner class DrawingView(context: Context) : View(context) {  
  
        private val screenshotPaint = Paint().apply {  
            isFilterBitmap = true  
        }  
  
        override fun onDraw(canvas: Canvas) {  
            super.onDraw(canvas)  
  
            // 绘制截图  
            canvas.drawBitmap(  
                screenshot, null,  
                RectF(0f, 0f, width.toFloat(), height.toFloat()),  
                screenshotPaint  
            )  
  
            // 绘制暗色遮罩（选区外部）  
            if (selectionRect.width() > 0 && selectionRect.height() > 0) {  
                // 上  
                canvas.drawRect(0f, 0f, width.toFloat(), selectionRect.top, dimPaint)  
                // 下  
                canvas.drawRect(0f, selectionRect.bottom, width.toFloat(), height.toFloat(), dimPaint)  
                // 左  
                canvas.drawRect(0f, selectionRect.top, selectionRect.left, selectionRect.bottom, dimPaint)  
                // 右  
                canvas.drawRect(selectionRect.right, selectionRect.top, width.toFloat(), selectionRect.bottom, dimPaint)  
                // 选区边框  
                canvas.drawRect(selectionRect, borderPaint)  
            } else {  
                // 未选择时全屏半透明 + 提示文字  
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)  
                canvas.drawText(  
                    "触摸并拖动框选头像区域",  
                    width / 2f, height / 2f, hintPaint  
                )  
            }  
        }  
  
        override fun onTouchEvent(event: MotionEvent): Boolean {  
            // 不处理按钮区域的触摸  
            if (event.y > height - buttonsLayout.height) return false  
  
            when (event.action) {  
                MotionEvent.ACTION_DOWN -> {  
                    startX = event.x  
                    startY = event.y  
                    isDrawing = true  
                    selectionRect.set(startX, startY, startX, startY)  
                    invalidate()  
                    return true  
                }  
                MotionEvent.ACTION_MOVE -> {  
                    if (isDrawing) {  
                        selectionRect.set(  
                            minOf(startX, event.x),  
                            minOf(startY, event.y),  
                            maxOf(startX, event.x),  
                            maxOf(startY, event.y)  
                        )  
                        invalidate()  
                    }  
                    return true  
                }  
                MotionEvent.ACTION_UP -> {  
                    isDrawing = false  
                    return true  
                }  
            }  
            return super.onTouchEvent(event)  
        }  
    }  
  
    private fun dp(value: Float): Float {  
        return TypedValue.applyDimension(  
            TypedValue.COMPLEX_UNIT_DIP, value,  
            resources.displayMetrics  
        )  
    }  
}