package com.boga.kidgame

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class GameType(val title: String, val emoji: String, val subtitle: String) {
        FIND("找一找", "🔍", "專注搜尋"),
        SAME("找一樣", "🧩", "觀察細節"),
        COLOR("顏色圖形", "🎨", "辨識形狀"),
        MAZE("迷宮", "🌀", "手眼協調"),
        MATCH("連連看", "🔗", "拖曳配對"),
        COUNT("數一數", "🔢", "數量概念"),
        MATH("數學小高手", "➕", "基本加減法"),
        MEMORY("記憶挑戰", "🧠", "短期記憶")
    }

    private sealed class Screen {
        object Home : Screen()
        data class Game(val game: GameType) : Screen()
        object Achievements : Screen()
        object EnglishHome : Screen()
        data class EnglishWords(val category: String? = null) : Screen()
        object EnglishQuiz : Screen()
        object EnglishListening : Screen()
        object EnglishPronunciation : Screen()
    }

    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
    private val prefs by lazy { getSharedPreferences("kid_game_v04", Context.MODE_PRIVATE) }

    private var stars = 0
    private var gamesPlayed = 0
    private var difficulty = 1
    private var currentQuestion = ""
    private val screenStack = mutableListOf<Screen>()
    private var englishAccent = Locale.US
    private var speechRecognizer: SpeechRecognizer? = null
    private var pronunciationTarget: String = ""
    private var pronunciationResultView: TextView? = null
    private var pronunciationScoreView: TextView? = null
    private var pronunciationStatusView: TextView? = null

    private val bg = Color.rgb(255, 248, 234)
    private val brown = Color.rgb(86, 64, 48)
    private val coral = Color.rgb(238, 79, 104)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stars = prefs.getInt("stars", 0)
        gamesPlayed = prefs.getInt("games", 0)
        difficulty = prefs.getInt("difficulty", 1).coerceIn(1, 3)
        tts = TextToSpeech(this, this)

        root = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }
        setContentView(root)

        screenStack.clear()
        screenStack.add(Screen.Home)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackOnePage()
            }
        })
        renderScreen(Screen.Home)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tone.release()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.TAIWAN)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.86f)
            tts.setPitch(1.03f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "pronunciation_demo") {
                        handler.postDelayed({ beginPronunciationRecognition() }, 500L)
                    }
                }
            })
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int, radius: Int = 22, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (strokeColor != null && strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }

    private fun text(
        value: String,
        size: Float,
        color: Int = brown,
        bold: Boolean = false,
        gravity: Int = Gravity.CENTER
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        this.gravity = gravity
        setPadding(dp(6), dp(4), dp(6), dp(4))
        includeFontPadding = false
        typeface = android.graphics.Typeface.create(
            "sans-serif-rounded",
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }

    private fun LinearLayout.addSpace(height: Int) {
        addView(Space(this@MainActivity), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun saveProgress() {
        prefs.edit()
            .putInt("stars", stars)
            .putInt("games", gamesPlayed)
            .putInt("difficulty", difficulty)
            .apply()
    }

    private fun renderScreen(screen: Screen) {
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) tts.stop()
        speechRecognizer?.cancel()
        if (screen != Screen.EnglishPronunciation) pronunciationTarget = ""
        pronunciationResultView = null
        pronunciationScoreView = null
        pronunciationStatusView = null
        when (screen) {
            Screen.Home -> showHome()
            is Screen.Game -> showGame(screen.game)
            Screen.Achievements -> showAchievements()
            Screen.EnglishHome -> showEnglishHome()
            is Screen.EnglishWords -> showEnglishWords(screen.category)
            Screen.EnglishQuiz -> showEnglishQuiz()
            Screen.EnglishListening -> showEnglishListening()
            Screen.EnglishPronunciation -> showEnglishPronunciation()
        }
    }

    private fun navigateTo(screen: Screen) {
        if (screenStack.lastOrNull() != screen) screenStack.add(screen)
        renderScreen(screen)
    }

    private fun goBackOnePage() {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.lastIndex)
            renderScreen(screenStack.last())
        } else {
            finish()
        }
    }

    // ------------------------- HOME -------------------------

    private fun showHome() {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFF8E9.toInt())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(28))
        }
        scroll.addView(content, ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll)

        // 1. 頂端資料列：完全依預覽圖比例重新排版
        content.addView(makeTopStats(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(78)
        ))
        content.addSpace(10)

        // 2. 直接顯示任務卡，不再使用額外大標題
        val missionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        missionRow.addView(
            makeMissionCard(R.drawable.mission_calendar, "每日任務", "${min(gamesPlayed, 5)}/5", 0xFFF0F8D9.toInt(), 0xFF58A815.toInt()),
            LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginEnd = dp(5) }
        )
        missionRow.addView(
            makeMissionCard(R.drawable.mission_treasure, "星星寶箱", "${stars % 30}/30", 0xFFFFF1D4.toInt(), 0xFFE37500.toInt()),
            LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginStart = dp(5) }
        )
        content.addView(missionRow)
        content.addSpace(8)

        // 3. 三毛孩：直接採用本次核准預覽圖中的偶貴、黑糖、熊熊
        val pets = ImageView(this).apply {
            setImageResource(R.drawable.home_pets)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            background = rounded(0xFFFFF8E9.toInt(), 18)
            clipToOutline = true
            contentDescription = "偶貴老師、黑糖老師、熊熊老師"
        }
        content.addView(pets, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160)
        ))
        content.addSpace(8)

        val gameValues = GameType.values()
        for (row in 0 until 4) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            repeat(2) { col ->
                val index = row * 2 + col
                val tile = makeGameTile(gameValues[index])
                val lp = LinearLayout.LayoutParams(0, dp(88), 1f)
                if (col == 0) lp.marginEnd = dp(5) else lp.marginStart = dp(5)
                line.addView(tile, lp)
            }
            content.addView(line)
            if (row < 3) content.addSpace(9)
        }

        content.addSpace(10)

        val shapeQuick = text("⭐　點圖形看看　☝", 21f, brown, true).apply {
            background = rounded(0xFFFFF0B7.toInt(), 24)
            elevation = dp(2).toFloat()
            setOnClickListener { navigateTo(Screen.Game(GameType.COLOR)) }
        }
        content.addView(shapeQuick, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ))

        content.addSpace(9)

        // 英文學習整合於安卓版；放在預覽主區塊下方，不破壞預覽版面
        val english = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(7))
            background = rounded(0xFFDFF3FF.toInt(), 24)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            addView(text("ABC", 24f, 0xFF2879CB.toInt(), true), LinearLayout.LayoutParams(dp(68), dp(48)))
            addView(text("英文小教室", 20f, brown, true, Gravity.START or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(text("›", 30f, 0xFF2879CB.toInt(), true), LinearLayout.LayoutParams(dp(38), dp(48)))
            setOnClickListener { navigateTo(Screen.EnglishHome) }
        }
        content.addView(english, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
        ))

        content.addSpace(9)

        val achievements = text("🏅 成就徽章　　已累積 ⭐ $stars", 17f, brown, true).apply {
            background = rounded(0xFFFFE9A9.toInt(), 22)
            setOnClickListener { navigateTo(Screen.Achievements) }
        }
        content.addView(achievements, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))
    }

    private fun makeTopStats(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.WHITE, 28)
            elevation = dp(4).toFloat()
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.kid_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xFFD9F4FF.toInt(), 50)
            clipToOutline = true
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(58), dp(58)))

        val name = text("小朋友", 24f, 0xFF4A2E1D.toInt(), true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 18, 24, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        card.addView(name, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(8) })

        card.addView(makeTopCounter(R.drawable.top_star, stars.toString()), LinearLayout.LayoutParams(dp(92), dp(58)))
        card.addView(makeTopCounter(R.drawable.top_coin, (stars * 10).toString()), LinearLayout.LayoutParams(dp(104), dp(58)))
        return card
    }

    private fun makeTopCounter(iconRes: Int, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val iv = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(text(value, 24f, 0xFF3B2417.toInt(), true), LinearLayout.LayoutParams(0, dp(48), 1f))
        }
    }

    private fun makeMissionCard(iconRes: Int, title: String, value: String, color: Int, valueColor: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color, 23)
            setPadding(dp(9), dp(7), dp(9), dp(7))
            val iv = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(iv, LinearLayout.LayoutParams(dp(62), dp(62)))
            val words = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, 0, 0)
                addView(text(title, 16f, 0xFF4A2E1D.toInt(), true, Gravity.START).apply {
                    maxLines = 1
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 13, 17, 1, TypedValue.COMPLEX_UNIT_SP)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(text(value, 27f, valueColor, true, Gravity.START).apply {
                    maxLines = 1
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f))
            }
            addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun gameIconRes(game: GameType): Int = when (game) {
        GameType.FIND -> R.drawable.game_find
        GameType.SAME -> R.drawable.game_same
        GameType.COLOR -> R.drawable.game_color
        GameType.MAZE -> R.drawable.game_maze
        GameType.MATCH -> R.drawable.game_match
        GameType.COUNT -> R.drawable.game_count
        GameType.MATH -> R.drawable.game_math
        GameType.MEMORY -> R.drawable.game_memory
    }

    private fun gameCardColor(game: GameType): Int = when (game) {
        GameType.FIND -> 0xFFF0F8DC.toInt()
        GameType.SAME -> 0xFFFFE9D7.toInt()
        GameType.COLOR -> 0xFFF0E3FF.toInt()
        GameType.MAZE -> 0xFFE4EFFF.toInt()
        GameType.MATCH -> 0xFFFFE2EC.toInt()
        GameType.COUNT -> 0xFFDDF5F3.toInt()
        GameType.MATH -> 0xFFE2EDFF.toInt()
        GameType.MEMORY -> 0xFFFFE9D4.toInt()
    }

    private fun gameSubtitleColor(game: GameType): Int = when (game) {
        GameType.FIND -> 0xFF4E9D18.toInt()
        GameType.SAME -> 0xFFE85A11.toInt()
        GameType.COLOR -> 0xFF7542E4.toInt()
        GameType.MAZE -> 0xFF276FD2.toInt()
        GameType.MATCH -> 0xFFE53C67.toInt()
        GameType.COUNT -> 0xFF078E94.toInt()
        GameType.MATH -> 0xFF296DD8.toInt()
        GameType.MEMORY -> 0xFFE45B13.toInt()
    }

    private fun makeGameTile(game: GameType): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(gameCardColor(game), 22, Color.WHITE, 2)
            elevation = dp(2).toFloat()
            setPadding(dp(8), dp(6), dp(9), dp(6))
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(this).apply {
            setImageResource(gameIconRes(game))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 50)
            clipToOutline = true
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(66), dp(66)))

        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, 0, 0)
        }
        val title = text(game.title, 20f, 0xFF4B2C18.toInt(), true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 14, 20, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val sub = text(game.subtitle, 14f, gameSubtitleColor(game), false, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        words.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f))
        words.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.9f))
        tile.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        tile.setOnClickListener {
            FeedbackTap.haptic(tile)
            navigateTo(Screen.Game(game))
        }
        return tile
    }

    private object FeedbackTap {
        fun haptic(view: View) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // ------------------------- GAME SHELL -------------------------

    private fun showGame(game: GameType) {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        root.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = text("←", 28f, Color.WHITE, true).apply {
            background = rounded(0xFF4E8EE8.toInt(), 18)
            setOnClickListener { goBackOnePage() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(58), dp(46)))

        val title = text(game.title, 28f, coral, true)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            title, 20, 30, 1, TypedValue.COMPLEX_UNIT_SP
        )
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))

        val score = text("⭐ $stars", 18f, brown, true)
        header.addView(score, LinearLayout.LayoutParams(dp(88), dp(46)))
        page.addView(header)

        val questionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 22)
            elevation = dp(2).toFloat()
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val q = text("", 23f, brown, true)
        q.id = View.generateViewId()
        q.maxLines = 3
        q.minLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            q, 17, 26, 1, TypedValue.COMPLEX_UNIT_SP
        )
        questionCard.addView(q, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)
        ))

        val replay = text("🔊 朗讀題目・重播", 16f, Color.WHITE, true).apply {
            background = rounded(0xFF3978CF.toInt(), 16)
            setOnClickListener {
                speakQuestion()
                toastFeedback("再聽一次 🔊")
            }
        }
        questionCard.addView(replay, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
        ))
        page.addView(questionCard)

        val gameArea = FrameLayout(this).apply {
            setPadding(0, dp(8), 0, dp(4))
        }
        page.addView(gameArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val feedback = text("", 17f, brown, true).apply {
            visibility = View.INVISIBLE
            background = rounded(0xFFFFF2BE.toInt(), 16)
        }
        page.addView(feedback, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ))

        when (game) {
            GameType.FIND -> buildFind(gameArea, q, feedback)
            GameType.SAME -> buildSame(gameArea, q, feedback)
            GameType.COLOR -> buildColor(gameArea, q, feedback)
            GameType.MAZE -> buildMaze(gameArea, q, feedback)
            GameType.MATCH -> buildMatch(gameArea, q, feedback)
            GameType.COUNT -> buildCount(gameArea, q, feedback)
            GameType.MATH -> buildMath(gameArea, q, feedback)
            GameType.MEMORY -> buildMemory(gameArea, q, feedback)
        }
    }

    private fun setQuestion(q: TextView, question: String, delay: Long = 500L) {
        currentQuestion = question
        q.text = question
        handler.postDelayed({ speakQuestion() }, delay)
    }

    private fun speakQuestion() {
        if (!ttsReady || currentQuestion.isBlank()) return
        tts.stop()
        tts.language = Locale.TAIWAN
        tts.setSpeechRate(0.86f)
        tts.speak(currentQuestion, TextToSpeech.QUEUE_FLUSH, null, "question")
    }

    private fun toastFeedback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun feedback(view: TextView, message: String, good: Boolean) {
        view.text = message
        view.setTextColor(if (good) 0xFF3C7A3F.toInt() else 0xFF9A4E3A.toInt())
        view.background = rounded(if (good) 0xFFE9F8DE.toInt() else 0xFFFFE4D9.toInt(), 16)
        view.visibility = View.VISIBLE
        handler.postDelayed({ view.visibility = View.INVISIBLE }, 1100L)
    }

    private fun success(feedbackView: TextView, message: String = "答對了！ ⭐ +1") {
        stars++
        gamesPlayed++
        saveProgress()
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        feedbackView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        feedback(feedbackView, message, true)
    }

    private fun wrong(feedbackView: TextView, message: String = "再試一次～") {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 120)
        feedbackView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        feedback(feedbackView, message, false)
    }

    private fun optionButton(label: String, color: Int = 0xFFFFFFFF.toInt()): TextView =
        text(label, 30f, brown, true).apply {
            background = rounded(color, 22, 0x11000000, 1)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            maxLines = 2
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 20, 36, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }

    // ------------------------- FIND -------------------------

    private fun buildFind(area: FrameLayout, q: TextView, fb: TextView) {
        val symbols = listOf("●" to "圓形", "■" to "正方形", "▲" to "三角形", "★" to "星星")
        val targetIndex = Random.nextInt(symbols.size)
        val target = symbols[targetIndex]
        setQuestion(q, "請找出所有${target.second}。")

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 5
            useDefaultMargins = false
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        area.addView(grid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val colors = listOf(
            0xFFF35F69.toInt(), 0xFF4D8FE8.toInt(),
            0xFF63B66B.toInt(), 0xFFFFB63C.toInt()
        )

        val kinds = MutableList(20) { Random.nextInt(4) }
        if (kinds.none { it == targetIndex }) kinds[Random.nextInt(kinds.size)] = targetIndex
        var remaining = kinds.count { it == targetIndex }

        kinds.forEach { kind ->
            val b = optionButton(symbols[kind].first, 0xFFFFFFFF.toInt()).apply {
                setTextColor(colors[kind])
                setOnClickListener {
                    if (kind == targetIndex) {
                        visibility = View.INVISIBLE
                        remaining--
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                        if (remaining == 0) {
                            success(fb, "全部找到了！ ⭐ +1")
                            handler.postDelayed({ showGame(GameType.FIND) }, 850)
                        }
                    } else {
                        wrong(fb)
                    }
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(b, lp)
        }
    }

    // ------------------------- SAME -------------------------

    private fun buildSame(area: FrameLayout, q: TextView, fb: TextView) {
        setQuestion(q, "請找出和上面完全一樣的花朵。")
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        area.addView(wrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val sample = optionButton("🌼", 0xFFFFEBCB.toInt())
        wrap.addView(sample, LinearLayout.LayoutParams(dp(150), dp(120)))

        val values = listOf("🌼", "🌻", "🌸", "🌺").shuffled()
        val grid = GridLayout(this).apply { columnCount = 2 }
        wrap.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        values.forEach { item ->
            val b = optionButton(item, Color.WHITE)
            b.setOnClickListener {
                if (item == "🌼") {
                    success(fb)
                    handler.postDelayed({ showGame(GameType.SAME) }, 750)
                } else wrong(fb)
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(7), dp(7), dp(7), dp(7))
            })
        }
    }

    // ------------------------- COLOR / SHAPE -------------------------

    private data class ShapeItem(val symbol: String, val color: Int, val target: Boolean)

    private fun buildColor(area: FrameLayout, q: TextView, fb: TextView) {
        setQuestion(q, "請點出所有藍色圓形。")
        val blue = 0xFF4D8FE8.toInt()
        val red = 0xFFF05F68.toInt()
        val green = 0xFF63B66B.toInt()

        val items = MutableList(12) {
            val circle = Random.nextBoolean()
            val c = listOf(blue, red, green).random()
            ShapeItem(if (circle) "●" else "■", c, circle && c == blue)
        }
        if (items.none { it.target }) items[Random.nextInt(items.size)] = ShapeItem("●", blue, true)
        var remaining = items.count { it.target }

        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        area.addView(grid)

        items.forEach { item ->
            val b = optionButton(item.symbol, Color.WHITE).apply {
                setTextColor(item.color)
                setOnClickListener {
                    if (item.target) {
                        visibility = View.INVISIBLE
                        remaining--
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
                        if (remaining == 0) {
                            success(fb, "全部找到！ ⭐ +1")
                            handler.postDelayed({ showGame(GameType.COLOR) }, 800)
                        }
                    } else wrong(fb)
                }
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
    }

    // ------------------------- MAZE -------------------------

    private fun buildMaze(area: FrameLayout, q: TextView, fb: TextView) {
        setQuestion(q, "拖曳星星，繞過障礙走到寶箱。")
        area.addView(MazeView(this) {
            success(fb, "找到寶箱！ ⭐ +1")
            handler.postDelayed({ showGame(GameType.MAZE) }, 850)
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    // ------------------------- MATCH -------------------------

    private fun buildMatch(area: FrameLayout, q: TextView, fb: TextView) {
        setQuestion(q, "把一樣的圖案連起來。可以拖曳，也可以先後點兩張。")
        area.addView(MatchView(this,
            onCorrect = {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                feedback(fb, "連對了！", true)
            },
            onWrong = { wrong(fb, "不是這一對，再看看～") },
            onComplete = {
                success(fb, "全部配對完成！ ⭐ +1")
                handler.postDelayed({ showGame(GameType.MATCH) }, 950)
            }
        ), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    // ------------------------- COUNT -------------------------

    private fun buildCount(area: FrameLayout, q: TextView, fb: TextView) {
        val target = Random.nextInt(3, 11)
        setQuestion(q, "請數一數，畫面上有幾顆蘋果？")

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        area.addView(wrap)

        val apples = GridLayout(this).apply { columnCount = 4 }
        repeat(target) {
            apples.addView(text("🍎", 36f), GridLayout.LayoutParams().apply {
                width = dp(64)
                height = dp(64)
            })
        }
        wrap.addView(apples, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val answers = linkedSetOf(target)
        while (answers.size < 3) answers.add(max(1, target + Random.nextInt(-2, 3)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        answers.shuffled().forEach { n ->
            val b = optionButton(n.toString(), 0xFFDDEEFF.toInt())
            b.setOnClickListener {
                if (n == target) {
                    success(fb)
                    handler.postDelayed({ showGame(GameType.COUNT) }, 700)
                } else wrong(fb)
            }
            row.addView(b, LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        wrap.addView(row)
    }

    // ------------------------- MATH -------------------------

    private fun buildMath(area: FrameLayout, q: TextView, fb: TextView) {
        val maxNum = when (difficulty) {
            1 -> 10
            2 -> 15
            else -> 20
        }
        val plus = Random.nextBoolean()
        val a: Int
        val b: Int
        val answer: Int
        val op: String

        if (plus) {
            a = Random.nextInt(1, maxNum / 2 + 1)
            b = Random.nextInt(1, maxNum / 2 + 1)
            answer = a + b
            op = "+"
            setQuestion(q, "請算算看，$a 加 $b 等於多少？")
        } else {
            a = Random.nextInt(2, maxNum + 1)
            b = Random.nextInt(1, a)
            answer = a - b
            op = "−"
            setQuestion(q, "請算算看，$a 減 $b 等於多少？")
        }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        area.addView(wrap)

        val equation = text("$a  $op  $b  =  ?", 44f, brown, true)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            equation, 30, 48, 1, TypedValue.COMPLEX_UNIT_SP
        )
        wrap.addView(equation, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)
        ))

        val answers = linkedSetOf(answer)
        while (answers.size < 3) answers.add(max(0, answer + Random.nextInt(-3, 4)))
        answers.shuffled().forEach { n ->
            val bView = optionButton(n.toString(), 0xFFFFEED1.toInt())
            bView.setOnClickListener {
                if (n == answer) {
                    success(fb)
                    if (gamesPlayed > 0 && gamesPlayed % 8 == 0 && difficulty < 3) {
                        difficulty++
                        saveProgress()
                    }
                    handler.postDelayed({ showGame(GameType.MATH) }, 700)
                } else wrong(fb)
            }
            wrap.addView(bView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(0, dp(7), 0, dp(7)) })
        }
    }

    // ------------------------- MEMORY -------------------------

    private fun buildMemory(area: FrameLayout, q: TextView, fb: TextView) {
        val sequence = MutableList(3 + difficulty) { Random.nextInt(4) }
        val input = mutableListOf<Int>()
        var accepting = false
        var lit = -1

        val colors = intArrayOf(
            0xFFFF6B6B.toInt(), 0xFF4D8FE8.toInt(),
            0xFF63B66B.toInt(), 0xFFFFC34B.toInt()
        )

        setQuestion(q, "先記住顏色順序。", 400)

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        area.addView(grid)

        val tiles = mutableListOf<TextView>()
        repeat(4) { index ->
            val tile = optionButton((index + 1).toString(), colors[index]).apply {
                setTextColor(Color.WHITE)
                alpha = 0.82f
                setOnClickListener {
                    if (!accepting) {
                        feedback(fb, "先看完順序喔！", false)
                        return@setOnClickListener
                    }
                    input.add(index)
                    val pos = input.lastIndex
                    if (sequence[pos] != index) {
                        wrong(fb)
                        handler.postDelayed({ showGame(GameType.MEMORY) }, 750)
                    } else if (input.size == sequence.size) {
                        success(fb, "記憶成功！ ⭐ +1")
                        if (difficulty < 3) {
                            difficulty++
                            saveProgress()
                        }
                        handler.postDelayed({ showGame(GameType.MEMORY) }, 900)
                    } else {
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
                        feedback(fb, "很好，繼續～", true)
                    }
                }
            }
            tiles.add(tile)
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(9), dp(9), dp(9), dp(9))
            })
        }

        var step = 0
        fun flashNext() {
            if (step >= sequence.size) {
                accepting = true
                currentQuestion = "請照剛才的順序點顏色方塊。"
                q.text = currentQuestion
                handler.postDelayed({ speakQuestion() }, 350)
                return
            }
            lit = sequence[step]
            tiles[lit].alpha = 1f
            tiles[lit].scaleX = 1.08f
            tiles[lit].scaleY = 1.08f
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            handler.postDelayed({
                tiles[lit].alpha = 0.82f
                tiles[lit].scaleX = 1f
                tiles[lit].scaleY = 1f
                step++
                handler.postDelayed({ flashNext() }, 250)
            }, 430)
        }

        handler.postDelayed({ flashNext() }, 1200)
    }


    // ------------------------- ENGLISH MODULE -------------------------

    private fun englishKnown(): MutableSet<String> =
        prefs.getStringSet("english_known", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun saveEnglishKnown(set: Set<String>) {
        prefs.edit().putStringSet("english_known", set).apply()
    }

    private fun showEnglishHome() {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFF8E9.toInt())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll)

        content.addView(makePageHeader("ABC 英文小教室"))
        content.addSpace(9)

        val hero = ImageView(this).apply {
            setImageResource(R.drawable.three_teachers)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 24)
            clipToOutline = true
        }
        content.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))
        content.addSpace(10)

        val known = englishKnown().size
        val progressCard = text("📚 已學會 $known / ${EnglishWordBank.all.size} 個單字", 18f, brown, true).apply {
            background = rounded(0xFFE9F7D9.toInt(), 20)
        }
        content.addView(progressCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        content.addSpace(10)

        val cards = listOf(
            Triple("📖", "單字學習", Screen.EnglishWords(null)),
            Triple("🎧", "聽力挑戰", Screen.EnglishListening),
            Triple("✅", "英文測驗", Screen.EnglishQuiz),
            Triple("🎤", "發音練習", Screen.EnglishPronunciation)
        )
        for (row in 0 until 2) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(2) { col ->
                val item = cards[row * 2 + col]
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(
                        if ((row * 2 + col) % 2 == 0) 0xFFDFF3FF.toInt() else 0xFFFFE9D4.toInt(),
                        23
                    )
                    elevation = dp(2).toFloat()
                    addView(text(item.first, 38f), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f))
                    addView(text(item.second, 20f, brown, true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.8f))
                    setOnClickListener { navigateTo(item.third) }
                }
                line.addView(card, LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                    if (col == 0) marginEnd = dp(5) else marginStart = dp(5)
                })
            }
            content.addView(line)
            if (row == 0) content.addSpace(10)
        }
    }

    private fun makePageHeader(titleText: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val back = text("←", 28f, Color.WHITE, true).apply {
                background = rounded(0xFF4E8EE8.toInt(), 17)
                setOnClickListener { goBackOnePage() }
            }
            addView(back, LinearLayout.LayoutParams(dp(56), dp(46)))
            addView(text(titleText, 25f, coral, true).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 18, 27, 1, TypedValue.COMPLEX_UNIT_SP)
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(text("⭐ $stars", 16f, brown, true), LinearLayout.LayoutParams(dp(82), dp(44)))
        }
    }

    private fun showEnglishWords(category: String?) {
        root.removeAllViews()
        root.setBackgroundColor(0xFFF7FBFF.toInt())

        var selectedCategory = category
        var pool = if (selectedCategory == null) EnglishWordBank.all else EnglishWordBank.all.filter { it.category == selectedCategory }
        if (pool.isEmpty()) pool = EnglishWordBank.all
        var index = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
        }
        root.addView(page)
        page.addView(makePageHeader("單字學習"))
        page.addSpace(8)

        val spinner = Spinner(this)
        val categories = listOf("全部分類") + EnglishWordBank.categories.map { "${it.second} ${it.first}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        if (selectedCategory != null) {
            val pos = EnglishWordBank.categories.indexOfFirst { it.first == selectedCategory }
            if (pos >= 0) spinner.setSelection(pos + 1)
        }
        page.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.WHITE, 26)
            elevation = dp(3).toFloat()
        }
        val emoji = text("", 82f)
        val english = text("", 31f, 0xFF2A67A5.toInt(), true).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 22, 34, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val chinese = text("", 22f, brown, true)
        val cat = text("", 14f, 0xFF7A6C62.toInt(), false)
        card.addView(emoji, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.8f))
        card.addView(english, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.8f))
        card.addView(chinese, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.65f))
        card.addView(cat, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.45f))
        page.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addSpace(8)

        val accentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val us = text("🇺🇸 美式發音", 17f, Color.WHITE, true).apply {
            background = rounded(0xFF3978CF.toInt(), 17)
        }
        val uk = text("🇬🇧 英式發音", 17f, Color.WHITE, true).apply {
            background = rounded(0xFF6C58C5.toInt(), 17)
        }
        accentRow.addView(us, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        accentRow.addView(uk, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        page.addView(accentRow)
        page.addSpace(7)

        val knownButton = text("🔔 會了", 18f, brown, true).apply {
            background = rounded(0xFFFFE9A9.toInt(), 18)
        }
        page.addView(knownButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        page.addSpace(7)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prev = text("← 上一個", 18f, brown, true).apply { background = rounded(0xFFE7F0FF.toInt(), 18) }
        val next = text("下一個 →", 18f, brown, true).apply { background = rounded(0xFFE7F0FF.toInt(), 18) }
        nav.addView(prev, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) })
        nav.addView(next, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) })
        page.addView(nav)

        fun renderWord(autoSpeak: Boolean = false) {
            if (pool.isEmpty()) return
            index = ((index % pool.size) + pool.size) % pool.size
            val w = pool[index]
            emoji.text = w.emoji
            english.text = w.english
            chinese.text = w.chinese
            cat.text = "${w.category}　${index + 1} / ${pool.size}"
            val known = englishKnown().contains(w.english.lowercase())
            knownButton.text = if (known) "✅ 已會了" else "🔔 會了"
            if (autoSpeak) speakEnglish(w.english, englishAccent)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = if (position == 0) null else EnglishWordBank.categories[position - 1].first
                pool = if (selectedCategory == null) EnglishWordBank.all else EnglishWordBank.all.filter { it.category == selectedCategory }
                index = 0
                renderWord(false)
            }
        }
        us.setOnClickListener {
            englishAccent = Locale.US
            speakEnglish(pool[index].english, Locale.US)
        }
        uk.setOnClickListener {
            englishAccent = Locale.UK
            speakEnglish(pool[index].english, Locale.UK)
        }
        prev.setOnClickListener { index--; renderWord(true) }
        next.setOnClickListener { index++; renderWord(true) }
        knownButton.setOnClickListener {
            val w = pool[index]
            val set = englishKnown()
            if (set.add(w.english.lowercase())) {
                saveEnglishKnown(set)
                stars++
                saveProgress()
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                knownButton.text = "✅ 已會了　⭐ +1"
            } else {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            }
        }
        renderWord(false)
    }

    private fun showEnglishQuiz() {
        root.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)
        page.addView(makePageHeader("英文測驗"))
        page.addSpace(8)

        val q = text("", 24f, brown, true).apply {
            background = rounded(Color.WHITE, 22)
            maxLines = 3
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 18, 25, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        page.addView(q, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)))
        page.addSpace(8)

        val answerWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(answerWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val fb = text("", 17f, brown, true).apply { visibility = View.INVISIBLE }
        page.addView(fb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        fun nextQuestion() {
            answerWrap.removeAllViews()
            val target = EnglishWordBank.all.random()
            val options = linkedSetOf(target)
            while (options.size < 3) options.add(EnglishWordBank.all.random())
            q.text = "${target.emoji} ${target.chinese} 的英文怎麼說？"
            currentQuestion = q.text.toString()
            handler.postDelayed({ speakChinese(currentQuestion) }, 500L)
            options.shuffled().forEach { opt ->
                val b = optionButton(opt.english, 0xFFFFFFFF.toInt()).apply {
                    setOnClickListener {
                        if (opt == target) {
                            stars++
                            gamesPlayed++
                            saveProgress()
                            feedback(fb, "答對了！ ⭐ +1", true)
                            speakEnglish(target.english, englishAccent)
                            handler.postDelayed({ nextQuestion() }, 850L)
                        } else wrong(fb, "再想想看～")
                    }
                }
                answerWrap.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, dp(6), 0, dp(6))
                })
            }
        }
        nextQuestion()
    }

    private fun showEnglishListening() {
        root.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)
        page.addView(makePageHeader("聽力挑戰"))
        page.addSpace(8)
        val prompt = text("🔊 聽單字，選出正確圖片", 22f, brown, true).apply {
            background = rounded(Color.WHITE, 22)
        }
        page.addView(prompt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))
        page.addSpace(8)
        val replay = text("🔊 再聽一次", 17f, Color.WHITE, true).apply { background = rounded(0xFF3978CF.toInt(), 16) }
        page.addView(replay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        page.addSpace(8)
        val grid = GridLayout(this).apply { columnCount = 2 }
        page.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val fb = text("", 17f, brown, true).apply { visibility = View.INVISIBLE }
        page.addView(fb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        var target = EnglishWordBank.all.first()

        fun nextRound() {
            target = EnglishWordBank.all.random()
            val options = linkedSetOf(target)
            while (options.size < 4) options.add(EnglishWordBank.all.random())
            grid.removeAllViews()
            options.shuffled().forEach { opt ->
                val b = optionButton(opt.emoji, Color.WHITE).apply {
                    textSize = 48f
                    setOnClickListener {
                        if (opt == target) {
                            stars++
                            gamesPlayed++
                            saveProgress()
                            feedback(fb, "聽對了！ ${target.english} ⭐ +1", true)
                            handler.postDelayed({ nextRound() }, 900L)
                        } else wrong(fb, "再聽一次～")
                    }
                }
                grid.addView(b, GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            handler.postDelayed({ speakEnglish(target.english, englishAccent) }, 500L)
        }
        replay.setOnClickListener { speakEnglish(target.english, englishAccent) }
        nextRound()
    }

    private fun showEnglishPronunciation() {
        root.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)
        page.addView(makePageHeader("發音練習"))
        page.addSpace(8)

        val targetWord = EnglishWordBank.everyday.random()
        pronunciationTarget = targetWord.english

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 24)
            elevation = dp(3).toFloat()
            addView(text(targetWord.emoji, 82f), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f))
            addView(text(targetWord.english, 31f, 0xFF2A67A5.toInt(), true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.6f))
            addView(text(targetWord.chinese, 21f, brown, true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.5f))
        }
        page.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(245)))
        page.addSpace(8)

        val accent = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val us = text("🇺🇸 美式", 17f, Color.WHITE, true).apply { background = rounded(0xFF3978CF.toInt(), 16) }
        val uk = text("🇬🇧 英式", 17f, Color.WHITE, true).apply { background = rounded(0xFF6C58C5.toInt(), 16) }
        accent.addView(us, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        accent.addView(uk, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        page.addView(accent)
        page.addSpace(8)

        val start = text("🔊 示範 → 停 0.5 秒 → 🎤 跟讀", 17f, Color.WHITE, true).apply {
            background = rounded(0xFFEF5C77.toInt(), 18)
        }
        page.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        page.addSpace(8)

        val status = text("按上方按鈕開始", 16f, brown, true).apply { background = rounded(0xFFFFF0BE.toInt(), 16) }
        val result = text("辨識內容：—", 17f, brown, false, Gravity.START or Gravity.CENTER_VERTICAL).apply { background = rounded(Color.WHITE, 16) }
        val score = text("發音分數：—", 22f, 0xFF3978CF.toInt(), true).apply { background = rounded(Color.WHITE, 16) }
        page.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        page.addSpace(6)
        page.addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        page.addSpace(6)
        page.addView(score, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        pronunciationStatusView = status
        pronunciationResultView = result
        pronunciationScoreView = score

        us.setOnClickListener { englishAccent = Locale.US; speakEnglish(targetWord.english, Locale.US) }
        uk.setOnClickListener { englishAccent = Locale.UK; speakEnglish(targetWord.english, Locale.UK) }
        start.setOnClickListener { requestPronunciationDemo() }
    }

    private fun speakChinese(text: String) {
        if (!ttsReady) return
        tts.language = Locale.TAIWAN
        tts.setSpeechRate(0.86f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zh_question")
    }

    private fun speakEnglish(text: String, locale: Locale) {
        if (!ttsReady) return
        tts.language = locale
        tts.setSpeechRate(0.82f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english_word")
    }

    private fun requestPronunciationDemo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pronunciationStatusView?.text = "需要麥克風權限，允許後會開始示範"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7301)
            return
        }
        speakPronunciationDemo()
    }

    private fun speakPronunciationDemo() {
        if (!ttsReady || pronunciationTarget.isBlank()) return
        pronunciationStatusView?.text = "先聽示範…"
        pronunciationResultView?.text = "辨識內容：—"
        pronunciationScoreView?.text = "發音分數：—"
        tts.stop()
        tts.language = englishAccent
        tts.setSpeechRate(0.78f)
        tts.speak(pronunciationTarget, TextToSpeech.QUEUE_FLUSH, null, "pronunciation_demo")
    }

    private fun beginPronunciationRecognition() {
        if (pronunciationTarget.isBlank() || pronunciationStatusView == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            pronunciationStatusView?.text = "這台裝置沒有可用的語音辨識服務"
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    pronunciationStatusView?.text = "🎤 現在請說：$pronunciationTarget"
                }
                override fun onBeginningOfSpeech() {
                    pronunciationStatusView?.text = "正在聽…"
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    pronunciationStatusView?.text = "正在分析發音…"
                }
                override fun onError(error: Int) {
                    pronunciationStatusView?.text = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "沒有聽清楚，再試一次"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "沒有聽到聲音，再試一次"
                        else -> "辨識未完成，請再試一次"
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heard = matches.firstOrNull().orEmpty()
                    pronunciationResultView?.text = "辨識內容：${if (heard.isBlank()) "—" else heard}"
                    val score = pronunciationSimilarity(pronunciationTarget, heard)
                    pronunciationScoreView?.text = "發音分數：$score 分"
                    pronunciationStatusView?.text = when {
                        score >= 90 -> "🌟 很棒！發音很接近"
                        score >= 70 -> "👍 不錯，再唸一次會更好"
                        else -> "💪 再聽一次示範試看看"
                    }
                    if (score >= 90) {
                        stars++
                        saveProgress()
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, englishAccent.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, englishAccent.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7301) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speakPronunciationDemo()
            } else {
                pronunciationStatusView?.text = "未取得麥克風權限，無法進行跟讀"
            }
        }
    }

    private fun pronunciationSimilarity(target: String, heard: String): Int {
        val a = target.lowercase(Locale.US).replace(Regex("[^a-z]"), "")
        val b = heard.lowercase(Locale.US).replace(Regex("[^a-z]"), "")
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 100
        val dist = levenshtein(a, b)
        return ((1.0 - dist.toDouble() / max(a.length, b.length).toDouble()) * 100.0)
            .roundToInt().coerceIn(0, 100)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    // ------------------------- ACHIEVEMENTS -------------------------

    private fun showAchievements() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        root.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            setBackgroundColor(bg)
        }
        root.addView(page)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = text("←", 28f, Color.WHITE, true).apply {
            background = rounded(0xFF4E8EE8.toInt(), 18)
            setOnClickListener { goBackOnePage() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(58), dp(46)))
        header.addView(text("🏅 成就徽章", 28f, brown, true), LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(text("⭐ $stars", 19f, brown, true), LinearLayout.LayoutParams(dp(92), dp(46)))
        page.addView(header)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        scroll.addView(grid)
        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val badges = listOf(
            Triple("🌟", "初次挑戰", gamesPlayed >= 1),
            Triple("🎯", "專注新秀", gamesPlayed >= 5),
            Triple("🔥", "連勝新秀", stars >= 10),
            Triple("🧠", "記憶小高手", stars >= 20),
            Triple("🔢", "數學達人", stars >= 30),
            Triple("🏆", "毛孩學霸", stars >= 50)
        )
        badges.forEach { (icon, title, unlocked) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(
                    if (unlocked) 0xFFFFF0B8.toInt() else 0xFFE8E8E8.toInt(),
                    24
                )
                setPadding(dp(10), dp(12), dp(10), dp(12))
                addView(text(if (unlocked) icon else "🔒", 40f))
                addView(text(title, 18f, brown, true))
                addView(text(if (unlocked) "已獲得" else "繼續加油", 14f, 0xFF7B6E65.toInt()))
            }
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(150)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
    }

    // ------------------------- CUSTOM MAZE VIEW -------------------------

    private class MazeView(
        context: Context,
        val onWin: () -> Unit
    ) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var player = PointF()
        private var goal = PointF()
        private val walls = mutableListOf<RectF>()
        private var dragging = false
        private var ready = false
        private val density = resources.displayMetrics.density
        private fun dp(v: Float) = v * density

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            player = PointF(dp(34f), dp(52f))
            goal = PointF(w - dp(36f), h - dp(44f))
            walls.clear()
            walls += RectF(w * .18f, dp(25f), w * .25f, h * .63f)
            walls += RectF(w * .38f, h * .33f, w * .45f, h - dp(28f))
            walls += RectF(w * .58f, dp(25f), w * .65f, h * .63f)
            walls += RectF(w * .78f, h * .33f, w * .85f, h - dp(28f))
            ready = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!ready) return
            canvas.drawColor(0xFFEAF6FF.toInt())

            p.color = 0xFF8B6C5E.toInt()
            walls.forEach { canvas.drawRoundRect(it, dp(8f), dp(8f), p) }

            p.color = 0xFFFFC107.toInt()
            drawStar(canvas, player.x, player.y, dp(19f), p)

            p.color = 0xFF915C2B.toInt()
            canvas.drawRoundRect(goal.x-dp(24f), goal.y-dp(17f), goal.x+dp(24f), goal.y+dp(17f), dp(6f), dp(6f), p)
            p.color = 0xFFFFC344.toInt()
            canvas.drawRect(goal.x-dp(3f), goal.y-dp(17f), goal.x+dp(3f), goal.y+dp(17f), p)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = hypot((e.x-player.x).toDouble(), (e.y-player.y).toDouble()) <= dp(48f)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    val x = e.x.coerceIn(dp(20f), width-dp(20f))
                    val y = e.y.coerceIn(dp(20f), height-dp(20f))
                    val r = RectF(x-dp(17f), y-dp(17f), x+dp(17f), y+dp(17f))
                    if (walls.none { RectF.intersects(it, r) }) {
                        player.x = x
                        player.y = y
                        invalidate()
                        if (hypot((player.x-goal.x).toDouble(), (player.y-goal.y).toDouble()) < dp(45f)) {
                            dragging = false
                            onWin()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
            val path = Path()
            for (i in 0 until 10) {
                val a = -Math.PI/2 + i*Math.PI/5
                val rr = if (i%2==0) r else r*.45f
                val x = cx + cos(a).toFloat()*rr
                val y = cy + sin(a).toFloat()*rr
                if (i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close()
            c.drawPath(path, paint)
        }
    }

    // ------------------------- CUSTOM MATCH VIEW -------------------------

    private class MatchView(
        context: Context,
        val onCorrect: () -> Unit,
        val onWrong: () -> Unit,
        val onComplete: () -> Unit
    ) : View(context) {

        data class Card(val id: Int, val side: Int, val emoji: String, var rect: RectF = RectF())

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val density = resources.displayMetrics.density
        private fun dp(v: Float) = v * density

        private val cards = mutableListOf<Card>()
        private val matched = mutableSetOf<Int>()
        private var dragStart = -1
        private var selected = -1
        private var dragPoint = PointF()

        init {
            val icons = listOf("🍎", "🐸", "🐼", "🍌")
            icons.forEachIndexed { i, e -> cards += Card(i, 0, e) }
            (0..3).shuffled().forEach { i -> cards += Card(i, 1, icons[i]) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(0xFFF7FBFF.toInt())

            val top = dp(15f)
            val gap = dp(11f)
            val cardW = min(dp(125f), width * .34f)
            val cardH = (height - top*2 - gap*3) / 4f
            val leftX = dp(10f)
            val rightX = width - dp(10f) - cardW

            for (i in 0..3) {
                val y = top + i*(cardH+gap)
                cards[i].rect = RectF(leftX, y, leftX+cardW, y+cardH)
                cards[4+i].rect = RectF(rightX, y, rightX+cardW, y+cardH)
            }

            linePaint.strokeWidth = dp(6f)
            val lineColors = intArrayOf(
                0xFFFF6B9A.toInt(), 0xFF4AA8FF.toInt(),
                0xFFFFBE3C.toInt(), 0xFF65C96F.toInt()
            )
            matched.forEach { id ->
                val a = cards.first { it.id == id && it.side == 0 }.rect
                val b = cards.first { it.id == id && it.side == 1 }.rect
                linePaint.color = lineColors[id]
                canvas.drawLine(a.right, a.centerY(), b.left, b.centerY(), linePaint)
            }

            if (dragStart >= 0) {
                val r = cards[dragStart].rect
                linePaint.color = 0xFF7D6AEF.toInt()
                val sx = if (cards[dragStart].side == 0) r.right else r.left
                canvas.drawLine(sx, r.centerY(), dragPoint.x, dragPoint.y, linePaint)
            }

            cards.forEachIndexed { index, c ->
                paint.color = when {
                    c.id in matched -> 0xFFE7F8E4.toInt()
                    index == selected || index == dragStart -> 0xFFFFF0B7.toInt()
                    else -> Color.WHITE
                }
                canvas.drawRoundRect(c.rect, dp(18f), dp(18f), paint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = min(dp(45f), cardH*.52f)
                canvas.drawText(c.emoji, c.rect.centerX(), c.rect.centerY()+textPaint.textSize*.34f, textPaint)
            }
        }

        private fun cardAt(x: Float, y: Float): Int {
            return cards.indexOfFirst { it.id !in matched && RectF(it.rect).apply { inset(-dp(10f), -dp(10f)) }.contains(x,y) }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStart = cardAt(e.x, e.y)
                    if (dragStart >= 0) {
                        dragPoint.set(e.x, e.y)
                        invalidate()
                    }
                }
                MotionEvent.ACTION_MOVE -> if (dragStart >= 0) {
                    dragPoint.set(e.x, e.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (dragStart < 0) return true
                    val start = dragStart
                    val end = cardAt(e.x, e.y)
                    val moved = hypot(
                        (cards[start].rect.centerX()-e.x).toDouble(),
                        (cards[start].rect.centerY()-e.y).toDouble()
                    ) > dp(34f)

                    if (end >= 0 && end != start && moved) {
                        tryMatch(start, end)
                        selected = -1
                    } else {
                        if (selected < 0) selected = start
                        else if (selected == start) selected = -1
                        else {
                            tryMatch(selected, start)
                            selected = -1
                        }
                    }
                    dragStart = -1
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragStart = -1
                    invalidate()
                }
            }
            return true
        }

        private fun tryMatch(a: Int, b: Int) {
            val ca = cards[a]
            val cb = cards[b]
            if (ca.side != cb.side && ca.id == cb.id) {
                matched += ca.id
                onCorrect()
                if (matched.size == 4) onComplete()
            } else onWrong()
        }
    }
}
