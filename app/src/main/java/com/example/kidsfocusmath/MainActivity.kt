package com.example.kidsfocusmath

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(KidsGameView(this))
    }
}

private enum class Screen {
    HOME, FIND, SAME, COLOR_SHAPE, MAZE, MATCH, COUNT, MATH, MEMORY
}

private data class Bubble(
    val x: Float, val y: Float, val r: Float,
    val kind: Int, val color: Int
)

class KidsGameView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

    private var screen = Screen.HOME
    private var stars = 0
    private var message = "選一個遊戲開始吧！"
    private var difficulty = 1

    private val buttons = mutableListOf<Pair<RectF, Screen>>()
    private val bubbles = mutableListOf<Bubble>()
    private var targetKind = 0
    private var targetCount = 0
    private var foundCount = 0

    private var mathA = 2
    private var mathB = 3
    private var mathOp = "+"
    private var mathAnswer = 5
    private val answerRects = mutableListOf<Pair<RectF, Int>>()

    private var countTarget = 5
    private var countOptions = listOf(4,5,6)

    private var memorySequence = mutableListOf<Int>()
    private var memoryInput = mutableListOf<Int>()
    private var memoryShowing = false
    private var memoryStart = 0L

    private var mazePlayer = PointF(0f, 0f)
    private var mazeGoal = PointF(0f, 0f)
    private val mazeWalls = mutableListOf<RectF>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDetachedFromWindow() {
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(255, 247, 237))
        drawTopBar(canvas)

        when (screen) {
            Screen.HOME -> drawHome(canvas)
            Screen.FIND -> drawFind(canvas)
            Screen.SAME -> drawSame(canvas)
            Screen.COLOR_SHAPE -> drawColorShape(canvas)
            Screen.MAZE -> drawMaze(canvas)
            Screen.MATCH -> drawMatch(canvas)
            Screen.COUNT -> drawCount(canvas)
            Screen.MATH -> drawMath(canvas)
            Screen.MEMORY -> drawMemory(canvas)
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.setShadowLayer(12f, 0f, 4f, 0x22000000)
        canvas.drawRoundRect(20f, 20f, width - 20f, 120f, 28f, 28f, paint)
        paint.clearShadowLayer()

        textPaint.color = Color.rgb(71, 57, 48)
        textPaint.textSize = 34f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("🌟 小小腦力樂園", 42f, 72f, textPaint)

        textPaint.textSize = 26f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("星星 $stars", width - 170f, 73f, textPaint)

        if (screen != Screen.HOME) {
            textPaint.textSize = 25f
            canvas.drawText("← 回首頁", 42f, 108f, textPaint)
        } else {
            canvas.drawText(message, 42f, 108f, textPaint)
        }
    }

    private fun card(canvas: Canvas, rect: RectF, title: String, emoji: String, target: Screen, color: Int) {
        paint.color = color
        paint.setShadowLayer(10f, 0f, 5f, 0x22000000)
        canvas.drawRoundRect(rect, 28f, 28f, paint)
        paint.clearShadowLayer()

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.rgb(65, 53, 45)
        textPaint.textSize = 54f
        canvas.drawText(emoji, rect.centerX(), rect.top + 68f, textPaint)
        textPaint.textSize = 29f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, rect.centerX(), rect.bottom - 26f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
        buttons += rect to target
    }

    private fun drawHome(canvas: Canvas) {
        buttons.clear()
        val gap = 20f
        val left = 28f
        val cardW = (width - left * 2 - gap) / 2f
        val cardH = 150f
        var y = 150f

        val items = listOf(
            Triple("找一找", "🔍", Screen.FIND),
            Triple("找一樣", "🧩", Screen.SAME),
            Triple("顏色圖形", "🎨", Screen.COLOR_SHAPE),
            Triple("迷宮", "🌀", Screen.MAZE),
            Triple("連連看", "🔗", Screen.MATCH),
            Triple("數一數", "🔢", Screen.COUNT),
            Triple("數學小高手", "➕", Screen.MATH),
            Triple("記憶挑戰", "🧠", Screen.MEMORY)
        )
        val colors = intArrayOf(
            0xFFFFE2E2.toInt(), 0xFFE7F6FF.toInt(), 0xFFFFF1C7.toInt(), 0xFFE8F5E9.toInt(),
            0xFFF3E5F5.toInt(), 0xFFE0F2F1.toInt(), 0xFFFFE0B2.toInt(), 0xFFEDE7F6.toInt()
        )
        for (i in items.indices) {
            val col = i % 2
            if (col == 0 && i > 0) y += cardH + gap
            val x = left + col * (cardW + gap)
            val r = RectF(x, y, x + cardW, y + cardH)
            card(canvas, r, items[i].first, items[i].second, items[i].third, colors[i])
        }
    }

    private fun ensureBubbles() {
        if (bubbles.isNotEmpty()) return
        val startY = 210f
        val cols = 5
        val rows = 6
        val dx = width / (cols + 1f)
        val dy = 95f
        targetKind = Random.nextInt(0, 4)
        targetCount = 0
        foundCount = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val kind = Random.nextInt(0, 4)
                if (kind == targetKind) targetCount++
                val colors = intArrayOf(
                    0xFFFF6B6B.toInt(), 0xFF4D96FF.toInt(),
                    0xFF6BCB77.toInt(), 0xFFFFC75F.toInt()
                )
                bubbles += Bubble(
                    dx * (c + 1), startY + dy * r, 29f,
                    kind, colors[kind]
                )
            }
        }
        if (targetCount == 0) {
            bubbles[0] = bubbles[0].copy(kind = targetKind)
            targetCount = 1
        }
    }

    private fun symbolFor(kind: Int) = when(kind) {
        0 -> "●"
        1 -> "■"
        2 -> "▲"
        else -> "★"
    }

    private fun drawFind(canvas: Canvas) {
        ensureBubbles()
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 31f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("找出所有 ${symbolFor(targetKind)}", 35f, 165f, textPaint)
        textPaint.textSize = 23f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("找到 $foundCount / $targetCount", 35f, 198f, textPaint)

        for (b in bubbles) {
            paint.color = b.color
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = b.color
            textPaint.textSize = 56f
            canvas.drawText(symbolFor(b.kind), b.x, b.y + 18f, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawSame(canvas: Canvas) {
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("哪一個和左邊一模一樣？", 35f, 170f, textPaint)

        val sample = RectF(40f, 220f, 220f, 400f)
        paint.color = 0xFFFFE0B2.toInt()
        canvas.drawRoundRect(sample, 24f, 24f, paint)
        drawFlower(canvas, sample.centerX(), sample.centerY(), 58f, 8, Color.rgb(236, 99, 89))

        val opts = listOf(8, 7, 9, 6)
        answerRects.clear()
        var y = 220f
        for (i in opts.indices) {
            val r = RectF(width - 260f, y, width - 60f, y + 135f)
            paint.color = Color.WHITE
            canvas.drawRoundRect(r, 22f, 22f, paint)
            drawFlower(canvas, r.centerX(), r.centerY(), 48f, opts[i], Color.rgb(236, 99, 89))
            answerRects += r to (if (opts[i] == 8) 1 else 0)
            y += 150f
        }
    }

    private fun drawFlower(canvas: Canvas, cx: Float, cy: Float, radius: Float, petals: Int, color: Int) {
        paint.color = color
        for (i in 0 until petals) {
            val a = Math.PI * 2 * i / petals
            val px = cx + cos(a).toFloat() * radius
            val py = cy + sin(a).toFloat() * radius
            canvas.drawCircle(px, py, radius * 0.36f, paint)
        }
        paint.color = 0xFFFFD166.toInt()
        canvas.drawCircle(cx, cy, radius * 0.48f, paint)
    }

    private fun drawColorShape(canvas: Canvas) {
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("點出所有藍色圓形", 35f, 170f, textPaint)

        if (bubbles.isEmpty()) {
            val cols = 4
            val dx = width / 5f
            val colors = intArrayOf(
                0xFF4D96FF.toInt(), 0xFFFF6B6B.toInt(), 0xFF6BCB77.toInt()
            )
            for (r in 0 until 5) {
                for (c in 0 until cols) {
                    val kind = Random.nextInt(0, 2)
                    val color = colors[Random.nextInt(colors.size)]
                    bubbles += Bubble(dx * (c + 1), 250f + 110f * r, 34f, kind, color)
                }
            }
        }
        for (b in bubbles) {
            paint.color = b.color
            if (b.kind == 0) {
                canvas.drawCircle(b.x, b.y, b.r, paint)
            } else {
                canvas.drawRect(b.x-b.r, b.y-b.r, b.x+b.r, b.y+b.r, paint)
            }
        }
    }

    private fun initMaze() {
        if (mazeWalls.isNotEmpty()) return
        mazePlayer = PointF(70f, 220f)
        mazeGoal = PointF(width - 80f, 760f)
        mazeWalls += RectF(130f, 200f, 170f, 620f)
        mazeWalls += RectF(280f, 360f, 320f, 820f)
        mazeWalls += RectF(430f, 180f, 470f, 620f)
        mazeWalls += RectF(580f, 360f, 620f, 820f)
    }

    private fun drawMaze(canvas: Canvas) {
        initMaze()
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 29f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("拖曳小星星走到寶箱！", 35f, 170f, textPaint)

        paint.style = Paint.Style.FILL
        paint.color = 0xFF5D4037.toInt()
        for (w in mazeWalls) canvas.drawRoundRect(w, 12f, 12f, paint)

        textPaint.textSize = 56f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("⭐", mazePlayer.x, mazePlayer.y + 18f, textPaint)
        canvas.drawText("🎁", mazeGoal.x, mazeGoal.y + 18f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMatch(canvas: Canvas) {
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("找出相同的一對", 35f, 170f, textPaint)

        val icons = listOf("🐶","🐱","🐰","🦊","🐼","🐯")
        answerRects.clear()
        var idx = 0
        for (r in 0 until 3) {
            for (c in 0 until 2) {
                val rect = RectF(
                    70f + c * (width/2f),
                    230f + r*190f,
                    250f + c * (width/2f),
                    360f + r*190f
                )
                paint.color = Color.WHITE
                canvas.drawRoundRect(rect, 22f, 22f, paint)
                textPaint.textSize = 70f
                textPaint.textAlign = Paint.Align.CENTER
                val icon = if (idx < 2) "🐶" else icons[idx]
                canvas.drawText(icon, rect.centerX(), rect.centerY()+25f, textPaint)
                answerRects += rect to (if (idx < 2) 1 else 0)
                idx++
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCount(canvas: Canvas) {
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("數一數：有幾顆蘋果？", 35f, 170f, textPaint)

        val cols = 4
        textPaint.textSize = 58f
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 0 until countTarget) {
            val c = i % cols
            val r = i / cols
            canvas.drawText("🍎", 100f + c*145f, 280f + r*110f, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        answerRects.clear()
        val y = 670f
        for (i in countOptions.indices) {
            val x = 45f + i*((width-120f)/3f)
            val rect = RectF(x, y, x+120f, y+90f)
            paint.color = 0xFFE3F2FD.toInt()
            canvas.drawRoundRect(rect, 18f,18f,paint)
            textPaint.textSize = 34f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.DKGRAY
            canvas.drawText(countOptions[i].toString(), rect.centerX(), rect.centerY()+12f, textPaint)
            answerRects += rect to countOptions[i]
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun newMathQuestion() {
        val max = when(difficulty) {
            1 -> 10
            2 -> 15
            else -> 20
        }
        mathOp = if (Random.nextBoolean()) "+" else "-"
        if (mathOp == "+") {
            mathA = Random.nextInt(1, max/2 + 1)
            mathB = Random.nextInt(1, max/2 + 1)
            mathAnswer = mathA + mathB
        } else {
            mathA = Random.nextInt(2, max + 1)
            mathB = Random.nextInt(1, mathA)
            mathAnswer = mathA - mathB
        }
    }

    private fun drawMath(canvas: Canvas) {
        if (mathAnswer == 5 && mathA == 2 && mathB == 3) newMathQuestion()
        textPaint.color = Color.DKGRAY
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("數學小高手", 35f, 170f, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 64f
        canvas.drawText("$mathA  $mathOp  $mathB  =  ?", width/2f, 330f, textPaint)

        val wrong1 = max(0, mathAnswer + 1)
        val wrong2 = max(0, mathAnswer - 1)
        val options = listOf(mathAnswer, wrong1, wrong2).shuffled()
        answerRects.clear()
        for (i in options.indices) {
            val rect = RectF(80f, 430f + i*125f, width - 80f, 525f + i*125f)
            paint.color = 0xFFFFF3E0.toInt()
            canvas.drawRoundRect(rect, 22f, 22f, paint)
            textPaint.textSize = 42f
            canvas.drawText(options[i].toString(), rect.centerX(), rect.centerY()+14f, textPaint)
            answerRects += rect to options[i]
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun resetMemory() {
        memorySequence = MutableList(3 + difficulty) { Random.nextInt(0, 4) }
        memoryInput.clear()
        memoryShowing = true
        memoryStart = System.currentTimeMillis()
        postInvalidateDelayed(2200)
    }

    private fun drawMemory(canvas: Canvas) {
        if (memorySequence.isEmpty()) resetMemory()
        val elapsed = System.currentTimeMillis() - memoryStart
        if (memoryShowing && elapsed > 1900) {
            memoryShowing = false
        }

        textPaint.color = Color.DKGRAY
        textPaint.textSize = 29f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(
            if (memoryShowing) "記住亮起來的順序" else "照剛才的順序點",
            35f, 170f, textPaint
        )
        val centers = listOf(
            PointF(width*0.3f, 340f),
            PointF(width*0.7f, 340f),
            PointF(width*0.3f, 580f),
            PointF(width*0.7f, 580f)
        )
        val colors = intArrayOf(
            0xFFFF6B6B.toInt(), 0xFF4D96FF.toInt(),
            0xFF6BCB77.toInt(), 0xFFFFC75F.toInt()
        )
        answerRects.clear()
        for (i in 0..3) {
            var color = colors[i]
            if (memoryShowing && memorySequence.contains(i)) {
                color = lighten(color)
            }
            paint.color = color
            val r = RectF(centers[i].x-85f, centers[i].y-85f, centers[i].x+85f, centers[i].y+85f)
            canvas.drawRoundRect(r, 30f,30f,paint)
            answerRects += r to i
        }

        if (memoryShowing) {
            textPaint.textSize = 26f
            textPaint.typeface = Typeface.DEFAULT
            canvas.drawText("順序：${memorySequence.joinToString(" → ") { (it+1).toString() }}", 45f, 760f, textPaint)
        } else {
            canvas.drawText("已輸入 ${memoryInput.size}/${memorySequence.size}", 45f, 760f, textPaint)
        }
    }

    private fun lighten(color: Int): Int {
        val r = min(255, Color.red(color) + 60)
        val g = min(255, Color.green(color) + 60)
        val b = min(255, Color.blue(color) + 60)
        return Color.rgb(r,g,b)
    }

    private fun success(text: String = "答對了！") {
        stars++
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        message = "$text ⭐ +1"
    }

    private fun retry() {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 120)
        message = "再試一次～"
    }

    private fun resetGameState() {
        bubbles.clear()
        buttons.clear()
        answerRects.clear()
        mazeWalls.clear()
        memorySequence.clear()
        memoryInput.clear()
        foundCount = 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (screen != Screen.HOME && event.action == MotionEvent.ACTION_DOWN && y < 130f) {
            screen = Screen.HOME
            resetGameState()
            invalidate()
            return true
        }

        when (screen) {
            Screen.HOME -> if (event.action == MotionEvent.ACTION_DOWN) {
                buttons.firstOrNull { it.first.contains(x,y) }?.let {
                    screen = it.second
                    resetGameState()
                    invalidate()
                }
            }

            Screen.FIND -> if (event.action == MotionEvent.ACTION_DOWN) {
                val idx = bubbles.indexOfFirst { hypot((it.x-x).toDouble(), (it.y-y).toDouble()) < it.r*1.4 }
                if (idx >= 0) {
                    val b = bubbles[idx]
                    if (b.kind == targetKind) {
                        bubbles.removeAt(idx)
                        foundCount++
                        success()
                        if (foundCount == targetCount) {
                            message = "全部找到了！"
                            postDelayed({
                                resetGameState()
                                invalidate()
                            }, 600)
                        }
                    } else retry()
                    invalidate()
                }
            }

            Screen.SAME, Screen.MATCH -> if (event.action == MotionEvent.ACTION_DOWN) {
                val hit = answerRects.firstOrNull { it.first.contains(x,y) }
                if (hit != null) {
                    if (hit.second == 1) {
                        success()
                        postDelayed({ invalidate() }, 400)
                    } else retry()
                }
            }

            Screen.COLOR_SHAPE -> if (event.action == MotionEvent.ACTION_DOWN) {
                val idx = bubbles.indexOfFirst { hypot((it.x-x).toDouble(), (it.y-y).toDouble()) < it.r*1.5 }
                if (idx >= 0) {
                    val b = bubbles[idx]
                    if (b.kind == 0 && b.color == 0xFF4D96FF.toInt()) {
                        bubbles.removeAt(idx)
                        success()
                    } else retry()
                    invalidate()
                }
            }

            Screen.MAZE -> {
                if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                    val candidate = RectF(x-24f,y-24f,x+24f,y+24f)
                    val blocked = mazeWalls.any { RectF.intersects(it,candidate) }
                    if (!blocked && y > 160f) {
                        mazePlayer.x = x
                        mazePlayer.y = y
                        if (hypot((mazePlayer.x-mazeGoal.x).toDouble(), (mazePlayer.y-mazeGoal.y).toDouble()) < 70) {
                            success("到達寶箱！")
                            resetGameState()
                        }
                        invalidate()
                    }
                }
            }

            Screen.COUNT -> if (event.action == MotionEvent.ACTION_DOWN) {
                val hit = answerRects.firstOrNull { it.first.contains(x,y) }
                if (hit != null) {
                    if (hit.second == countTarget) {
                        success()
                        countTarget = Random.nextInt(3, 11)
                        countOptions = listOf(
                            max(1,countTarget-1), countTarget, countTarget+1
                        ).shuffled()
                    } else retry()
                    invalidate()
                }
            }

            Screen.MATH -> if (event.action == MotionEvent.ACTION_DOWN) {
                val hit = answerRects.firstOrNull { it.first.contains(x,y) }
                if (hit != null) {
                    if (hit.second == mathAnswer) {
                        success()
                        newMathQuestion()
                    } else retry()
                    invalidate()
                }
            }

            Screen.MEMORY -> if (event.action == MotionEvent.ACTION_DOWN && !memoryShowing) {
                val hit = answerRects.firstOrNull { it.first.contains(x,y) }
                if (hit != null) {
                    memoryInput.add(hit.second)
                    val idx = memoryInput.size - 1
                    if (memorySequence[idx] != hit.second) {
                        retry()
                        resetMemory()
                    } else if (memoryInput.size == memorySequence.size) {
                        success("記憶成功！")
                        if (difficulty < 3) difficulty++
                        resetMemory()
                    }
                    invalidate()
                }
            }
        }
        return true
    }
}
