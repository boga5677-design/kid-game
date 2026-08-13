package com.example.kidsfocusmath

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
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
    val x: Float,
    val y: Float,
    val r: Float,
    val kind: Int,
    val color: Int
)

private data class GameTile(
    val title: String,
    val subtitle: String,
    val target: Screen,
    val color: Int,
    val icon: Int
)

private data class MatchCard(
    val pairId: Int,
    val side: Int,
    val label: String,
    var rect: RectF = RectF()
)

class KidsGameView(context: Context) : View(context), TextToSpeech.OnInitListener {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
    private val handler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context, this)
    private val prefs = context.getSharedPreferences("kids_focus_math", Context.MODE_PRIVATE)

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private var ttsReady = false
    private var screen = Screen.HOME
    private var stars = prefs.getInt("stars", 0)
    private var gamesPlayed = prefs.getInt("games_played", 0)
    private var difficulty = prefs.getInt("difficulty", 1).coerceIn(1, 3)
    private var questionText = ""

    private var feedbackText = ""
    private var feedbackUntil = 0L
    private var celebrateUntil = 0L

    private val homeGameRects = mutableListOf<Pair<RectF, Screen>>()
    private val backRect = RectF()
    private val replayRect = RectF()
    private val answerRects = mutableListOf<Pair<RectF, Int>>()

    private val bubbles = mutableListOf<Bubble>()
    private var targetKind = 0
    private var targetCount = 0
    private var foundCount = 0

    private var sameCorrectIndex = 0

    private var countTarget = 5
    private var countOptions = listOf(4, 5, 6)

    private var mathA = 2
    private var mathB = 3
    private var mathOp = "+"
    private var mathAnswer = 5
    private var mathOptions = listOf(4, 5, 6)

    private var memorySequence = mutableListOf<Int>()
    private var memoryInput = mutableListOf<Int>()
    private var memoryShowing = false

    private var mazePlayer = PointF()
    private var mazeGoal = PointF()
    private val mazeWalls = mutableListOf<RectF>()
    private var mazeDragging = false

    // 連連看：同時支援拖曳連線與點兩張卡片。
    private val matchCards = mutableListOf<MatchCard>()
    private val matchedPairIds = mutableSetOf<Int>()
    private var matchDragStart = -1
    private var matchTapSelected = -1
    private var matchDragPoint = PointF()

    private val gameTiles = listOf(
        GameTile("找一找", "專注搜尋", Screen.FIND, 0xFFE7F6D5.toInt(), 0),
        GameTile("找一樣", "觀察細節", Screen.SAME, 0xFFFFE2C8.toInt(), 1),
        GameTile("顏色圖形", "辨識形狀", Screen.COLOR_SHAPE, 0xFFEAD7FF.toInt(), 2),
        GameTile("迷宮", "手眼協調", Screen.MAZE, 0xFFDDE9FF.toInt(), 3),
        GameTile("連連看", "拖曳配對", Screen.MATCH, 0xFFFFD8E6.toInt(), 4),
        GameTile("數一數", "數量概念", Screen.COUNT, 0xFFD4F4F0.toInt(), 5),
        GameTile("數學小高手", "基本加減法", Screen.MATH, 0xFFD8EAFF.toInt(), 6),
        GameTile("記憶挑戰", "短期記憶", Screen.MEMORY, 0xFFFFE4C9.toInt(), 7)
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
        isClickable = true
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.TAIWAN)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.86f)
            tts.setPitch(1.04f)
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFFFFF8EA.toInt())

        if (screen == Screen.HOME) {
            drawHome(canvas)
        } else {
            drawGameHeader(canvas)
            when (screen) {
                Screen.FIND -> drawFind(canvas)
                Screen.SAME -> drawSame(canvas)
                Screen.COLOR_SHAPE -> drawColorShape(canvas)
                Screen.MAZE -> drawMaze(canvas)
                Screen.MATCH -> drawMatch(canvas)
                Screen.COUNT -> drawCount(canvas)
                Screen.MATH -> drawMath(canvas)
                Screen.MEMORY -> drawMemory(canvas)
                else -> Unit
            }
        }

        drawFeedback(canvas)
        drawCelebration(canvas)
    }

    // -------------------------- 首頁 --------------------------

    private fun drawHome(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = dp(14f)

        // 頂部資訊列
        paint.color = Color.WHITE
        paint.setShadowLayer(dp(5f), 0f, dp(2f), 0x22000000)
        canvas.drawRoundRect(margin, dp(12f), w - margin, dp(68f), dp(20f), dp(20f), paint)
        paint.clearShadowLayer()

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = 0xFF604B3C.toInt()
        textPaint.textSize = sp(17f)
        canvas.drawText("👦 小朋友", dp(28f), dp(48f), textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(18f)
        canvas.drawText("⭐ $stars", w * 0.55f, dp(48f), textPaint)
        canvas.drawText("🪙 ${stars * 10}", w * 0.78f, dp(48f), textPaint)

        // 任務與寶箱
        val task = RectF(dp(18f), dp(82f), dp(120f), dp(137f))
        val treasure = RectF(w - dp(120f), dp(82f), w - dp(18f), dp(137f))
        drawMiniStatus(canvas, task, "每日任務", "${min(gamesPlayed, 5)}/5", 0xFFEAF6D2.toInt())
        drawMiniStatus(canvas, treasure, "星星寶箱", "${stars % 30}/30", 0xFFFFE8B0.toInt())

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = 0xFFE84F6B.toInt()
        textPaint.textSize = sp(31f)
        canvas.drawText("一起來挑戰吧！", w / 2f, dp(116f), textPaint)

        // 三位去背毛孩小老師
        val mascotY = dp(193f)
        val s = min(dp(62f), w * 0.105f)
        drawOguiTeacher(canvas, w * 0.25f, mascotY, s)
        drawBrownSugarTeacher(canvas, w * 0.50f, mascotY - dp(4f), s * 1.08f)
        drawBearTeacher(canvas, w * 0.75f, mascotY, s)

        textPaint.textSize = sp(14f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = 0xFF5E4C3F.toInt()
        canvas.drawText("偶貴老師", w * 0.25f, dp(267f), textPaint)
        canvas.drawText("黑糖老師", w * 0.50f, dp(267f), textPaint)
        canvas.drawText("熊熊老師", w * 0.75f, dp(267f), textPaint)

        // 8 個大型遊戲按鈕
        homeGameRects.clear()
        val gridTop = dp(286f)
        val gridBottomReserved = dp(86f)
        val gap = dp(10f)
        val left = dp(14f)
        val cardW = (w - left * 2f - gap) / 2f
        val usableH = max(dp(330f), h - gridTop - gridBottomReserved)
        val cardH = min(dp(86f), (usableH - gap * 3f) / 4f)

        for (i in gameTiles.indices) {
            val row = i / 2
            val col = i % 2
            val x = left + col * (cardW + gap)
            val y = gridTop + row * (cardH + gap)
            val r = RectF(x, y, x + cardW, y + cardH)
            drawHomeGameTile(canvas, r, gameTiles[i])
            homeGameRects += RectF(r) to gameTiles[i].target
        }

        // 底部週進度
        val bottomY = h - dp(66f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(dp(18f), bottomY, w - dp(18f), h - dp(14f), dp(18f), dp(18f), paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(14f)
        textPaint.color = 0xFF725C49.toInt()
        canvas.drawText("⭐ 本週星星：$stars / 200", dp(34f), bottomY + dp(22f), textPaint)
        drawProgressBar(canvas, dp(34f), bottomY + dp(32f), w - dp(34f), bottomY + dp(45f), (stars % 201) / 200f)
    }

    private fun drawMiniStatus(canvas: Canvas, r: RectF, title: String, value: String, color: Int) {
        paint.color = color
        canvas.drawRoundRect(r, dp(14f), dp(14f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF665342.toInt()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(12f)
        canvas.drawText(title, r.centerX(), r.top + dp(21f), textPaint)
        textPaint.textSize = sp(15f)
        canvas.drawText(value, r.centerX(), r.bottom - dp(10f), textPaint)
    }

    private fun drawHomeGameTile(canvas: Canvas, r: RectF, tile: GameTile) {
        paint.color = tile.color
        paint.setShadowLayer(dp(3f), 0f, dp(2f), 0x25000000)
        canvas.drawRoundRect(r, dp(18f), dp(18f), paint)
        paint.clearShadowLayer()

        val iconCx = r.left + min(dp(45f), r.width() * 0.25f)
        val iconCy = r.centerY()
        drawTileIcon(canvas, iconCx, iconCy, min(dp(25f), r.height() * 0.30f), tile.icon)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFF56473E.toInt()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(18f)
        canvas.drawText(tile.title, r.left + dp(78f), r.centerY() - dp(3f), textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = sp(13f)
        textPaint.color = 0xFF806B59.toInt()
        canvas.drawText(tile.subtitle, r.left + dp(78f), r.centerY() + dp(21f), textPaint)
    }

    // -------------------------- 遊戲共用標頭 --------------------------

    private fun gameTitle(): String = when (screen) {
        Screen.FIND -> "找一找"
        Screen.SAME -> "找一樣"
        Screen.COLOR_SHAPE -> "顏色圖形"
        Screen.MAZE -> "迷宮"
        Screen.MATCH -> "連連看"
        Screen.COUNT -> "數一數"
        Screen.MATH -> "數學小高手"
        Screen.MEMORY -> "記憶挑戰"
        else -> ""
    }

    private fun drawGameHeader(canvas: Canvas) {
        val w = width.toFloat()
        val topCardBottom = dp(174f)

        paint.color = 0xFFE8F6FF.toInt()
        canvas.drawRect(0f, 0f, w, topCardBottom + dp(8f), paint)

        // 返回
        backRect.set(dp(12f), dp(16f), dp(74f), dp(62f))
        paint.color = 0xFF4C8FE8.toInt()
        canvas.drawRoundRect(backRect, dp(18f), dp(18f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(22f)
        canvas.drawText("←", backRect.centerX(), backRect.centerY() + dp(7f), textPaint)

        // 標題
        textPaint.color = 0xFFE94F6C.toInt()
        textPaint.textSize = sp(if (gameTitle().length > 5) 27f else 33f)
        canvas.drawText(gameTitle(), w / 2f, dp(54f), textPaint)

        // 題目大卡片
        val qRect = RectF(dp(16f), dp(74f), w - dp(16f), dp(128f))
        paint.color = Color.WHITE
        paint.setShadowLayer(dp(3f), 0f, dp(1f), 0x1A000000)
        canvas.drawRoundRect(qRect, dp(18f), dp(18f), paint)
        paint.clearShadowLayer()
        textPaint.color = 0xFF493F39.toInt()
        textPaint.textSize = sp(if (questionText.length > 16) 20f else 23f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(questionText, qRect.centerX(), qRect.centerY() + dp(7f), textPaint)

        // 大型朗讀/重播按鈕
        replayRect.set(w / 2f - dp(93f), dp(137f), w / 2f + dp(93f), dp(172f))
        paint.color = 0xFF3978CF.toInt()
        canvas.drawRoundRect(replayRect, dp(16f), dp(16f), paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = sp(16f)
        canvas.drawText("🔊 朗讀題目・重播", replayRect.centerX(), replayRect.centerY() + dp(5f), textPaint)

        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun enterGame(target: Screen) {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        screen = target
        resetGameState()
        prepareGame(target)
        invalidate()
        handler.postDelayed({ speakQuestion() }, 500L)
    }

    private fun prepareGame(target: Screen) {
        when (target) {
            Screen.FIND -> prepareFind()
            Screen.SAME -> prepareSame()
            Screen.COLOR_SHAPE -> prepareColorShape()
            Screen.MAZE -> prepareMaze()
            Screen.MATCH -> prepareMatch()
            Screen.COUNT -> prepareCount()
            Screen.MATH -> prepareMath()
            Screen.MEMORY -> prepareMemory()
            else -> Unit
        }
    }

    private fun speakQuestion() {
        if (!ttsReady || questionText.isBlank()) return
        tts.stop()
        tts.speak(questionText, TextToSpeech.QUEUE_FLUSH, null, "kids_question")
    }

    private fun scheduleReadQuestion() {
        handler.postDelayed({ speakQuestion() }, 500L)
    }

    // -------------------------- 找一找 --------------------------

    private fun prepareFind() {
        targetKind = Random.nextInt(0, 4)
        questionText = "請找出所有 ${symbolName(targetKind)}。"
        targetCount = 0
        foundCount = 0
        bubbles.clear()

        val cols = 4
        val rows = 5
        val left = dp(44f)
        val right = width - dp(44f)
        val top = dp(235f)
        val bottom = height - dp(46f)
        val dx = (right - left) / (cols - 1)
        val dy = (bottom - top) / (rows - 1)
        val r = min(dp(25f), dx * 0.25f)
        val colors = intArrayOf(
            0xFFF35F69.toInt(), 0xFF4D8FE8.toInt(),
            0xFF65B96C.toInt(), 0xFFFFB93D.toInt()
        )

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val kind = Random.nextInt(0, 4)
                if (kind == targetKind) targetCount++
                bubbles += Bubble(left + dx * col, top + dy * row, r, kind, colors[kind])
            }
        }
        if (targetCount == 0) {
            bubbles[0] = bubbles[0].copy(kind = targetKind, color = colors[targetKind])
            targetCount = 1
        }
    }

    private fun symbolFor(kind: Int) = when (kind) {
        0 -> "●"
        1 -> "■"
        2 -> "▲"
        else -> "★"
    }

    private fun symbolName(kind: Int) = when (kind) {
        0 -> "圓形"
        1 -> "正方形"
        2 -> "三角形"
        else -> "星星"
    }

    private fun drawFind(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(22f)
        textPaint.color = 0xFF665449.toInt()
        canvas.drawText("找到 $foundCount / $targetCount", width / 2f, dp(212f), textPaint)

        for (b in bubbles) {
            textPaint.color = b.color
            textPaint.textSize = sp(47f)
            canvas.drawText(symbolFor(b.kind), b.x, b.y + sp(15f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // -------------------------- 找一樣 --------------------------

    private fun prepareSame() {
        questionText = "請找出和上面完全一樣的花朵。"
        sameCorrectIndex = Random.nextInt(0, 4)
    }

    private fun drawSame(canvas: Canvas) {
        val w = width.toFloat()
        val sample = RectF(w * 0.27f, dp(201f), w * 0.73f, dp(335f))
        paint.color = 0xFFFFE8C8.toInt()
        canvas.drawRoundRect(sample, dp(22f), dp(22f), paint)
        drawFlower(canvas, sample.centerX(), sample.centerY(), dp(34f), 8, 0xFFEB655B.toInt())

        answerRects.clear()
        val gap = dp(12f)
        val left = dp(18f)
        val top = dp(365f)
        val cardW = (w - left * 2f - gap) / 2f
        val cardH = min(dp(145f), (height - top - dp(26f) - gap) / 2f)

        for (i in 0 until 4) {
            val row = i / 2
            val col = i % 2
            val r = RectF(
                left + col * (cardW + gap),
                top + row * (cardH + gap),
                left + col * (cardW + gap) + cardW,
                top + row * (cardH + gap) + cardH
            )
            paint.color = Color.WHITE
            paint.setShadowLayer(dp(3f), 0f, dp(2f), 0x1F000000)
            canvas.drawRoundRect(r, dp(22f), dp(22f), paint)
            paint.clearShadowLayer()

            val distractors = intArrayOf(6, 7, 9, 10)
            val petals = if (i == sameCorrectIndex) 8 else distractors[i]
            drawFlower(canvas, r.centerX(), r.centerY(), min(dp(39f), cardH * 0.28f), petals, 0xFFEB655B.toInt())
            answerRects += RectF(r) to if (i == sameCorrectIndex) 1 else 0
        }
    }

    // -------------------------- 顏色圖形 --------------------------

    private fun prepareColorShape() {
        questionText = "請點出所有藍色圓形。"
        bubbles.clear()

        val cols = 4
        val rows = 4
        val left = dp(48f)
        val right = width - dp(48f)
        val top = dp(240f)
        val bottom = height - dp(60f)
        val dx = (right - left) / (cols - 1)
        val dy = (bottom - top) / (rows - 1)
        val colors = intArrayOf(0xFF4D8FE8.toInt(), 0xFFF05F68.toInt(), 0xFF63B66B.toInt())

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val kind = Random.nextInt(0, 2)
                val color = colors[Random.nextInt(colors.size)]
                bubbles += Bubble(left + dx * c, top + dy * r, dp(25f), kind, color)
            }
        }
        bubbles[0] = bubbles[0].copy(kind = 0, color = 0xFF4D8FE8.toInt())
    }

    private fun drawColorShape(canvas: Canvas) {
        for (b in bubbles) {
            paint.color = b.color
            if (b.kind == 0) {
                canvas.drawCircle(b.x, b.y, b.r, paint)
            } else {
                canvas.drawRoundRect(b.x - b.r, b.y - b.r, b.x + b.r, b.y + b.r, dp(7f), dp(7f), paint)
            }
        }
    }

    // -------------------------- 迷宮 --------------------------

    private fun prepareMaze() {
        questionText = "拖曳星星，繞過障礙走到寶箱。"
        val w = width.toFloat()
        mazePlayer = PointF(dp(40f), dp(225f))
        mazeGoal = PointF(w - dp(42f), height - dp(55f))
        mazeWalls.clear()

        mazeWalls += RectF(w * 0.20f, dp(205f), w * 0.27f, height * 0.62f)
        mazeWalls += RectF(w * 0.39f, height * 0.38f, w * 0.46f, height - dp(70f))
        mazeWalls += RectF(w * 0.58f, dp(205f), w * 0.65f, height * 0.62f)
        mazeWalls += RectF(w * 0.77f, height * 0.38f, w * 0.84f, height - dp(70f))
    }

    private fun drawMaze(canvas: Canvas) {
        paint.color = 0xFF8C6C5D.toInt()
        for (wall in mazeWalls) canvas.drawRoundRect(wall, dp(8f), dp(8f), paint)
        drawStar(canvas, mazePlayer.x, mazePlayer.y, dp(20f), 0xFFFFC107.toInt())
        drawTreasure(canvas, mazeGoal.x, mazeGoal.y, dp(34f))
    }

    // -------------------------- 連連看 --------------------------

    private fun prepareMatch() {
        questionText = "找出一樣的圖案，拖曳或點選把它們連起來。"
        matchCards.clear()
        matchedPairIds.clear()
        matchDragStart = -1
        matchTapSelected = -1

        val labels = listOf("蘋果", "青蛙", "熊貓", "香蕉")
        for (id in 0..3) {
            matchCards += MatchCard(id, 0, labels[id])
        }
        val rightOrder = (0..3).shuffled()
        for (id in rightOrder) {
            matchCards += MatchCard(id, 1, labels[id])
        }
    }

    private fun drawMatch(canvas: Canvas) {
        val w = width.toFloat()
        val top = dp(210f)
        val bottom = height - dp(70f)
        val rowGap = dp(12f)
        val cardW = min(dp(128f), w * 0.34f)
        val cardH = min(dp(112f), (bottom - top - rowGap * 3f) / 4f)
        val leftX = dp(20f)
        val rightX = w - dp(20f) - cardW

        // 先配置大卡片位置
        for (i in 0 until 4) {
            val y = top + i * (cardH + rowGap)
            matchCards[i].rect = RectF(leftX, y, leftX + cardW, y + cardH)
            matchCards[4 + i].rect = RectF(rightX, y, rightX + cardW, y + cardH)
        }

        // 已成功配對的永久連線
        val lineColors = intArrayOf(
            0xFFFF6B9A.toInt(), 0xFF4AA8FF.toInt(), 0xFFFFBE3C.toInt(), 0xFF65C96F.toInt()
        )
        linePaint.strokeWidth = dp(7f)
        for (pairId in matchedPairIds) {
            val a = matchCards.first { it.pairId == pairId && it.side == 0 }.rect
            val b = matchCards.first { it.pairId == pairId && it.side == 1 }.rect
            linePaint.color = lineColors[pairId % lineColors.size]
            canvas.drawLine(a.right, a.centerY(), b.left, b.centerY(), linePaint)
        }

        // 拖曳中的即時連線
        if (matchDragStart >= 0 && matchDragStart < matchCards.size) {
            val start = matchCards[matchDragStart].rect
            linePaint.strokeWidth = dp(6f)
            linePaint.color = 0xFF7B73F0.toInt()
            val sx = if (matchCards[matchDragStart].side == 0) start.right else start.left
            canvas.drawLine(sx, start.centerY(), matchDragPoint.x, matchDragPoint.y, linePaint)
        }

        // 大卡片
        for (i in matchCards.indices) {
            val card = matchCards[i]
            val selected = i == matchTapSelected || i == matchDragStart
            val matched = card.pairId in matchedPairIds

            paint.color = when {
                matched -> 0xFFE9F9E7.toInt()
                selected -> 0xFFFFF0B9.toInt()
                else -> Color.WHITE
            }
            paint.setShadowLayer(dp(3f), 0f, dp(2f), 0x22000000)
            canvas.drawRoundRect(card.rect, dp(20f), dp(20f), paint)
            paint.clearShadowLayer()

            if (selected || matched) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3f)
                paint.color = if (matched) 0xFF62BE69.toInt() else 0xFFFFA62E.toInt()
                canvas.drawRoundRect(card.rect, dp(20f), dp(20f), paint)
                paint.style = Paint.Style.FILL
            }

            drawMatchIcon(canvas, card.rect.centerX(), card.rect.centerY() - dp(7f), dp(27f), card.pairId)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = sp(15f)
            textPaint.color = 0xFF57473D.toInt()
            canvas.drawText(card.label, card.rect.centerX(), card.rect.bottom - dp(10f), textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(17f)
        textPaint.color = 0xFF6D5A4A.toInt()
        val done = matchedPairIds.size
        canvas.drawText("進度：$done / 4", w / 2f, height - dp(25f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMatchIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, id: Int) {
        when (id) {
            0 -> drawApple(canvas, cx, cy, s)
            1 -> { // 青蛙
                paint.color = 0xFF72C85D.toInt()
                canvas.drawCircle(cx, cy, s, paint)
                canvas.drawCircle(cx - s * 0.55f, cy - s * 0.65f, s * 0.40f, paint)
                canvas.drawCircle(cx + s * 0.55f, cy - s * 0.65f, s * 0.40f, paint)
                drawPetEyes(canvas, cx, cy - s * 0.12f, s)
            }
            2 -> { // 熊貓
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, s, paint)
                paint.color = 0xFF2D2D2D.toInt()
                canvas.drawCircle(cx - s * 0.68f, cy - s * 0.70f, s * 0.36f, paint)
                canvas.drawCircle(cx + s * 0.68f, cy - s * 0.70f, s * 0.36f, paint)
                canvas.drawOval(cx - s * 0.58f, cy - s * 0.30f, cx - s * 0.15f, cy + s * 0.20f, paint)
                canvas.drawOval(cx + s * 0.15f, cy - s * 0.30f, cx + s * 0.58f, cy + s * 0.20f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx - s * 0.36f, cy - s * 0.04f, s * 0.10f, paint)
                canvas.drawCircle(cx + s * 0.36f, cy - s * 0.04f, s * 0.10f, paint)
            }
            else -> { // 香蕉
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.40f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = 0xFFFFC83D.toInt()
                val path = Path()
                path.moveTo(cx - s * 0.62f, cy - s * 0.48f)
                path.quadTo(cx - s * 0.05f, cy + s * 0.70f, cx + s * 0.70f, cy - s * 0.10f)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    // -------------------------- 數一數 --------------------------

    private fun prepareCount() {
        countTarget = Random.nextInt(3, 11)
        countOptions = listOf(max(1, countTarget - 1), countTarget, countTarget + 1).shuffled()
        questionText = "請數一數，畫面上有幾顆蘋果？"
    }

    private fun drawCount(canvas: Canvas) {
        val w = width.toFloat()
        val cols = 4
        val left = dp(55f)
        val right = w - dp(55f)
        val dx = (right - left) / 3f
        val top = dp(245f)
        val dy = dp(78f)

        for (i in 0 until countTarget) {
            val c = i % cols
            val r = i / cols
            drawApple(canvas, left + c * dx, top + r * dy, dp(23f))
        }

        answerRects.clear()
        val y = height - dp(150f)
        val gap = dp(12f)
        val optionW = (w - dp(36f) * 2f - gap * 2f) / 3f
        for (i in countOptions.indices) {
            val leftX = dp(36f) + i * (optionW + gap)
            val rect = RectF(leftX, y, leftX + optionW, y + dp(96f))
            paint.color = 0xFFDDEEFF.toInt()
            paint.setShadowLayer(dp(3f), 0f, dp(2f), 0x22000000)
            canvas.drawRoundRect(rect, dp(22f), dp(22f), paint)
            paint.clearShadowLayer()

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = 0xFF3F4C56.toInt()
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = sp(34f)
            canvas.drawText(countOptions[i].toString(), rect.centerX(), rect.centerY() + sp(11f), textPaint)
            answerRects += RectF(rect) to countOptions[i]
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // -------------------------- 數學 --------------------------

    private fun prepareMath() {
        val maxNum = when (difficulty) {
            1 -> 10
            2 -> 15
            else -> 20
        }
        mathOp = if (Random.nextBoolean()) "+" else "−"
        if (mathOp == "+") {
            mathA = Random.nextInt(1, maxNum / 2 + 1)
            mathB = Random.nextInt(1, maxNum / 2 + 1)
            mathAnswer = mathA + mathB
        } else {
            mathA = Random.nextInt(2, maxNum + 1)
            mathB = Random.nextInt(1, mathA)
            mathAnswer = mathA - mathB
        }

        val candidates = mutableSetOf(mathAnswer)
        while (candidates.size < 3) {
            candidates += max(0, mathAnswer + Random.nextInt(-3, 4))
        }
        mathOptions = candidates.toList().shuffled()

        val opWord = if (mathOp == "+") "加" else "減"
        questionText = "請算算看，$mathA $opWord $mathB 等於多少？"
    }

    private fun drawMath(canvas: Canvas) {
        val w = width.toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF493F39.toInt()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(43f)
        canvas.drawText("$mathA  $mathOp  $mathB  =  ?", w / 2f, dp(290f), textPaint)

        answerRects.clear()
        val top = dp(345f)
        val gap = dp(16f)
        val cardH = min(dp(112f), (height - top - dp(40f) - gap * 2f) / 3f)
        for (i in mathOptions.indices) {
            val rect = RectF(dp(35f), top + i * (cardH + gap), w - dp(35f), top + i * (cardH + gap) + cardH)
            paint.color = 0xFFFFEED1.toInt()
            paint.setShadowLayer(dp(3f), 0f, dp(2f), 0x22000000)
            canvas.drawRoundRect(rect, dp(24f), dp(24f), paint)
            paint.clearShadowLayer()
            textPaint.textSize = sp(34f)
            canvas.drawText(mathOptions[i].toString(), rect.centerX(), rect.centerY() + sp(11f), textPaint)
            answerRects += RectF(rect) to mathOptions[i]
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // -------------------------- 記憶 --------------------------

    private fun prepareMemory() {
        memorySequence = MutableList(3 + difficulty) { Random.nextInt(0, 4) }
        memoryInput.clear()
        memoryShowing = true
        questionText = "先記住亮起來的順序。"

        handler.postDelayed({
            memoryShowing = false
            questionText = "請照剛才的順序點顏色方塊。"
            invalidate()
            scheduleReadQuestion()
        }, 2400L)
    }

    private fun drawMemory(canvas: Canvas) {
        val w = width.toFloat()
        val centers = listOf(
            PointF(w * 0.30f, dp(325f)),
            PointF(w * 0.70f, dp(325f)),
            PointF(w * 0.30f, dp(535f)),
            PointF(w * 0.70f, dp(535f))
        )
        val colors = intArrayOf(
            0xFFFF6B6B.toInt(), 0xFF4D8FE8.toInt(),
            0xFF63B66B.toInt(), 0xFFFFC24B.toInt()
        )

        answerRects.clear()
        for (i in 0..3) {
            val r = RectF(
                centers[i].x - dp(68f), centers[i].y - dp(68f),
                centers[i].x + dp(68f), centers[i].y + dp(68f)
            )
            paint.color = if (memoryShowing && memorySequence.contains(i)) lighten(colors[i]) else colors[i]
            canvas.drawRoundRect(r, dp(28f), dp(28f), paint)
            answerRects += RectF(r) to i

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = sp(28f)
            textPaint.color = Color.WHITE
            canvas.drawText((i + 1).toString(), r.centerX(), r.centerY() + sp(9f), textPaint)
        }

        textPaint.textSize = sp(18f)
        textPaint.color = 0xFF655348.toInt()
        val status = if (memoryShowing) {
            "記住 ${memorySequence.size} 個順序"
        } else {
            "已完成 ${memoryInput.size} / ${memorySequence.size}"
        }
        canvas.drawText(status, w / 2f, dp(650f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    // -------------------------- 觸控 --------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // 首頁按鈕在 ACTION_DOWN 就回應，避免「按了沒反應」。
        if (screen == Screen.HOME) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val hit = homeGameRects.firstOrNull { hitRect(it.first, x, y, dp(6f)) }
                if (hit != null) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    enterGame(hit.second)
                    performClick()
                }
            }
            return true
        }

        // 遊戲共用按鈕
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (hitRect(backRect, x, y, dp(10f))) {
                tts.stop()
                handler.removeCallbacksAndMessages(null)
                screen = Screen.HOME
                resetGameState()
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                performClick()
                return true
            }
            if (hitRect(replayRect, x, y, dp(10f))) {
                speakQuestion()
                showFeedback("再聽一次 🔊", false)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                performClick()
                return true
            }
        }

        when (screen) {
            Screen.MAZE -> handleMazeTouch(event)
            Screen.MATCH -> handleMatchTouch(event)
            else -> if (event.action == MotionEvent.ACTION_DOWN) {
                when (screen) {
                    Screen.FIND -> handleFindTap(x, y)
                    Screen.SAME -> handleBinaryAnswer(x, y) { prepareSame() }
                    Screen.COLOR_SHAPE -> handleColorShapeTap(x, y)
                    Screen.COUNT -> handleCountTap(x, y)
                    Screen.MATH -> handleMathTap(x, y)
                    Screen.MEMORY -> handleMemoryTap(x, y)
                    else -> Unit
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitRect(r: RectF, x: Float, y: Float, pad: Float): Boolean {
        return x >= r.left - pad && x <= r.right + pad &&
            y >= r.top - pad && y <= r.bottom + pad
    }

    private fun hitAnswer(x: Float, y: Float): Pair<RectF, Int>? {
        val pad = dp(9f)
        return answerRects.firstOrNull { hitRect(it.first, x, y, pad) }
    }

    private fun handleFindTap(x: Float, y: Float) {
        val idx = bubbles.indexOfFirst {
            hypot((it.x - x).toDouble(), (it.y - y).toDouble()) <= max(it.r * 1.7f, dp(32f))
        }
        if (idx < 0) {
            showFeedback("點圖形看看 👆", false)
            return
        }

        if (bubbles[idx].kind == targetKind) {
            bubbles.removeAt(idx)
            foundCount++
            success()
            if (foundCount >= targetCount) {
                nextQuestion(Screen.FIND, 700L)
            }
        } else {
            retry()
        }
        invalidate()
    }

    private fun handleBinaryAnswer(x: Float, y: Float, nextQuestion: () -> Unit) {
        val hit = hitAnswer(x, y)
        if (hit == null) {
            showFeedback("請點選一個大方格", false)
            return
        }

        if (hit.second == 1) {
            success()
            handler.postDelayed({
                resetGameState()
                nextQuestion()
                invalidate()
                scheduleReadQuestion()
            }, 650L)
        } else {
            retry()
            invalidate()
        }
    }

    private fun handleColorShapeTap(x: Float, y: Float) {
        val idx = bubbles.indexOfFirst {
            hypot((it.x - x).toDouble(), (it.y - y).toDouble()) <= max(it.r * 1.7f, dp(32f))
        }
        if (idx < 0) {
            showFeedback("點圖形看看 👆", false)
            return
        }

        val b = bubbles[idx]
        if (b.kind == 0 && b.color == 0xFF4D8FE8.toInt()) {
            bubbles.removeAt(idx)
            success()
            if (bubbles.none { it.kind == 0 && it.color == 0xFF4D8FE8.toInt() }) {
                nextQuestion(Screen.COLOR_SHAPE, 700L)
            }
        } else {
            retry()
        }
        invalidate()
    }

    private fun handleMazeTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mazeDragging = hypot(
                    (mazePlayer.x - event.x).toDouble(),
                    (mazePlayer.y - event.y).toDouble()
                ) <= dp(45f)
                if (!mazeDragging) showFeedback("從星星開始拖曳 ⭐", false)
            }
            MotionEvent.ACTION_MOVE -> if (mazeDragging) {
                val x = event.x.coerceIn(dp(22f), width - dp(22f))
                val y = event.y.coerceIn(dp(190f), height - dp(22f))
                val candidate = RectF(x - dp(18f), y - dp(18f), x + dp(18f), y + dp(18f))
                val blocked = mazeWalls.any { RectF.intersects(it, candidate) }
                if (!blocked) {
                    mazePlayer.x = x
                    mazePlayer.y = y
                    if (hypot((x - mazeGoal.x).toDouble(), (y - mazeGoal.y).toDouble()) < dp(48f)) {
                        mazeDragging = false
                        success("找到寶箱！")
                        nextQuestion(Screen.MAZE, 800L)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mazeDragging = false
        }
    }

    private fun handleMatchTouch(event: MotionEvent) {
        val x = event.x
        val y = event.y

        fun cardAt(px: Float, py: Float): Int {
            val pad = dp(12f)
            return matchCards.indexOfFirst { card ->
                card.pairId !in matchedPairIds && hitRect(card.rect, px, py, pad)
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val idx = cardAt(x, y)
                if (idx >= 0) {
                    matchDragStart = idx
                    matchDragPoint.set(x, y)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    invalidate()
                } else {
                    showFeedback("從大方格開始連線", false)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (matchDragStart >= 0) {
                    matchDragPoint.set(x, y)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (matchDragStart < 0) return
                val start = matchDragStart
                val end = cardAt(x, y)
                val moved = hypot(
                    (matchCards[start].rect.centerX() - x).toDouble(),
                    (matchCards[start].rect.centerY() - y).toDouble()
                ) > dp(35f)

                if (end >= 0 && end != start && moved) {
                    tryMatch(start, end)
                    matchTapSelected = -1
                } else {
                    // 點一下也可選，再點另一張完成配對
                    if (matchTapSelected < 0) {
                        matchTapSelected = start
                        showFeedback("已選取，請再點相同圖案", false)
                    } else if (matchTapSelected == start) {
                        matchTapSelected = -1
                    } else {
                        tryMatch(matchTapSelected, start)
                        matchTapSelected = -1
                    }
                }

                matchDragStart = -1
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                matchDragStart = -1
                invalidate()
            }
        }
    }

    private fun tryMatch(aIndex: Int, bIndex: Int) {
        if (aIndex !in matchCards.indices || bIndex !in matchCards.indices) return
        val a = matchCards[aIndex]
        val b = matchCards[bIndex]

        if (a.side != b.side && a.pairId == b.pairId) {
            matchedPairIds += a.pairId
            success("連對了！")
            if (matchedPairIds.size == 4) {
                handler.postDelayed({ enterGame(Screen.MATCH) }, 1000L)
            }
        } else {
            retry("不是這一對，再看看～")
        }
    }

    private fun handleCountTap(x: Float, y: Float) {
        val hit = hitAnswer(x, y)
        if (hit == null) {
            showFeedback("請點下面的大數字", false)
            return
        }
        if (hit.second == countTarget) {
            success()
            nextQuestion(Screen.COUNT, 650L)
        } else {
            retry()
        }
        invalidate()
    }

    private fun handleMathTap(x: Float, y: Float) {
        val hit = hitAnswer(x, y)
        if (hit == null) {
            showFeedback("請點一個答案", false)
            return
        }
        if (hit.second == mathAnswer) {
            success()
            nextQuestion(Screen.MATH, 650L)
        } else {
            retry()
        }
        invalidate()
    }

    private fun handleMemoryTap(x: Float, y: Float) {
        if (memoryShowing) {
            showFeedback("先記住，等一下再點喔！", false)
            return
        }

        val hit = hitAnswer(x, y)
        if (hit == null) {
            showFeedback("請點彩色大方格", false)
            return
        }

        memoryInput.add(hit.second)
        val idx = memoryInput.lastIndex

        if (memorySequence[idx] != hit.second) {
            retry()
            nextQuestion(Screen.MEMORY, 750L)
        } else if (memoryInput.size == memorySequence.size) {
            success("記憶成功！")
            if (difficulty < 3) {
                difficulty++
                prefs.edit().putInt("difficulty", difficulty).apply()
            }
            nextQuestion(Screen.MEMORY, 900L)
        } else {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            showFeedback("很好！繼續～", false)
        }
        invalidate()
    }

    private fun nextQuestion(target: Screen, delay: Long) {
        handler.postDelayed({ enterGame(target) }, delay)
    }

    // -------------------------- 回饋與獎勵 --------------------------

    private fun success(text: String = "答對了！") {
        stars++
        gamesPlayed++
        prefs.edit().putInt("stars", stars).putInt("games_played", gamesPlayed).apply()
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 140)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showFeedback("$text ⭐ +1", true)
    }

    private fun retry(text: String = "再試一次～") {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 130)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showFeedback(text, false)
    }

    private fun showFeedback(text: String, celebrate: Boolean) {
        feedbackText = text
        feedbackUntil = System.currentTimeMillis() + 1400L
        if (celebrate) celebrateUntil = System.currentTimeMillis() + 900L
        invalidate()
        handler.postDelayed({ invalidate() }, 1500L)
    }

    private fun drawFeedback(canvas: Canvas) {
        if (feedbackText.isBlank() || System.currentTimeMillis() > feedbackUntil) return
        val w = width.toFloat()
        val r = RectF(dp(32f), height - dp(78f), w - dp(32f), height - dp(22f))
        paint.color = 0xFFFFF6D8.toInt()
        paint.setShadowLayer(dp(4f), 0f, dp(2f), 0x22000000)
        canvas.drawRoundRect(r, dp(22f), dp(22f), paint)
        paint.clearShadowLayer()

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = sp(19f)
        textPaint.color = 0xFF745638.toInt()
        canvas.drawText(feedbackText, r.centerX(), r.centerY() + dp(7f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCelebration(canvas: Canvas) {
        if (System.currentTimeMillis() > celebrateUntil) return
        val w = width.toFloat()
        val h = height.toFloat()
        val points = listOf(
            PointF(w * 0.12f, h * 0.24f), PointF(w * 0.88f, h * 0.25f),
            PointF(w * 0.18f, h * 0.48f), PointF(w * 0.82f, h * 0.52f),
            PointF(w * 0.28f, h * 0.70f), PointF(w * 0.72f, h * 0.72f)
        )
        for ((i, p) in points.withIndex()) {
            drawStar(canvas, p.x, p.y, dp(if (i % 2 == 0) 12f else 9f),
                if (i % 2 == 0) 0xFFFFC107.toInt() else 0xFFFF7A98.toInt())
        }
    }

    private fun resetGameState() {
        bubbles.clear()
        answerRects.clear()
        mazeWalls.clear()
        memorySequence.clear()
        memoryInput.clear()
        matchCards.clear()
        matchedPairIds.clear()
        matchDragStart = -1
        matchTapSelected = -1
        foundCount = 0
        mazeDragging = false
    }

    // -------------------------- 繪圖工具 --------------------------

    private fun drawProgressBar(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, progress: Float) {
        paint.color = 0xFFE4DFC9.toInt()
        canvas.drawRoundRect(l, t, r, b, dp(8f), dp(8f), paint)
        paint.color = 0xFF7FCB3C.toInt()
        canvas.drawRoundRect(l, t, l + (r - l) * progress.coerceIn(0f, 1f), b, dp(8f), dp(8f), paint)
    }

    private fun drawTileIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, icon: Int) {
        paint.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(cx, cy, s * 1.25f, paint)

        when (icon) {
            0 -> { // 放大鏡
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3f)
                paint.color = 0xFF4892B8.toInt()
                canvas.drawCircle(cx - s * 0.18f, cy - s * 0.12f, s * 0.55f, paint)
                canvas.drawLine(cx + s * 0.20f, cy + s * 0.28f, cx + s * 0.75f, cy + s * 0.82f, paint)
                paint.style = Paint.Style.FILL
            }
            1 -> {
                paint.color = 0xFFF05B66.toInt()
                canvas.drawCircle(cx - s * 0.35f, cy, s * 0.45f, paint)
                canvas.drawCircle(cx + s * 0.35f, cy, s * 0.45f, paint)
            }
            2 -> {
                paint.color = 0xFF7866D9.toInt()
                canvas.drawCircle(cx - s * 0.40f, cy, s * 0.35f, paint)
                paint.color = 0xFFFFB83E.toInt()
                canvas.drawRect(cx, cy - s * 0.35f, cx + s * 0.65f, cy + s * 0.30f, paint)
            }
            3 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(4f)
                paint.color = 0xFF4C78CF.toInt()
                canvas.drawRect(cx - s * 0.65f, cy - s * 0.65f, cx + s * 0.65f, cy + s * 0.65f, paint)
                canvas.drawLine(cx - s * 0.30f, cy - s * 0.65f, cx - s * 0.30f, cy + s * 0.20f, paint)
                canvas.drawLine(cx - s * 0.30f, cy + s * 0.20f, cx + s * 0.35f, cy + s * 0.20f, paint)
                paint.style = Paint.Style.FILL
            }
            4 -> {
                paint.color = 0xFFE75B82.toInt()
                canvas.drawCircle(cx - s * 0.55f, cy - s * 0.30f, s * 0.22f, paint)
                canvas.drawCircle(cx + s * 0.55f, cy + s * 0.30f, s * 0.22f, paint)
                linePaint.strokeWidth = dp(4f)
                linePaint.color = 0xFF7C63D5.toInt()
                canvas.drawLine(cx - s * 0.35f, cy - s * 0.18f, cx + s * 0.35f, cy + s * 0.18f, linePaint)
            }
            5 -> {
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = sp(20f)
                textPaint.color = 0xFF3E8C87.toInt()
                canvas.drawText("123", cx, cy + sp(7f), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
            6 -> {
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = sp(18f)
                textPaint.color = 0xFF3E77C8.toInt()
                canvas.drawText("1+2", cx, cy + sp(6f), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
            else -> {
                paint.color = 0xFFE46D8C.toInt()
                canvas.drawCircle(cx - s * 0.28f, cy, s * 0.50f, paint)
                canvas.drawCircle(cx + s * 0.28f, cy, s * 0.50f, paint)
                canvas.drawCircle(cx, cy - s * 0.32f, s * 0.38f, paint)
            }
        }
    }

    private fun drawFlower(canvas: Canvas, cx: Float, cy: Float, radius: Float, petals: Int, color: Int) {
        paint.color = color
        for (i in 0 until petals) {
            val a = Math.PI * 2 * i / petals
            val px = cx + cos(a).toFloat() * radius
            val py = cy + sin(a).toFloat() * radius
            canvas.drawCircle(px, py, radius * 0.34f, paint)
        }
        paint.color = 0xFFFFD166.toInt()
        canvas.drawCircle(cx, cy, radius * 0.45f, paint)
    }

    private fun lighten(color: Int): Int {
        return Color.rgb(
            min(255, Color.red(color) + 80),
            min(255, Color.green(color) + 80),
            min(255, Color.blue(color) + 80)
        )
    }

    // -------- 三位透明背景小老師 --------

    private fun drawBearTeacher(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = 0xFFF4D2A7.toInt()
        val ear = Path().apply {
            moveTo(cx - s * 0.62f, cy - s * 0.25f)
            lineTo(cx - s * 0.25f, cy - s * 0.85f)
            lineTo(cx - s * 0.06f, cy - s * 0.18f)
            close()
        }
        canvas.drawPath(ear, paint)
        val ear2 = Path().apply {
            moveTo(cx + s * 0.62f, cy - s * 0.25f)
            lineTo(cx + s * 0.25f, cy - s * 0.85f)
            lineTo(cx + s * 0.06f, cy - s * 0.18f)
            close()
        }
        canvas.drawPath(ear2, paint)
        canvas.drawOval(cx - s * 0.60f, cy - s * 0.52f, cx + s * 0.60f, cy + s * 0.62f, paint)
        paint.color = 0xFFFFE7C7.toInt()
        canvas.drawOval(cx - s * 0.38f, cy, cx + s * 0.38f, cy + s * 0.52f, paint)
        drawPetEyes(canvas, cx, cy - s * 0.08f, s)
        paint.color = 0xFF4A352D.toInt()
        canvas.drawCircle(cx, cy + s * 0.16f, s * 0.09f, paint)
        drawTeacherBow(canvas, cx, cy + s * 0.63f, s * 0.23f, 0xFF5B8DEF.toInt())
    }

    private fun drawBrownSugarTeacher(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        // 黑糖：玳瑁自然混色，避免左右切半臉。
        paint.color = 0xFF43342F.toInt()
        drawCatHead(canvas, cx, cy, s)
        paint.color = 0xFFC67838.toInt()
        canvas.drawOval(cx - s * 0.45f, cy - s * 0.34f, cx - s * 0.02f, cy + s * 0.09f, paint)
        canvas.drawOval(cx + s * 0.08f, cy + s * 0.03f, cx + s * 0.45f, cy + s * 0.36f, paint)
        paint.color = 0xFFE4A14D.toInt()
        canvas.drawOval(cx - s * 0.12f, cy - s * 0.47f, cx + s * 0.19f, cy - s * 0.12f, paint)
        canvas.drawOval(cx - s * 0.48f, cy + s * 0.18f, cx - s * 0.20f, cy + s * 0.46f, paint)
        drawPetEyes(canvas, cx, cy - s * 0.04f, s)
        paint.color = 0xFF5B3C35.toInt()
        canvas.drawCircle(cx, cy + s * 0.19f, s * 0.07f, paint)
        drawTeacherBow(canvas, cx, cy + s * 0.62f, s * 0.23f, 0xFFE1A22D.toInt())
    }

    private fun drawOguiTeacher(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = 0xFFB79B78.toInt()
        drawCatHead(canvas, cx, cy, s)
        paint.color = 0xFF645548.toInt()
        paint.strokeWidth = s * 0.07f
        paint.style = Paint.Style.STROKE
        for (offset in listOf(-0.26f, 0f, 0.26f)) {
            canvas.drawLine(cx + s * offset, cy - s * 0.50f, cx + s * offset * 0.65f, cy - s * 0.22f, paint)
        }
        paint.style = Paint.Style.FILL
        drawPetEyes(canvas, cx, cy - s * 0.04f, s)
        paint.color = 0xFF6A5043.toInt()
        canvas.drawCircle(cx, cy + s * 0.19f, s * 0.07f, paint)
        drawTeacherBow(canvas, cx, cy + s * 0.62f, s * 0.23f, 0xFF57B99D.toInt())
    }

    private fun drawCatHead(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val leftEar = Path().apply {
            moveTo(cx - s * 0.55f, cy - s * 0.28f)
            lineTo(cx - s * 0.30f, cy - s * 0.78f)
            lineTo(cx - s * 0.07f, cy - s * 0.40f)
            close()
        }
        canvas.drawPath(leftEar, paint)
        val rightEar = Path().apply {
            moveTo(cx + s * 0.55f, cy - s * 0.28f)
            lineTo(cx + s * 0.30f, cy - s * 0.78f)
            lineTo(cx + s * 0.07f, cy - s * 0.40f)
            close()
        }
        canvas.drawPath(rightEar, paint)
        canvas.drawOval(cx - s * 0.58f, cy - s * 0.50f, cx + s * 0.58f, cy + s * 0.60f, paint)
    }

    private fun drawPetEyes(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = Color.WHITE
        canvas.drawOval(cx - s * 0.34f, cy - s * 0.13f, cx - s * 0.10f, cy + s * 0.10f, paint)
        canvas.drawOval(cx + s * 0.10f, cy - s * 0.13f, cx + s * 0.34f, cy + s * 0.10f, paint)
        paint.color = 0xFF3E302A.toInt()
        canvas.drawCircle(cx - s * 0.22f, cy, s * 0.075f, paint)
        canvas.drawCircle(cx + s * 0.22f, cy, s * 0.075f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx - s * 0.195f, cy - s * 0.025f, s * 0.022f, paint)
        canvas.drawCircle(cx + s * 0.245f, cy - s * 0.025f, s * 0.022f, paint)
    }

    private fun drawTeacherBow(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.color = color
        val left = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - s, cy - s * 0.52f)
            lineTo(cx - s, cy + s * 0.52f)
            close()
        }
        val right = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + s, cy - s * 0.52f)
            lineTo(cx + s, cy + s * 0.52f)
            close()
        }
        canvas.drawPath(left, paint)
        canvas.drawPath(right, paint)
        canvas.drawCircle(cx, cy, s * 0.30f, paint)
    }

    private fun drawApple(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.color = 0xFFEF5350.toInt()
        canvas.drawCircle(cx - r * 0.25f, cy, r * 0.72f, paint)
        canvas.drawCircle(cx + r * 0.25f, cy, r * 0.72f, paint)
        canvas.drawOval(cx - r * 0.62f, cy - r * 0.05f, cx + r * 0.62f, cy + r * 0.88f, paint)
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(cx - dp(2f), cy - r * 0.92f, cx + dp(2f), cy - r * 0.55f, paint)
        paint.color = 0xFF66BB6A.toInt()
        canvas.drawOval(cx + dp(2f), cy - r * 0.95f, cx + r * 0.48f, cy - r * 0.62f, paint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val p = Path()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val r = if (i % 2 == 0) radius else radius * 0.45f
            val x = cx + cos(a).toFloat() * r
            val y = cy + sin(a).toFloat() * r
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        paint.color = color
        canvas.drawPath(p, paint)
    }

    private fun drawTreasure(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        paint.color = 0xFF8D5A2B.toInt()
        canvas.drawRoundRect(cx - s * 0.65f, cy - s * 0.38f, cx + s * 0.65f, cy + s * 0.45f, dp(7f), dp(7f), paint)
        paint.color = 0xFFFFC44D.toInt()
        canvas.drawRect(cx - s * 0.08f, cy - s * 0.38f, cx + s * 0.08f, cy + s * 0.45f, paint)
        canvas.drawCircle(cx, cy + s * 0.05f, s * 0.10f, paint)
    }
}
