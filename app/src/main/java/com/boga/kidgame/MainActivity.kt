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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import java.text.SimpleDateFormat
import java.util.Date
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

    private enum class MapFocus { DAILY, CHEST }

    private sealed class Screen {
        object Home : Screen()
        data class Game(val game: GameType) : Screen()
        object Achievements : Screen()
        data class TreasureMap(val focus: MapFocus) : Screen()
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
    private var dailyProgress = 0
    private var dailyDateKey = ""
    private var chestClaims = 0
    private var currentQuestion = ""
    private val screenStack = mutableListOf<Screen>()
    private var englishAccent = Locale.US
    private var speechRecognizer: SpeechRecognizer? = null
    private var pronunciationTarget: String = ""
    private var pronunciationResultView: TextView? = null
    private var pronunciationScoreView: TextView? = null
    private var pronunciationStatusView: TextView? = null
    private var pendingGameNavigation: GameType? = null

    private val bg = Color.rgb(255, 248, 234)
    private val brown = Color.rgb(86, 64, 48)
    private val coral = Color.rgb(238, 79, 104)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stars = prefs.getInt("stars", 0)
        gamesPlayed = prefs.getInt("games", 0)
        difficulty = prefs.getInt("difficulty", 1).coerceIn(1, 3)
        chestClaims = prefs.getInt("chest_claims", 0)
        syncDailyMission()
        tts = TextToSpeech(this, this)

        root = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }
        setContentView(root)

        // Android 15 / Samsung 三鍵導覽列會以 edge-to-edge 疊在 App 內容上。
        // 只套用底部 system bar inset，保留目前上方狀態列的版面位置。
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, 0, 0, navigationBar.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

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
                    when (utteranceId) {
                        "pronunciation_demo" -> {
                            handler.postDelayed({ beginPronunciationRecognition() }, 500L)
                        }
                        "home_game_title" -> {
                            val game = pendingGameNavigation
                            pendingGameNavigation = null
                            if (game != null) {
                                handler.post { navigateTo(Screen.Game(game)) }
                            }
                        }
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
        setPadding(dp(6), dp(6), dp(6), dp(6))
        includeFontPadding = true
        setLineSpacing(0f, 1.0f)
        typeface = android.graphics.Typeface.create(
            "sans-serif-rounded",
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }

    private fun LinearLayout.addSpace(height: Int) {
        addView(Space(this@MainActivity), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun syncDailyMission() {
        val today = todayKey()
        val savedDate = prefs.getString("daily_date", "").orEmpty()
        if (savedDate != today) {
            dailyDateKey = today
            dailyProgress = 0
            prefs.edit()
                .putString("daily_date", today)
                .putInt("daily_progress", 0)
                .apply()
        } else {
            dailyDateKey = today
            dailyProgress = prefs.getInt("daily_progress", 0).coerceIn(0, 5)
        }
    }

    private fun recordCompletedChallenge() {
        syncDailyMission()
        gamesPlayed++
        if (dailyProgress < 5) dailyProgress++
    }

    private fun canOpenStarChest(): Boolean = stars / 30 > chestClaims

    private fun starChestProgress(): Int =
        if (canOpenStarChest()) 30 else stars % 30

    private fun chestBadgeTitle(claimNumber: Int): String = when ((claimNumber - 1) % 4) {
        0 -> "小小探險家"
        1 -> "毛孩尋寶王"
        2 -> "星光勇者"
        else -> "寶藏大師"
    }

    private fun chestBadgeIcon(claimNumber: Int): String = when ((claimNumber - 1) % 4) {
        0 -> "🗺️"
        1 -> "🐾"
        2 -> "🌟"
        else -> "👑"
    }

    private fun saveProgress() {
        prefs.edit()
            .putInt("stars", stars)
            .putInt("games", gamesPlayed)
            .putInt("difficulty", difficulty)
            .putString("daily_date", dailyDateKey.ifBlank { todayKey() })
            .putInt("daily_progress", dailyProgress)
            .putInt("chest_claims", chestClaims)
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
            is Screen.TreasureMap -> showTreasureMap(screen.focus)
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
    syncDailyMission()
    root.removeAllViews()
    root.setBackgroundColor(0xFFFFF8E9.toInt())

    // 以一頁為主要設計目標；ScrollView 只作為較矮手機的安全備援。
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(7), dp(10), dp(6))
    }
    scroll.addView(content, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    root.addView(scroll)

    // 最上方只保留真正有意義的星星 / 寶箱進度。
    content.addView(makeTopStats(), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    content.addSpace(3)

    val missionRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    missionRow.addView(
        makeMissionCard(
            R.drawable.mission_calendar,
            "每日任務",
            "$dailyProgress/5",
            0xFFF0F8D9.toInt(),
            0xFF58A815.toInt()
        ) { navigateTo(Screen.TreasureMap(MapFocus.DAILY)) },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) }
    )
    missionRow.addView(
        makeMissionCard(
            R.drawable.mission_treasure,
            "星星寶箱",
            if (canOpenStarChest()) "可開啟!" else "${starChestProgress()}/30",
            0xFFFFF1D4.toInt(),
            0xFFE37500.toInt()
        ) { navigateTo(Screen.TreasureMap(MapFocus.CHEST)) },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) }
    )
    content.addView(missionRow)
    content.addSpace(3)

    val pets = ImageView(this).apply {
        setImageResource(R.drawable.home_pets)
        scaleType = ImageView.ScaleType.CENTER_CROP
        adjustViewBounds = true
        background = rounded(0xFFFFF8E9.toInt(), 18)
        clipToOutline = true
        contentDescription = "偶貴老師、黑糖老師、熊熊老師"
    }
    content.addView(pets, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(110)
    ))
    content.addSpace(3)

    val gameValues = GameType.values()
    for (row in 0 until 4) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        repeat(2) { col ->
            val index = row * 2 + col
            val tile = makeGameTile(gameValues[index])
            val lp = LinearLayout.LayoutParams(0, dp(70), 1f)
            if (col == 0) lp.marginEnd = dp(5) else lp.marginStart = dp(5)
            line.addView(tile, lp)
        }
        content.addView(line)
        if (row < 3) content.addSpace(3)
    }

    content.addSpace(4)

    val english = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        setPadding(dp(14), dp(5), dp(14), dp(5))
        background = rounded(0xFFDFF3FF.toInt(), 22)
        elevation = dp(2).toFloat()
        isClickable = true
        isFocusable = true
        addView(text("ABC", 21f, 0xFF2879CB.toInt(), true),
            LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(text("英文小教室", 18f, brown, true,
            Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 14, 19, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("›", 27f, 0xFF2879CB.toInt(), true),
            LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        setOnClickListener { navigateTo(Screen.EnglishHome) }
    }
    content.addView(english, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ))

    content.addSpace(4)

    val achievements = text("🏅 我的徽章　　已累積 ⭐ $stars", 16f, brown, true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
        maxLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, 13, 17, 1, TypedValue.COMPLEX_UNIT_SP
        )
        background = rounded(0xFFFFE9A9.toInt(), 20)
        minimumHeight = dp(46)
        setOnClickListener { navigateTo(Screen.Achievements) }
    }
    content.addView(achievements, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    content.addSpace(2)
}

private fun makeTopStats(): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.WHITE, 24)
        elevation = dp(3).toFloat()
        minimumHeight = dp(60)
        setPadding(dp(12), dp(6), dp(8), dp(6))

        addView(text("小小腦力樂園", 20f, 0xFF4A2E1D.toInt(), true,
            Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 15, 21, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(makeTopCounter(R.drawable.top_star, stars.toString()),
            LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(makeTopCounter(
            R.drawable.mission_treasure,
            if (canOpenStarChest()) "OPEN" else "${starChestProgress()}/30"
        ), LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

private fun makeTopCounter(iconRes: Int, value: String): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(4), dp(2), dp(4))
        val iv = ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(iv, LinearLayout.LayoutParams(dp(32), dp(32)))
        addView(text(value, 17f, 0xFF3B2417.toInt(), true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 12, 18, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}

    private fun makeMissionCard(
        iconRes: Int,
        title: String,
        value: String,
        color: Int,
        valueColor: Int,
        onClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color, 23)
            elevation = dp(2).toFloat()
            minimumHeight = dp(68)
            isClickable = true
            isFocusable = true
            setPadding(dp(9), dp(6), dp(9), dp(6))
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }

            val iv = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(iv, LinearLayout.LayoutParams(dp(46), dp(46)))

            val words = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, 0, 0)

                addView(
                    text(title, 15f, 0xFF4A2E1D.toInt(), true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                        maxLines = 1
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this, 12, 16, 1, TypedValue.COMPLEX_UNIT_SP
                        )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    text(value, 21f, valueColor, true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                        maxLines = 1
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this, 16, 22, 1, TypedValue.COMPLEX_UNIT_SP
                        )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            addView(
                words,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
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
            minimumHeight = dp(68)
            setPadding(dp(8), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            contentDescription = game.title
        }

        val icon = ImageView(this).apply {
            setImageResource(gameIconRes(game))
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            contentDescription = game.title
            // 子元件不攔截點擊，整張卡片都是同一個點擊區。
            isClickable = false
            isFocusable = false
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        val title = text(
            game.title,
            18f,
            0xFF4B2C18.toInt(),
            true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 14, 19, 1, TypedValue.COMPLEX_UNIT_SP
            )
            isClickable = false
            isFocusable = false
        }
        tile.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(8)
            }
        )

        tile.setOnClickListener {
            tile.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // v0.6.4：先朗讀卡片名稱，朗讀完成後才切到遊戲頁。
            // 這樣 renderScreen()/showGame() 的 tts.stop() 不會再把名稱切掉。
            if (ttsReady) {
                pendingGameNavigation = game
                tts.stop()
                tts.language = Locale.TAIWAN
                tts.setSpeechRate(0.86f)
                tts.speak(game.title, TextToSpeech.QUEUE_FLUSH, null, "home_game_title")

                // 某些手機 TTS 不一定回傳 onDone；1.5 秒後保底進頁。
                handler.postDelayed({
                    if (pendingGameNavigation == game) {
                        pendingGameNavigation = null
                        navigateTo(Screen.Game(game))
                    }
                }, 1500L)
            } else {
                navigateTo(Screen.Game(game))
            }
        }

        return tile
    }

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

        // 只有「重播題目」保留喇叭按鈕。
        val replay = text("🔊 重播題目", 16f, Color.WHITE, true).apply {
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

        // 進入遊戲後仍維持約 0.5 秒自動朗讀題目。
        // 若主畫面的遊戲名稱還在唸，題目排在後面，避免把遊戲名稱切掉。
        handler.postDelayed({ speakQuestion(autoRead = true) }, delay)
    }

    private fun speakQuestion(autoRead: Boolean = false) {
        if (!ttsReady || currentQuestion.isBlank()) return
        tts.language = Locale.TAIWAN
        tts.setSpeechRate(0.86f)

        if (!autoRead) {
            // 使用者按「重播題目」時要立即重播。
            tts.stop()
        }

        tts.speak(
            currentQuestion,
            if (autoRead) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            null,
            if (autoRead) "question_auto" else "question_replay"
        )
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
        recordCompletedChallenge()
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
        // v0.5.6：題目改成「固定輪替」而不是完全隨機。
        // 這樣每一局一定會換題，不會剛好又抽到「藍色圓形」而看起來像沒更新。
        // 輪替內容包含 5 種顏色 × 2 種圖形，並特別包含「藍色正方形」。
        data class ColorSpec(val name: String, val value: Int)
        data class ShapeSpec(val symbol: String, val name: String)

        val colors = listOf(
            ColorSpec("藍色", 0xFF4D8FE8.toInt()),
            ColorSpec("紅色", 0xFFF05F68.toInt()),
            ColorSpec("綠色", 0xFF63B66B.toInt()),
            ColorSpec("黃色", 0xFFFFB63C.toInt()),
            ColorSpec("紫色", 0xFF8A68D8.toInt())
        )
        val circle = ShapeSpec("●", "圓形")
        val square = ShapeSpec("■", "正方形")

        // 第一題刻意從「紅色圓形」開始，讓新版安裝後一眼就能確認題目已更新。
        // 之後依序輪替，10 題一循環：
        // 紅圓 → 綠方 → 黃圓 → 紫方 → 藍方 → 紅方 → 綠圓 → 黃方 → 紫圓 → 藍圓
        val questionSequence = listOf(
            colors[1] to circle,
            colors[2] to square,
            colors[3] to circle,
            colors[4] to square,
            colors[0] to square,
            colors[1] to square,
            colors[2] to circle,
            colors[3] to square,
            colors[4] to circle,
            colors[0] to circle
        )

        val prefs = getSharedPreferences("kid_game_progress", MODE_PRIVATE)
        val rawIndex = prefs.getInt("colorQuestionIndex", 0)
        val index = ((rawIndex % questionSequence.size) + questionSequence.size) % questionSequence.size
        val (targetColor, targetShape) = questionSequence[index]
        prefs.edit().putInt("colorQuestionIndex", (index + 1) % questionSequence.size).apply()

        val questionText = "請點出所有${targetColor.name}${targetShape.name}。"
        setQuestion(q, questionText)

        // 固定 3 個正確答案；其他 9 個一定不會和目標「顏色 + 圖形」完全相同。
        val items = mutableListOf<ShapeItem>()
        repeat(3) {
            items += ShapeItem(targetShape.symbol, targetColor.value, true)
        }

        while (items.size < 12) {
            val color = colors.random()
            val shape = if (Random.nextBoolean()) circle else square

            if (color.value == targetColor.value && shape.symbol == targetShape.symbol) {
                continue
            }
            items += ShapeItem(shape.symbol, color.value, false)
        }
        items.shuffle()

        var remaining = items.count { it.target }

        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            setPadding(dp(6), dp(8), dp(6), dp(8))
            isClickable = true
        }
        area.addView(
            grid,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        items.forEach { item ->
            val b = optionButton(item.symbol, Color.WHITE).apply {
                setTextColor(item.color)

                // 明確確保所有方格（包含藍色正方形）都有可點擊狀態。
                isClickable = true
                isFocusable = true
                isEnabled = true

                setOnClickListener {
                    if (item.target) {
                        if (!isEnabled) return@setOnClickListener

                        // 點過的正確答案只計一次，但仍留在畫面上淡化，
                        // 不再使用 INVISIBLE，避免格子消失後版面看起來像「不能點」。
                        isEnabled = false
                        alpha = 0.24f
                        remaining--

                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 55)

                        if (remaining == 0) {
                            success(fb, "全部找到！ ⭐ +1")
                            handler.postDelayed({ showGame(GameType.COLOR) }, 900)
                        } else {
                            feedback(fb, "答對了！還有 $remaining 個", true)
                        }
                    } else {
                        wrong(fb)
                    }
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
        if (category == null) {
            showEnglishCategoryList()
            return
        }
        showEnglishWordDeck(category)
    }

    /**
     * 單字學習首頁：依照使用者提供的參考截圖，以大卡片分門別類。
     */
    private fun showEnglishCategoryList() {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFFAF0.toInt())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll)

        content.addView(makePageHeader("單字學習"))
        content.addSpace(8)

        val intro = text("選一個主題開始學習", 18f, brown, true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            background = rounded(0xFFFFF1C9.toInt(), 19)
        }
        content.addView(intro, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addSpace(10)

        EnglishWordBank.categories.forEach { (categoryName, fallbackIcon) ->
            val categoryWords = EnglishWordBank.wordsIn(categoryName)
            val heroEmoji = categoryWords.firstOrNull()?.emoji ?: fallbackIcon
            val sampleEmoji = categoryWords.take(4).joinToString("   ") { it.emoji }
            val description = EnglishWordBank.categoryDescription(categoryName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(Color.WHITE, 26)
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
            }

            val iconBox = text(heroEmoji, 52f, Color.BLACK, false).apply {
                background = rounded(0xFFFFE3EC.toInt(), 23)
            }
            card.addView(iconBox, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
                marginEnd = dp(14)
            })

            val words = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = text(categoryName, 25f, 0xFF19191F.toInt(), true, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 20, 27, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
            val desc = text(description, 16f, 0xFF303038.toInt(), false, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                maxLines = 2
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 14, 17, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
            val samples = text(sampleEmoji, 25f, 0xFF19191F.toInt(), false, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                maxLines = 1
            }
            words.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
            words.addView(desc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            words.addView(samples, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
            card.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val arrow = text("›", 42f, 0xFF17171B.toInt(), true)
            card.addView(arrow, LinearLayout.LayoutParams(dp(40), dp(70)))

            card.setOnClickListener {
                // 分類卡只負責進入分類；進入後 0.5 秒自動朗讀第一個英文單字。
                navigateTo(Screen.EnglishWords(categoryName))
            }

            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(148)))
            content.addSpace(10)
        }
    }

    /**
     * 單字卡：只保留美式發音。進頁 0.5 秒自動朗讀；喇叭與換字按鈕則立即朗讀。
     */
    private fun showEnglishWordDeck(category: String) {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFFAF0.toInt())

        val pool = EnglishWordBank.wordsIn(category).ifEmpty { EnglishWordBank.all }
        var index = 0

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(26))
        }
        scroll.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(scroll)

        page.addView(makePageHeader(category))
        page.addSpace(8)

        val categoryInfo = text(
            EnglishWordBank.categoryDescription(category),
            16f,
            brown,
            false,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            background = rounded(0xFFFFF1C9.toInt(), 18)
            minHeight = dp(50)
            maxLines = 2
        }
        page.addView(
            categoryInfo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        page.addSpace(8)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 26)
            elevation = dp(3).toFloat()
        }

        val emoji = text("", 84f).apply { minHeight = dp(125) }
        val english = text("", 33f, 0xFF2A67A5.toInt(), true).apply {
            maxLines = 1
            minHeight = dp(58)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 23, 35, 1, TypedValue.COMPLEX_UNIT_SP
            )
            isClickable = true
            isFocusable = true
        }
        val chinese = text("", 22f, brown, true).apply {
            minHeight = dp(48)
            maxLines = 1
        }
        val position = text("", 14f, 0xFF7A6C62.toInt(), false).apply {
            minHeight = dp(38)
            maxLines = 1
        }

        card.addView(emoji, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(english, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(chinese, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(position, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(8)

        val speaker = text("🔊", 30f, Color.WHITE, true).apply {
            background = rounded(0xFF3978CF.toInt(), 18)
            contentDescription = "朗讀英文單字"
            minHeight = dp(54)
        }
        page.addView(speaker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(7)

        val knownButton = text("✅ 會了", 18f, brown, true).apply {
            background = rounded(0xFFFFE9A9.toInt(), 18)
            minHeight = dp(50)
        }
        page.addView(knownButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(7)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prev = text("← 上一個", 18f, brown, true).apply {
            background = rounded(0xFFE7F0FF.toInt(), 18)
            minHeight = dp(50)
        }
        val next = text("下一個 →", 18f, brown, true).apply {
            background = rounded(0xFFE7F0FF.toInt(), 18)
            minHeight = dp(50)
        }
        nav.addView(prev, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = dp(5) })
        nav.addView(next, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(5) })
        page.addView(nav)

        fun currentWord(): EnglishWord =
            pool[((index % pool.size) + pool.size) % pool.size]

        fun renderWord(speakNow: Boolean) {
            index = ((index % pool.size) + pool.size) % pool.size
            val w = currentWord()
            emoji.text = w.emoji
            english.text = w.english
            chinese.text = w.chinese
            position.text = "$category　${index + 1} / ${pool.size}"
            val known = englishKnown().contains(w.english.lowercase(Locale.US))
            knownButton.text = if (known) "✅ 已會了" else "✅ 會了"
            if (speakNow) speakEnglishUS(w.english)
        }

        speaker.setOnClickListener { speakEnglishUS(currentWord().english) }
        english.setOnClickListener { speakEnglishUS(currentWord().english) }
        prev.setOnClickListener {
            index--
            renderWord(true)
        }
        next.setOnClickListener {
            index++
            renderWord(true)
        }
        knownButton.setOnClickListener {
            val w = currentWord()
            val set = englishKnown()
            if (set.add(w.english.lowercase(Locale.US))) {
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
        handler.postDelayed({ speakEnglishUS(currentWord().english) }, 500L)
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
                        // 點英文答案按鈕時，立即以美式發音讀出按鈕文字。
                        speakEnglishUS(opt.english)
                        if (opt == target) {
                            stars++
                            recordCompletedChallenge()
                            saveProgress()
                            feedback(fb, "答對了！ ⭐ +1", true)
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
                            recordCompletedChallenge()
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
            handler.postDelayed({ speakEnglishUS(target.english) }, 500L)
        }
        replay.setOnClickListener { speakEnglishUS(target.english) }
        nextRound()
    }

    private fun showEnglishPronunciation() {
        root.removeAllViews()
        root.setBackgroundColor(0xFFF7FBFF.toInt())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(26))
        }
        scroll.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(scroll)

        page.addView(makePageHeader("發音練習"))
        page.addSpace(8)

        val targetWord = EnglishWordBank.everyday.random()
        pronunciationTarget = targetWord.english

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 24)
            elevation = dp(3).toFloat()
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        card.addView(text(targetWord.emoji, 80f).apply {
            minHeight = dp(125)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(text(targetWord.english, 31f, 0xFF2A67A5.toInt(), true).apply {
            minHeight = dp(54)
            maxLines = 1
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(text(targetWord.chinese, 21f, brown, true).apply {
            minHeight = dp(46)
            maxLines = 1
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(8)

        val replay = text("🔊", 28f, Color.WHITE, true).apply {
            background = rounded(0xFF3978CF.toInt(), 16)
            contentDescription = "美式發音示範"
            minHeight = dp(48)
        }
        page.addView(replay, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(8)

        // 使用者指定：紅色按鈕只寫「跟讀」。
        val start = text("跟讀", 18f, Color.WHITE, true).apply {
            background = rounded(0xFFEF5C77.toInt(), 18)
            minHeight = dp(54)
        }
        page.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(8)

        val status = text(
            "按「跟讀」開始",
            16f,
            brown,
            true
        ).apply {
            background = rounded(0xFFFFF0BE.toInt(), 16)
            minHeight = dp(48)
        }
        val result = text(
            "辨識內容：—",
            17f,
            brown,
            false,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            background = rounded(Color.WHITE, 16)
            minHeight = dp(52)
            maxLines = 2
        }
        val score = text(
            "發音分數：—",
            22f,
            0xFF3978CF.toInt(),
            true
        ).apply {
            background = rounded(Color.WHITE, 16)
            minHeight = dp(58)
            maxLines = 2
        }

        page.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(6)
        page.addView(result, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addSpace(6)
        page.addView(score, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        pronunciationStatusView = status
        pronunciationResultView = result
        pronunciationScoreView = score

        replay.setOnClickListener { speakEnglishUS(targetWord.english) }
        start.setOnClickListener { requestPronunciationDemo() }
        handler.postDelayed({ speakEnglishUS(targetWord.english) }, 500L)
    }

    private fun speakChinese(text: String) {
        if (!ttsReady) return
        tts.language = Locale.TAIWAN
        tts.setSpeechRate(0.86f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zh_question")
    }

    private fun speakEnglishUS(text: String) {
        if (!ttsReady) return
        englishAccent = Locale.US
        tts.stop()
        tts.language = Locale.US
        tts.setSpeechRate(0.82f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english_word")
    }

    private fun speakEnglish(text: String, locale: Locale = Locale.US) {
        // 保留舊呼叫介面，但英文小教室統一使用美式發音。
        speakEnglishUS(text)
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
        englishAccent = Locale.US
        tts.language = Locale.US
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag())
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

    // ------------------------- TREASURE MAP -------------------------


private fun showTreasureMap(focus: MapFocus) {
    syncDailyMission()
    root.removeAllViews()
    root.setBackgroundColor(0xFFFFF5D8.toInt())

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(16))
    }
    scroll.addView(page, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    root.addView(scroll)

    page.addView(makePageHeader("冒險藏寶圖"))
    page.addSpace(6)

    val statusRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    statusRow.addView(
        makeAdventureStatusCard(
            R.drawable.mission_calendar,
            "每日任務",
            "$dailyProgress/5",
            if (focus == MapFocus.DAILY) 0xFFE6F6C9.toInt() else 0xFFF4F8E9.toInt(),
            0xFF4D9E27.toInt()
        ) { toastFeedback("完成任一遊戲或英文挑戰，就會增加今日進度") },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
    )
    statusRow.addView(
        makeAdventureStatusCard(
            R.drawable.mission_treasure,
            "星星寶箱",
            if (canOpenStarChest()) "可開啟!" else "${starChestProgress()}/30",
            if (focus == MapFocus.CHEST) 0xFFFFE5A8.toInt() else 0xFFFFF1D4.toInt(),
            0xFFE37500.toInt()
        ) {
            if (canOpenStarChest()) openStarChest()
            else toastFeedback("再收集 ${30 - starChestProgress()} 顆星就能開箱！")
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) }
    )
    page.addView(statusRow)
    page.addSpace(6)

    val map = TreasureMapView(
        context = this,
        dailyProgress = dailyProgress,
        chestReady = canOpenStarChest(),
        gameTitles = GameType.values().map { it.title },
        gameEmojis = GameType.values().map { it.emoji },
        onNodeClick = { index ->
            if (index in GameType.values().indices) {
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                navigateTo(Screen.Game(GameType.values()[index]))
            }
        },
        onChestClick = {
            if (canOpenStarChest()) openStarChest()
            else toastFeedback("寶箱還沒集滿：${starChestProgress()}/30 ⭐")
        }
    )
    page.addView(map, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(610)
    ))
    page.addSpace(7)

    val badgeCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xFFFFF7E8.toInt(), 22, 0x22B38335, 1)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener { navigateTo(Screen.Achievements) }

        addView(text("🏅 我的徽章", 18f, brown, true, Gravity.START))
        addView(makeTreasureBadgeStrip(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(66)
        ))
    }
    page.addView(badgeCard, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(104)
    ))
}

private fun makeAdventureStatusCard(
    iconRes: Int,
    titleText: String,
    valueText: String,
    cardColor: Int,
    valueColor: Int,
    onClick: () -> Unit
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(cardColor, 19, Color.WHITE, 1)
        elevation = dp(2).toFloat()
        minimumHeight = dp(74)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }

        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            isClickable = false
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, 0)

            addView(text(
                titleText,
                14f,
                brown,
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 11, 15, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(text(
                valueText,
                21f,
                valueColor,
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 15, 22, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}

private fun makeTreasureBadgeStrip(): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val icons = listOf("🗺️", "🐾", "🌟", "👑")
    val names = listOf("探險家", "尋寶王", "星光勇者", "寶藏大師")
    for (i in icons.indices) {
        val unlocked = chestClaims > i
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(
                if (unlocked) 0xFFFFE6A1.toInt() else 0xFFEAE6DF.toInt(),
                18
            )
            addView(text(if (unlocked) icons[i] else "🔒", 24f),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f))
            addView(text(names[i], 10f, 0xFF6C513B.toInt(), true).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 8, 11, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.7f))
        }
        row.addView(badge, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
    }
    return row
}

private fun openStarChest() {
    if (!canOpenStarChest()) return

    chestClaims++
    saveProgress()
    tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
    root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    val badgeTitle = chestBadgeTitle(chestClaims)
    val badgeIcon = chestBadgeIcon(chestClaims)

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(12), dp(18), dp(8))
        addView(text("✨  寶箱打開了！  ✨", 22f, 0xFFE07B00.toInt(), true))
        addView(text("🎁", 68f))
        addView(text(badgeIcon, 58f))
        addView(text("獲得「$badgeTitle」徽章", 21f, brown, true).apply {
            maxLines = 2
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 16, 22, 1, TypedValue.COMPLEX_UNIT_SP
            )
        })
        addView(text("徽章已收藏到「我的徽章」", 14f, 0xFF7B624A.toInt(), false))
    }

    androidx.appcompat.app.AlertDialog.Builder(this)
        .setView(content)
        .setPositiveButton("收下徽章") { _, _ ->
            renderScreen(Screen.TreasureMap(MapFocus.CHEST))
        }
        .setNeutralButton("看全部徽章") { _, _ ->
            navigateTo(Screen.Achievements)
        }
        .show()
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
            Triple("🏆", "毛孩學霸", stars >= 50),
            Triple("🗺️", "小小探險家", chestClaims >= 1),
            Triple("🐾", "毛孩尋寶王", chestClaims >= 2),
            Triple("🌟", "星光勇者", chestClaims >= 3),
            Triple("👑", "寶藏大師", chestClaims >= 4)
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

    // ------------------------- CUSTOM TREASURE MAP VIEW -------------------------


private class TreasureMapView(
    context: Context,
    private val dailyProgress: Int,
    private val chestReady: Boolean,
    private val gameTitles: List<String>,
    private val gameEmojis: List<String>,
    private val onNodeClick: (Int) -> Unit,
    private val onChestClick: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val nodeRects = MutableList(8) { RectF() }
    private val chestRect = RectF()

    // 參考核准預覽圖：由左上一路蜿蜒到右下寶箱。
    private val nodePoints = arrayOf(
        PointF(0.21f, 0.16f),
        PointF(0.52f, 0.25f),
        PointF(0.77f, 0.36f),
        PointF(0.22f, 0.47f),
        PointF(0.51f, 0.56f),
        PointF(0.77f, 0.67f),
        PointF(0.23f, 0.76f),
        PointF(0.51f, 0.85f)
    )
    private val chestPoint = PointF(0.82f, 0.88f)

    private val nodeColors = intArrayOf(
        0xFF5A9FE8.toInt(),
        0xFFF17D7D.toInt(),
        0xFFF3A642.toInt(),
        0xFF77BA55.toInt(),
        0xFF9574D9.toInt(),
        0xFF43B7A5.toInt(),
        0xFF5A9FE8.toInt(),
        0xFFF3A642.toInt()
    )

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "冒險藏寶圖，包含八個可點選遊戲關卡與星星寶箱"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawParchment(canvas, w, h)
        drawRiver(canvas, w, h)
        drawScenery(canvas, w, h)

        val pts = nodePoints.map { PointF(it.x * w, it.y * h) }
        val chest = PointF(chestPoint.x * w, chestPoint.y * h)

        drawRoute(canvas, pts, chest)

        pts.forEachIndexed { index, p ->
            drawNode(canvas, index, p.x, p.y)
        }

        drawChest(canvas, chest.x, chest.y, chestReady)
    }

    private fun drawParchment(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                0xFFFFF4C9.toInt(),
                0xFFF5E4AE.toInt(),
                0xFFFFF0C2.toInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, dp(25f), dp(25f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.color = 0xFFD7A95C.toInt()
        canvas.drawRoundRect(dp(2f), dp(2f), w - dp(2f), h - dp(2f), dp(25f), dp(25f), paint)

        // 上方與下方柔和草地
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB9DB70.toInt()
        canvas.drawOval(-w * 0.08f, h * 0.04f, w * 0.48f, h * 0.30f, paint)
        canvas.drawOval(w * 0.48f, h * 0.01f, w * 1.10f, h * 0.28f, paint)
        canvas.drawOval(-w * 0.10f, h * 0.68f, w * 0.55f, h * 1.03f, paint)
        canvas.drawOval(w * 0.45f, h * 0.68f, w * 1.10f, h * 1.02f, paint)
    }

    private fun drawRiver(canvas: Canvas, w: Float, h: Float) {
        val river = Path().apply {
            moveTo(-dp(8f), h * 0.23f)
            cubicTo(w * 0.18f, h * 0.28f, w * 0.05f, h * 0.47f, w * 0.29f, h * 0.51f)
            cubicTo(w * 0.48f, h * 0.54f, w * 0.43f, h * 0.69f, w * 0.64f, h * 0.69f)
            cubicTo(w * 0.83f, h * 0.68f, w * 0.77f, h * 0.51f, w + dp(8f), h * 0.47f)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(34f)
        paint.color = 0xFF8DD5E7.toInt()
        canvas.drawPath(river, paint)

        paint.strokeWidth = dp(4f)
        paint.color = 0xBFFFFFFF.toInt()
        paint.pathEffect = DashPathEffect(floatArrayOf(dp(12f), dp(10f)), 0f)
        canvas.drawPath(river, paint)
        paint.pathEffect = null

        // 小橋
        drawBridge(canvas, w * 0.60f, h * 0.63f)
    }

    private fun drawBridge(canvas: Canvas, cx: Float, cy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB97637.toInt()
        canvas.drawRoundRect(
            cx - dp(31f), cy - dp(9f),
            cx + dp(31f), cy + dp(9f),
            dp(5f), dp(5f), paint
        )
        paint.color = 0xFFE0A35B.toInt()
        for (i in -2..2) {
            val x = cx + dp(i * 12f)
            canvas.drawRect(x - dp(3f), cy - dp(10f), x + dp(3f), cy + dp(10f), paint)
        }
    }

    private fun drawScenery(canvas: Canvas, w: Float, h: Float) {
        // 山丘 / 石頭
        drawRock(canvas, w * 0.89f, h * 0.41f, 1.0f)
        drawRock(canvas, w * 0.12f, h * 0.69f, 0.72f)
        drawRock(canvas, w * 0.66f, h * 0.80f, 0.58f)

        // 樹木
        val trees = arrayOf(
            0.37f to 0.16f, 0.64f to 0.16f,
            0.31f to 0.37f, 0.87f to 0.56f,
            0.11f to 0.61f, 0.37f to 0.69f
        )
        trees.forEachIndexed { index, pos ->
            drawTree(canvas, pos.first * w, pos.second * h, if (index % 2 == 0) 0.85f else 0.70f)
        }

        // 花朵散點
        val flowers = arrayOf(
            0.08f to 0.33f, 0.43f to 0.34f, 0.68f to 0.28f,
            0.92f to 0.33f, 0.14f to 0.56f, 0.42f to 0.49f,
            0.70f to 0.47f, 0.92f to 0.73f, 0.34f to 0.90f,
            0.64f to 0.91f
        )
        flowers.forEachIndexed { i, pos ->
            drawFlower(canvas, pos.first * w, pos.second * h, i % 3)
        }

        // 小帆船，呼應預覽圖左側
        drawBoat(canvas, w * 0.08f, h * 0.44f)
    }

    private fun drawRoute(canvas: Canvas, pts: List<PointF>, chest: PointF) {
        val all = pts + chest
        val route = Path()
        route.moveTo(all.first().x, all.first().y)
        for (i in 1 until all.size) {
            val a = all[i - 1]
            val b = all[i]
            val midX = (a.x + b.x) / 2f
            route.cubicTo(midX, a.y, midX, b.y, b.x, b.y)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(5f)
        paint.color = 0xFF8B6B43.toInt()
        paint.pathEffect = DashPathEffect(floatArrayOf(dp(7f), dp(8f)), 0f)
        canvas.drawPath(route, paint)
        paint.pathEffect = null

        // 終點箭頭
        val last = all[all.lastIndex - 1]
        val end = all.last()
        val angle = atan2(end.y - last.y, end.x - last.x)
        val ax = end.x - cos(angle) * dp(52f)
        val ay = end.y - sin(angle) * dp(52f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF8B6B43.toInt()
        val arrow = Path().apply {
            moveTo(ax, ay)
            lineTo(
                ax - cos(angle - 0.55f) * dp(14f),
                ay - sin(angle - 0.55f) * dp(14f)
            )
            lineTo(
                ax - cos(angle + 0.55f) * dp(14f),
                ay - sin(angle + 0.55f) * dp(14f)
            )
            close()
        }
        canvas.drawPath(arrow, paint)
    }

    private fun drawNode(canvas: Canvas, index: Int, cx: Float, cy: Float) {
        val cardW = dp(92f)
        val cardH = dp(72f)
        val rect = RectF(
            cx - cardW / 2f,
            cy - cardH / 2f,
            cx + cardW / 2f,
            cy + cardH / 2f
        )
        nodeRects[index].set(rect)

        // 卡片陰影
        paint.style = Paint.Style.FILL
        paint.color = 0x26000000
        canvas.drawRoundRect(
            RectF(rect.left + dp(2f), rect.top + dp(4f), rect.right + dp(2f), rect.bottom + dp(4f)),
            dp(15f), dp(15f), paint
        )

        paint.color = 0xFFFFFCF4.toInt()
        canvas.drawRoundRect(rect, dp(15f), dp(15f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = 0xFFE5C382.toInt()
        canvas.drawRoundRect(rect, dp(15f), dp(15f), paint)

        // 關卡編號圓牌
        paint.style = Paint.Style.FILL
        paint.color = nodeColors[index]
        canvas.drawCircle(rect.left + dp(13f), rect.top + dp(9f), dp(14f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(12f)
        canvas.drawText("${index + 1}", rect.left + dp(13f), rect.top + dp(13f), textPaint)

        // 每日任務已完成節點：金色星星
        if (index < dailyProgress.coerceAtMost(5)) {
            textPaint.textSize = dp(17f)
            textPaint.color = 0xFFFFB800.toInt()
            canvas.drawText("★", rect.right - dp(10f), rect.top + dp(16f), textPaint)
        }

        textPaint.color = 0xFF49301F.toInt()
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = dp(18f)
        val emoji = gameEmojis.getOrNull(index).orEmpty()
        canvas.drawText(emoji, cx, cy - dp(4f), textPaint)

        textPaint.textSize = dp(10.5f)
        val title = gameTitles.getOrNull(index).orEmpty()
        canvas.drawText(title, cx, cy + dp(23f), textPaint)
    }

    private fun drawChest(canvas: Canvas, cx: Float, cy: Float, open: Boolean) {
        val size = dp(86f)
        chestRect.set(
            cx - size * 0.58f, cy - size * 0.58f,
            cx + size * 0.58f, cy + size * 0.58f
        )

        if (open) {
            paint.style = Paint.Style.FILL
            paint.color = 0x44FFD54A
            canvas.drawCircle(cx, cy, dp(55f), paint)
            paint.color = 0x33FFF176
            canvas.drawCircle(cx, cy, dp(68f), paint)
        }

        // 箱身
        paint.style = Paint.Style.FILL
        paint.color = 0xFF9B5124.toInt()
        canvas.drawRoundRect(
            cx - dp(38f), cy - dp(17f),
            cx + dp(38f), cy + dp(28f),
            dp(8f), dp(8f), paint
        )
        paint.color = 0xFFFFB420.toInt()
        canvas.drawRect(cx - dp(7f), cy - dp(17f), cx + dp(7f), cy + dp(28f), paint)
        canvas.drawRect(cx - dp(38f), cy + dp(2f), cx + dp(38f), cy + dp(11f), paint)

        if (open) {
            paint.color = 0xFFB96A31.toInt()
            val lid = Path().apply {
                moveTo(cx - dp(37f), cy - dp(19f))
                lineTo(cx - dp(26f), cy - dp(46f))
                lineTo(cx + dp(31f), cy - dp(46f))
                lineTo(cx + dp(39f), cy - dp(19f))
                close()
            }
            canvas.drawPath(lid, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textPaint.textSize = dp(30f)
            textPaint.color = 0xFFFFC400.toInt()
            canvas.drawText("★", cx, cy - dp(50f), textPaint)
        } else {
            paint.color = 0xFFB96A31.toInt()
            canvas.drawRoundRect(
                cx - dp(38f), cy - dp(38f),
                cx + dp(38f), cy - dp(13f),
                dp(12f), dp(12f), paint
            )
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textPaint.textSize = dp(13f)
            textPaint.color = 0xFF6A3A19.toInt()
            canvas.drawText("${if (chestReady) 30 else ""}", cx, cy + dp(8f), textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = dp(11f)
        textPaint.color = 0xFF5D3B23.toInt()
        canvas.drawText(
            if (open) "點我開寶箱" else "星星寶箱",
            cx, cy + dp(49f), textPaint
        )
    }

    private fun drawTree(canvas: Canvas, x: Float, y: Float, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF8B5A2B.toInt()
        canvas.drawRoundRect(
            x - dp(4f) * scale, y,
            x + dp(4f) * scale, y + dp(20f) * scale,
            dp(2f), dp(2f), paint
        )
        paint.color = 0xFF5FA34A.toInt()
        canvas.drawCircle(x, y - dp(4f) * scale, dp(15f) * scale, paint)
        paint.color = 0xFF76BB58.toInt()
        canvas.drawCircle(x - dp(8f) * scale, y, dp(10f) * scale, paint)
        canvas.drawCircle(x + dp(8f) * scale, y, dp(10f) * scale, paint)
    }

    private fun drawRock(canvas: Canvas, x: Float, y: Float, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF9D9B8E.toInt()
        canvas.drawOval(
            x - dp(14f) * scale, y - dp(8f) * scale,
            x + dp(14f) * scale, y + dp(8f) * scale, paint
        )
        paint.color = 0xFFC4C1AF.toInt()
        canvas.drawOval(
            x - dp(8f) * scale, y - dp(7f) * scale,
            x + dp(4f) * scale, y + dp(2f) * scale, paint
        )
    }

    private fun drawFlower(canvas: Canvas, x: Float, y: Float, variant: Int) {
        val petalColor = when (variant) {
            0 -> 0xFFFFFFFF.toInt()
            1 -> 0xFFFF8AA1.toInt()
            else -> 0xFFFFE06A.toInt()
        }
        paint.style = Paint.Style.FILL
        paint.color = petalColor
        for (i in 0 until 5) {
            val a = i * (Math.PI * 2 / 5)
            val px = x + cos(a).toFloat() * dp(4f)
            val py = y + sin(a).toFloat() * dp(4f)
            canvas.drawCircle(px, py, dp(3f), paint)
        }
        paint.color = 0xFFF1A42B.toInt()
        canvas.drawCircle(x, y, dp(2.5f), paint)
    }

    private fun drawBoat(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF9A5728.toInt()
        val hull = Path().apply {
            moveTo(x - dp(18f), y)
            lineTo(x + dp(18f), y)
            lineTo(x + dp(11f), y + dp(12f))
            lineTo(x - dp(10f), y + dp(12f))
            close()
        }
        canvas.drawPath(hull, paint)

        paint.color = 0xFF7C4925.toInt()
        canvas.drawRect(x - dp(1.5f), y - dp(26f), x + dp(1.5f), y, paint)
        paint.color = 0xFFFFF2C7.toInt()
        val sail = Path().apply {
            moveTo(x, y - dp(25f))
            lineTo(x + dp(16f), y - dp(9f))
            lineTo(x, y - dp(7f))
            close()
        }
        canvas.drawPath(sail, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            for (i in nodeRects.indices) {
                val hit = RectF(nodeRects[i]).apply { inset(-dp(8f), -dp(8f)) }
                if (hit.contains(x, y)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onNodeClick(i)
                    return true
                }
            }
            val chestHit = RectF(chestRect).apply { inset(-dp(10f), -dp(10f)) }
            if (chestHit.contains(x, y)) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onChestClick()
                return true
            }
        }
        return true
    }
}

}
