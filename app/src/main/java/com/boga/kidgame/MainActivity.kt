package com.boga.kidgame

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
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

    // BUILD_MARKER_v0_8_3 — GitHub 上搜尋到這行才代表真的在編譯 0.8.0。

    private enum class GameType(val title: String, val emoji: String, val subtitle: String) {
        FIND("找一找", "🔍", "專注搜尋"),
        SAME("找一樣", "🧩", "觀察細節"),
        COLOR("顏色圖形", "🎨", "辨識形狀"),
        MAZE("迷宮", "🌀", "手眼協調"),
        MATCH("連連看", "🔗", "拖曳配對"),
        COUNT("數一數", "🔢", "數量概念"),
        MATH("數學練習", "➕", "基本加減法"),
        MEMORY("記憶挑戰", "🧠", "短期記憶"),
        ODD("找不同", "👀", "觀察差異"),
        PATTERN("規律接龍", "🔁", "邏輯規律"),
        COMPARE("比一比", "⚖️", "大小比較"),
        ORDER("排順序", "🔢", "數字順序")
    }

    private enum class MapFocus { DAILY, CHEST }

    private sealed class Screen {
        object Home : Screen()
        data class Game(val game: GameType) : Screen()
        object Achievements : Screen()
        object GameLibrary : Screen()
        data class TreasureMap(val focus: MapFocus) : Screen()
        object EnglishHome : Screen()
        data class EnglishWords(val category: String? = null) : Screen()
        data class EnglishQuiz(val category: String? = null) : Screen()
        data class EnglishListening(val category: String? = null) : Screen()
        data class EnglishPronunciation(val category: String? = null) : Screen()
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
    private var pendingEnglishNavigation: Screen? = null
    private var pendingListeningWord: String? = null
    private data class PendingTts(
        val text: String,
        val locale: Locale,
        val utteranceId: String
    )
    private var pendingTts: PendingTts? = null
    private var ttsInitRetryCount = 0

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
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, navigationBar.bottom)
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
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            if (ttsInitRetryCount < 1) {
                ttsInitRetryCount++
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        runCatching { tts.shutdown() }
                        tts = TextToSpeech(this, this)
                    }
                }, 700L)
            }
            return
        }

        // 只要 TTS 引擎本身初始化成功就啟用。
        // 語系是否可用改成每次朗讀時分別判斷，避免中文語音包缺少時連英文也一起靜音。
        ttsReady = true
        ttsInitRetryCount = 0
        configureTtsLocale(Locale.US)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onError(utteranceId: String?) {
                when (utteranceId) {
                    "english_menu_title" -> handler.post {
                        val destination = pendingEnglishNavigation
                        pendingEnglishNavigation = null
                        if (destination != null) navigateTo(destination)
                    }
                    "home_game_title" -> handler.post {
                        val game = pendingGameNavigation
                        pendingGameNavigation = null
                        if (game != null) navigateTo(Screen.Game(game))
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                when (utteranceId) {
                    "pronunciation_demo" -> {
                        handler.postDelayed({ beginPronunciationRecognition() }, 500L)
                    }
                    "home_game_title" -> {
                        val game = pendingGameNavigation
                        pendingGameNavigation = null
                        if (game != null) handler.post { navigateTo(Screen.Game(game)) }
                    }
                    "english_menu_title" -> {
                        val destination = pendingEnglishNavigation
                        pendingEnglishNavigation = null
                        if (destination != null) handler.post { navigateTo(destination) }
                    }
                    "english_listening_prompt" -> {
                        val word = pendingListeningWord
                        pendingListeningWord = null
                        if (!word.isNullOrBlank()) {
                            handler.postDelayed({ speakEnglishUS(word) }, 250L)
                        }
                    }
                }
            }
        })

        val queued = pendingTts
        if (queued != null) {
            pendingTts = null
            handler.post {
                speakWithLocale(
                    queued.text,
                    queued.locale,
                    queued.utteranceId,
                    queueIfNotReady = false
                )
            }
        }
    }

    private fun supportedLocale(preferred: Locale): Locale? {
        if (!ttsReady) return null
        val candidates = if (preferred.language == Locale.CHINESE.language) {
            listOf(Locale.TAIWAN, Locale.TRADITIONAL_CHINESE, Locale.CHINESE, Locale.CHINA)
        } else {
            listOf(Locale.US, Locale.ENGLISH)
        }.distinctBy { it.toLanguageTag() }

        return candidates.firstOrNull { candidate ->
            runCatching {
                tts.isLanguageAvailable(candidate) >= TextToSpeech.LANG_AVAILABLE
            }.getOrDefault(false)
        }
    }

    private fun configureTtsLocale(preferred: Locale): Boolean {
        if (!ttsReady) return false
        val locale = supportedLocale(preferred) ?: return false

        val result = runCatching { tts.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) return false

        val isEnglish = locale.language == Locale.ENGLISH.language

        // v0.8.3：所有朗讀統一使用「輕柔女聲優先」。
        // Android TTS 沒有跨引擎統一的 gender 欄位，所以用 Voice 名稱、features、
        // 語系、quality 綜合評分。若裝置有明確 female voice，會強制優先。
        val matchingVoices = tts.voices
            ?.filter { voice ->
                voice.locale.language.equals(locale.language, ignoreCase = true)
            }
            .orEmpty()

        val preferredVoice = matchingVoices.maxByOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            val features = voice.features.orEmpty()
                .joinToString(" ")
                .lowercase(Locale.ROOT)
            val descriptor = "$name $features"

            val explicitFemale =
                "female" in descriptor ||
                "woman" in descriptor ||
                "girl" in descriptor ||
                "feminine" in descriptor ||
                "voice_f" in descriptor ||
                "voice-f" in descriptor ||
                Regex("(^|[-_# .])fem($|[-_# .])").containsMatchIn(descriptor)

            // female 內含 male 字串，因此只有 explicitFemale=false 時才判定 male。
            val explicitMale =
                !explicitFemale &&
                (
                    Regex("(^|[-_# .])male($|[-_# .])").containsMatchIn(descriptor) ||
                    Regex("(^|[-_# .])man($|[-_# .])").containsMatchIn(descriptor) ||
                    "masculine" in descriptor
                )

            val naturalQuality =
                "neural" in descriptor ||
                "natural" in descriptor ||
                "wavenet" in descriptor ||
                "studio" in descriptor ||
                "premium" in descriptor ||
                "enhanced" in descriptor ||
                "high quality" in descriptor

            var score = voice.quality * 24

            // 同國家語音優先，例如 zh-TW / en-US。
            if (locale.country.isNotBlank() &&
                voice.locale.country.equals(locale.country, ignoreCase = true)
            ) {
                score += 2400
            }

            // 女聲權重遠高於其他項目，避免選到品質高但明確是男聲的 voice。
            if (explicitFemale) score += 18000
            if (explicitMale) score -= 18000

            // 女聲之中再挑真人感較佳的 neural / natural 類 voice。
            if (naturalQuality) score += 4200

            // 網路型 voice 通常品質較高，但不強制，離線 voice 仍可正常運作。
            if (voice.isNetworkConnectionRequired || "network" in descriptor) score += 500

            // 舊式 compact / legacy / low-quality voice 降權。
            if ("compact" in descriptor || "legacy" in descriptor || "low quality" in descriptor) {
                score -= 2200
            }

            score
        }

        if (preferredVoice != null) {
            runCatching { tts.voice = preferredVoice }
        }

        // 「輕柔女老師」設定：
        // 不再用過高 pitch 模仿小孩，避免電子感；語速稍慢、音高只小幅提高。
        tts.setSpeechRate(if (isEnglish) 0.83f else 0.85f)
        tts.setPitch(if (isEnglish) 1.045f else 1.055f)
        return true
    }

    private fun speechParams(): Bundle = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.92f)
    }

    private fun speakWithLocale(
        value: String,
        locale: Locale,
        utteranceId: String,
        queueIfNotReady: Boolean = true
    ): Boolean {
        if (value.isBlank()) return false
        if (!ttsReady) {
            if (queueIfNotReady) pendingTts = PendingTts(value, locale, utteranceId)
            return false
        }
        if (!configureTtsLocale(locale)) return false
        tts.stop()
        return tts.speak(
            value,
            TextToSpeech.QUEUE_FLUSH,
            speechParams(),
            utteranceId
        ) == TextToSpeech.SUCCESS
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
        if (screen !is Screen.EnglishPronunciation) pronunciationTarget = ""
        pronunciationResultView = null
        pronunciationScoreView = null
        pronunciationStatusView = null

        when (screen) {
            Screen.Home -> showHome()
            is Screen.Game -> showGame(screen.game)
            Screen.GameLibrary -> showGameLibrary()
            Screen.Achievements -> showAchievements()
            is Screen.TreasureMap -> showTreasureMap(screen.focus)
            Screen.EnglishHome -> showEnglishHome()
            is Screen.EnglishWords -> showEnglishWords(screen.category)
            is Screen.EnglishQuiz -> showEnglishQuiz(screen.category)
            is Screen.EnglishListening -> showEnglishListening(screen.category)
            is Screen.EnglishPronunciation -> showEnglishPronunciation(screen.category)
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

    // v0.8.2：首頁改為「真正一頁」。
    // 不用 ScrollView；依螢幕高度縮放 header / 任務 / 毛孩 / 12款遊戲。
    val screenHeightDp = resources.configuration.screenHeightDp
    val screenWidthDp = resources.configuration.screenWidthDp
    val compact = screenHeightDp < 720
    val veryCompact = screenHeightDp < 640

    val outerPad = if (veryCompact) 5 else if (compact) 7 else 9
    val gap = if (veryCompact) 2 else 3
    val headerHeight = if (veryCompact) 44 else if (compact) 50 else 58
    // v0.8.3：任務卡在 Samsung 實機因字型 ascender/descender 較高，
    // 56/62dp 會把 0/5、0/30 下緣裁掉，因此提高任務區高度；
    // 同時略縮毛孩區，總高度仍維持單頁。
    val missionHeight = if (veryCompact) 72 else if (compact) 78 else 86
    val petHeight = if (veryCompact) 66 else if (compact) 80 else 96
    val englishHeight = if (veryCompact) 40 else if (compact) 44 else 50
    val badgeHeight = if (veryCompact) 38 else if (compact) 42 else 46

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(outerPad), dp(gap), dp(outerPad), dp(gap))
        clipChildren = true
        clipToPadding = true
    }
    root.addView(
        content,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    content.addView(
        makeHomeTopStats(compact = compact || veryCompact),
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(headerHeight)
        )
    )
    content.addSpace(gap)

    val missionRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    missionRow.addView(
        makeHomeMissionCard(
            iconRes = R.drawable.mission_calendar,
            title = "每日任務",
            value = "$dailyProgress/5",
            color = 0xFFF0F8D9.toInt(),
            valueColor = 0xFF58A815.toInt(),
            compact = compact || veryCompact
        ) { navigateTo(Screen.TreasureMap(MapFocus.DAILY)) },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(3)
        }
    )
    missionRow.addView(
        makeHomeMissionCard(
            iconRes = R.drawable.mission_treasure,
            title = "星星寶箱",
            value = if (canOpenStarChest()) "可開啟!" else "${starChestProgress()}/30",
            color = 0xFFFFF1D4.toInt(),
            valueColor = 0xFFE37500.toInt(),
            compact = compact || veryCompact
        ) { navigateTo(Screen.TreasureMap(MapFocus.CHEST)) },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(3)
        }
    )
    content.addView(
        missionRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(missionHeight)
        )
    )
    content.addSpace(if (veryCompact) 3 else 5)

    val pets = ImageView(this).apply {
        setImageResource(R.drawable.home_pets)
        scaleType = ImageView.ScaleType.CENTER_CROP
        adjustViewBounds = false
        background = null
        clipToOutline = false
        contentDescription = "偶貴老師、黑糖老師、熊熊老師"
    }
    content.addView(
        pets,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(petHeight)
        )
    )
    // 毛孩圖片與第一列遊戲卡完全分層，不讓 elevation / 字型超出造成視覺重疊。
    content.addSpace(if (veryCompact) 4 else 6)

    // 12 款遊戲直接整合到首頁：4欄 × 3列。
    // 不再另外放「遊戲庫 12款」入口，因此不存在經典8款在遊戲庫重複顯示的問題。
    val gameGrid = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = true
        clipToPadding = true
    }
    val allGames = GameType.values().toList()
    repeat(3) { rowIndex ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = true
            clipToPadding = true
        }
        repeat(4) { col ->
            val index = rowIndex * 4 + col
            val game = allGames[index]
            row.addView(
                makeCompactHomeGameTile(game, compact = compact || veryCompact),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    val side = dp(if (veryCompact) 1 else 2)
                    setMargins(side, dp(1), side, dp(1))
                }
            )
        }
        gameGrid.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }
    content.addView(
        gameGrid,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )
    content.addSpace(gap)

    val english = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (compact) 10 else 14), 0, dp(10), 0)
        background = rounded(0xFFDFF3FF.toInt(), if (compact) 17 else 21)
        elevation = dp(1).toFloat()
        isClickable = true
        isFocusable = true

        addView(
            text("ABC", if (compact) 18f else 21f, 0xFF2879CB.toInt(), true),
            LinearLayout.LayoutParams(dp(if (compact) 52 else 60), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addView(
            text(
                "英文小教室",
                if (compact) 16f else 18f,
                brown,
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 12, if (compact) 17 else 19, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
        addView(
            text("›", if (compact) 23f else 27f, 0xFF2879CB.toInt(), true),
            LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setOnClickListener { navigateTo(Screen.EnglishHome) }
    }
    content.addView(
        english,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(englishHeight)
        )
    )
    content.addSpace(gap)

    val achievements = text(
        "🏅 我的徽章　　已累積 ⭐ $stars",
        if (compact) 14f else 16f,
        brown,
        true,
        Gravity.START or Gravity.CENTER_VERTICAL
    ).apply {
        maxLines = 1
        setPadding(dp(12), 0, dp(8), 0)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, 11, if (compact) 15 else 17, 1, TypedValue.COMPLEX_UNIT_SP
        )
        background = rounded(0xFFFFE9A9.toInt(), if (compact) 16 else 20)
        setOnClickListener { navigateTo(Screen.Achievements) }
    }
    content.addView(
        achievements,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(badgeHeight)
        )
    )
}

private fun makeHomeTopStats(compact: Boolean): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.WHITE, if (compact) 18 else 24)
        elevation = dp(2).toFloat()
        setPadding(dp(if (compact) 8 else 12), 0, dp(6), 0)

        addView(
            text(
                "小小腦力樂園",
                if (compact) 17f else 20f,
                0xFF4A2E1D.toInt(),
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 13, if (compact) 18 else 21, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )

        addView(
            makeHomeTopCounter(R.drawable.top_star, stars.toString(), compact),
            LinearLayout.LayoutParams(dp(if (compact) 68 else 82), ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // 星星寶箱一律使用真正寶箱 drawable，不用關卡/旗幟圖示。
        addView(
            makeHomeTopCounter(
                R.drawable.mission_treasure,
                if (canOpenStarChest()) "OPEN" else "${starChestProgress()}/30",
                compact
            ),
            LinearLayout.LayoutParams(dp(if (compact) 88 else 108), ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }
}

private fun makeHomeTopCounter(iconRes: Int, value: String, compact: Boolean): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val icon = ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(
            icon,
            LinearLayout.LayoutParams(dp(if (compact) 26 else 32), dp(if (compact) 26 else 32))
        )
        addView(
            text(
                value,
                if (compact) 14f else 17f,
                0xFF3B2417.toInt(),
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                setPadding(dp(2), 0, 0, 0)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 10, if (compact) 15 else 18, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
    }
}

private fun makeHomeMissionCard(
    iconRes: Int,
    title: String,
    value: String,
    color: Int,
    valueColor: Int,
    compact: Boolean,
    onClick: () -> Unit
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (compact) 6 else 9), dp(3), dp(6), dp(3))
        background = rounded(color, if (compact) 18 else 23)
        isClickable = true
        isFocusable = true

        val icon = ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
        }
        addView(
            icon,
            LinearLayout.LayoutParams(
                dp(if (compact) 36 else 46),
                dp(if (compact) 36 else 46)
            )
        )

        val words = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (compact) 6 else 8), dp(3), 0, dp(3))
            clipChildren = false
            clipToPadding = false
        }

        val titleView = text(
            title,
            if (compact) 12.5f else 14.5f,
            0xFF4A2E1D.toInt(),
            true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            maxLines = 1
            includeFontPadding = true
            setPadding(0, dp(1), 0, dp(1))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                if (compact) 13 else 15,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }

        val valueView = text(
            value,
            if (compact) 16.5f else 19f,
            valueColor,
            true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            maxLines = 1
            includeFontPadding = true
            setPadding(0, dp(1), 0, dp(2))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                13,
                if (compact) 18 else 20,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }

        // WRAP_CONTENT 由字型實際高度決定，不再硬切成上下各 50%。
        words.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        words.addView(
            valueView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            words,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }
}

private fun makeCompactHomeGameTile(game: GameType, compact: Boolean): View {
    val tile = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(gameCardColor(game), if (compact) 13 else 17, Color.WHITE, 1)
        elevation = dp(1).toFloat()
        setPadding(dp(2), dp(3), dp(2), dp(3))
        clipChildren = true
        clipToPadding = true
        isClickable = true
        isFocusable = true
        contentDescription = game.title
    }

    val iconSize = if (compact) 31 else 38
    tile.addView(
        makeGameRoundIcon(game),
        LinearLayout.LayoutParams(dp(iconSize), dp(iconSize))
    )

    tile.addView(
        text(
            game.title,
            if (compact) 12f else 14f,
            0xFF4B2C18.toInt(),
            true
        ).apply {
            maxLines = 1
            setPadding(dp(1), 0, dp(1), 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 9, if (compact) 13 else 15, 1, TypedValue.COMPLEX_UNIT_SP
            )
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    tile.setOnClickListener {
        tile.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (ttsReady) {
            pendingGameNavigation = game
            speakWithLocale(game.title, Locale.TAIWAN, "home_game_title")
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
            // 不再加 icon 容器 elevation，避免部分 Samsung 裝置出現「圓形後面又有一個方格」的陰影/殘影。
            elevation = 0f
            minimumHeight = dp(74)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }

            // v0.6.8：圖示與白色圓形底座由同一個 Canvas View 一次畫出。
            // 沒有 FrameLayout、沒有 ImageView 方形背景、沒有第二層底座。
            addView(
                makeMissionRoundIcon(iconRes),
                LinearLayout.LayoutParams(dp(58), dp(58))
            )

            val words = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)

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

    /**
     * 每日任務 / 星星寶箱的圓形圖示。
     * 直接在同一個 View 畫白色圓 + 圖示，不再疊 FrameLayout / ImageView，
     * 因此不會再看到圓形後方的縮小方格或白色殘角。
     */
    private fun makeMissionRoundIcon(iconRes: Int): View = object : View(this) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEDE7DF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        private val drawable by lazy { ContextCompat.getDrawable(this@MainActivity, iconRes)?.mutate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.46f
            canvas.drawCircle(cx, cy, radius, bgPaint)
            canvas.drawCircle(cx, cy, radius, borderPaint)

            val iconSize = (min(width, height) * 0.67f).toInt()
            val left = (width - iconSize) / 2
            val top = (height - iconSize) / 2
            drawable?.setBounds(left, top, left + iconSize, top + iconSize)
            drawable?.draw(canvas)
        }
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
        GameType.ODD -> 0xFFFFE6F0.toInt()
        GameType.PATTERN -> 0xFFE9F6FF.toInt()
        GameType.COMPARE -> 0xFFFFF2CC.toInt()
        GameType.ORDER -> 0xFFE8F4DE.toInt()
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
        GameType.ODD -> 0xFFD94B79.toInt()
        GameType.PATTERN -> 0xFF2785C7.toInt()
        GameType.COMPARE -> 0xFFC17A00.toInt()
        GameType.ORDER -> 0xFF4F8B3A.toInt()
    }

    private fun makeGameTile(game: GameType): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(gameCardColor(game), 22, Color.WHITE, 2)
            elevation = dp(2).toFloat()
            minimumHeight = dp(68)
            setPadding(dp(7), dp(6), dp(7), dp(6))
            isClickable = true
            isFocusable = true
            contentDescription = game.title
        }

        // v0.6.8：真正的「單一圓形底座」。
        // 白色圓形與圖案都由同一個 View 直接繪製；不再把 PNG 方格塞進圓形容器。
        val icon = makeGameRoundIcon(game).apply {
            isClickable = false
            isFocusable = false
            contentDescription = game.title
        }
        // 首頁雙欄寬度有限：圖示縮成 48dp，讓五個字的「數學小高手」有完整空間。
        tile.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        val title = text(
            game.title,
            if (game == GameType.MATH) 14f else 18f,
            0xFF4B2C18.toInt(),
            true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            maxLines = 1
            includeFontPadding = false
            ellipsize = null
            setPadding(0, 0, 0, 0)

            if (game == GameType.MATH) {
                // 固定小一號字級並略縮字寬，保證「數學小高手」五字完整顯示。
                textSize = 14f
                letterSpacing = -0.025f
                setTextScaleX(0.94f)
            } else {
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 13, 19, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
            isClickable = false
            isFocusable = false
        }
        tile.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(6)
            }
        )

        tile.setOnClickListener {
            tile.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // 先完整朗讀卡片名稱，再進遊戲；TTS 未就緒時也會先排隊，避免直接變成靜音。
            pendingGameNavigation = game
            val started = speakWithLocale(
                game.title,
                Locale.TAIWAN,
                "home_game_title"
            )

            handler.postDelayed({
                if (pendingGameNavigation == game) {
                    pendingGameNavigation = null
                    navigateTo(Screen.Game(game))
                }
            }, if (started) 2600L else 1200L)
        }

        return tile
    }

    /**
     * 首頁 8 個遊戲圖示：單一白色圓形底座 + 直接繪製圖案。
     * 完全不使用 game_*.png，所以不可能再出現 PNG 原本的方框、白角、殘影。
     */
    private fun makeGameRoundIcon(game: GameType): View = object : View(this) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEDE7DF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val s = min(width, height).toFloat()
            val r = s * 0.46f

            // 唯一一層白色圓形底座
            canvas.drawCircle(cx, cy, r, bg)
            canvas.drawCircle(cx, cy, r, border)

            when (game) {
                GameType.FIND -> drawFind(canvas, cx, cy, s)
                GameType.SAME -> drawSame(canvas, cx, cy, s)
                GameType.COLOR -> drawColor(canvas, cx, cy, s)
                GameType.MAZE -> drawMaze(canvas, cx, cy, s)
                GameType.MATCH -> drawMatch(canvas, cx, cy, s)
                GameType.COUNT -> drawLabel(canvas, "123", cx, cy, s, 0xFF07969B.toInt(), 0.34f)
                GameType.MATH -> drawLabel(canvas, "1+2", cx, cy, s, 0xFF2E6FD8.toInt(), 0.29f)
                GameType.MEMORY -> drawMemory(canvas, cx, cy, s)
                GameType.ODD -> drawOdd(canvas, cx, cy, s)
                GameType.PATTERN -> drawPattern(canvas, cx, cy, s)
                GameType.COMPARE -> drawLabel(canvas, ">", cx, cy, s, 0xFFC17A00.toInt(), 0.48f)
                GameType.ORDER -> drawLabel(canvas, "1·2·3", cx, cy, s, 0xFF4F8B3A.toInt(), 0.22f)
            }
        }

        private fun drawFind(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.075f
            p.color = 0xFF138AC4.toInt()
            c.drawCircle(cx - s * 0.06f, cy - s * 0.05f, s * 0.20f, p)
            c.drawLine(cx + s * 0.08f, cy + s * 0.10f, cx + s * 0.25f, cy + s * 0.27f, p)
        }

        private fun drawSame(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.FILL
            p.color = 0xFFF63E63.toInt()
            c.drawCircle(cx - s * 0.10f, cy, s * 0.15f, p)
            p.color = 0xFFF04E70.toInt()
            c.drawCircle(cx + s * 0.10f, cy, s * 0.15f, p)
        }

        private fun drawColor(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.FILL
            p.color = 0xFF7048DC.toInt()
            c.drawCircle(cx - s * 0.13f, cy + s * 0.01f, s * 0.13f, p)
            p.color = 0xFFFFAD21.toInt()
            val a = s * 0.13f
            c.drawRoundRect(
                RectF(cx + s * 0.02f, cy - a, cx + s * 0.02f + a * 2, cy + a),
                s * 0.025f,
                s * 0.025f,
                p
            )
        }

        private fun drawMaze(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.SQUARE
            p.strokeJoin = Paint.Join.MITER
            p.strokeWidth = s * 0.075f
            p.color = 0xFF2D72D8.toInt()
            val path = Path().apply {
                moveTo(cx - s * 0.22f, cy - s * 0.20f)
                lineTo(cx + s * 0.18f, cy - s * 0.20f)
                lineTo(cx + s * 0.18f, cy + s * 0.02f)
                lineTo(cx - s * 0.03f, cy + s * 0.02f)
                lineTo(cx - s * 0.03f, cy + s * 0.20f)
                lineTo(cx + s * 0.23f, cy + s * 0.20f)
            }
            c.drawPath(path, p)
        }

        private fun drawMatch(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.075f
            p.color = 0xFF674FC5.toInt()
            c.drawLine(cx - s * 0.17f, cy - s * 0.10f, cx + s * 0.17f, cy + s * 0.11f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFF53D69.toInt()
            c.drawCircle(cx - s * 0.18f, cy - s * 0.11f, s * 0.105f, p)
            c.drawCircle(cx + s * 0.18f, cy + s * 0.12f, s * 0.105f, p)
        }

        private fun drawMemory(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.FILL
            p.color = 0xFFF25276.toInt()
            c.drawCircle(cx - s * 0.12f, cy + s * 0.03f, s * 0.13f, p)
            c.drawCircle(cx, cy - s * 0.08f, s * 0.15f, p)
            c.drawCircle(cx + s * 0.13f, cy + s * 0.04f, s * 0.13f, p)
        }

        private fun drawOdd(c: Canvas, cx: Float, cy: Float, s: Float) {
            p.style = Paint.Style.FILL
            p.color = 0xFF4D8FE8.toInt()
            c.drawCircle(cx - s * 0.16f, cy, s * 0.10f, p)
            c.drawCircle(cx, cy, s * 0.10f, p)
            p.color = 0xFFF25276.toInt()
            c.drawCircle(cx + s * 0.16f, cy, s * 0.10f, p)
        }

        private fun drawPattern(c: Canvas, cx: Float, cy: Float, s: Float) {
            val xs = floatArrayOf(-0.24f, -0.08f, 0.08f, 0.24f)
            xs.forEachIndexed { index, offset ->
                p.style = Paint.Style.FILL
                p.color = if (index % 2 == 0) 0xFFF25276.toInt() else 0xFF4D8FE8.toInt()
                c.drawCircle(cx + s * offset, cy, s * 0.075f, p)
            }
        }

        private fun drawLabel(
            c: Canvas,
            label: String,
            cx: Float,
            cy: Float,
            s: Float,
            color: Int,
            textScale: Float
        ) {
            p.style = Paint.Style.FILL
            p.color = color
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = s * textScale
            val fm = p.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            c.drawText(label, cx, baseline, p)
        }
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
            GameType.ODD -> buildOdd(gameArea, q, feedback)
            GameType.PATTERN -> buildPattern(gameArea, q, feedback)
            GameType.COMPARE -> buildCompare(gameArea, q, feedback)
            GameType.ORDER -> buildOrder(gameArea, q, feedback)
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
        if (currentQuestion.isBlank()) return

        if (!ttsReady) {
            pendingTts = PendingTts(
                currentQuestion,
                Locale.TAIWAN,
                if (autoRead) "question_auto" else "question_replay"
            )
            return
        }
        if (!configureTtsLocale(Locale.TAIWAN)) return

        if (!autoRead) {
            // 使用者按「重播題目」時要立即重播。
            tts.stop()
        }

        tts.speak(
            currentQuestion,
            if (autoRead) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            speechParams(),
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

    private fun playCorrectSound() {
        runCatching {
            val player = MediaPlayer.create(this, R.raw.correct_answer_source)
            if (player == null) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
                return
            }
            player.setVolume(1.0f, 1.0f)
            player.setOnCompletionListener { mp -> mp.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.start()
        }.onFailure {
            // 極少數裝置若 MediaPlayer 建立失敗，仍保留系統提示音作 fallback。
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
        }
    }

    private fun success(feedbackView: TextView, message: String = "答對了！ ⭐ +1") {
        stars++
        recordCompletedChallenge()
        saveProgress()
        playCorrectSound()
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



    // ------------------------- ODD ONE OUT -------------------------

    private fun buildOdd(area: FrameLayout, q: TextView, fb: TextView) {
        setQuestion(q, "找出不一樣的圖案。")

        val groups = listOf(
            "🍎" to "🍊",
            "🐶" to "🐱",
            "⭐" to "❤️",
            "🔵" to "🟢",
            "▲" to "●"
        )
        val pair = groups.random()
        val oddIndex = Random.nextInt(6)

        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            setPadding(dp(10), dp(18), dp(10), dp(18))
        }
        area.addView(grid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        repeat(6) { index ->
            val symbol = if (index == oddIndex) pair.second else pair.first
            val b = optionButton(symbol, Color.WHITE)
            b.setOnClickListener {
                if (index == oddIndex) {
                    success(fb, "找到了！ ⭐ +1")
                    handler.postDelayed({ showGame(GameType.ODD) }, 750)
                } else {
                    wrong(fb, "這個和大部分一樣，再看看～")
                }
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

    // ------------------------- PATTERN -------------------------

    private fun buildPattern(area: FrameLayout, q: TextView, fb: TextView) {
        val patterns = listOf(
            Pair(listOf("🔴", "🔵", "🔴", "🔵"), "🔴"),
            Pair(listOf("⭐", "❤️", "⭐", "❤️"), "⭐"),
            Pair(listOf("🟢", "🟢", "🟡", "🟢", "🟢", "🟡"), "🟢"),
            Pair(listOf("▲", "●", "▲", "●"), "▲")
        )
        val picked = patterns.random()
        val sequence = picked.first
        val answer = picked.second
        setQuestion(q, "看看規律，下一個應該是什麼？")

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        area.addView(wrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val line = text(sequence.joinToString("  ") + "  ?", 32f, brown, true)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            line, 22, 36, 1, TypedValue.COMPLEX_UNIT_SP
        )
        wrap.addView(line, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)
        ))

        val pool = (sequence.distinct() + listOf("🟡", "🟢", "🔵", "❤️", "●"))
            .distinct()
            .filter { it != answer }
            .shuffled()
            .take(2)
            .plus(answer)
            .shuffled()

        pool.forEach { symbol ->
            val b = optionButton(symbol, 0xFFF7FBFF.toInt())
            b.setOnClickListener {
                if (symbol == answer) {
                    success(fb, "規律答對了！ ⭐ +1")
                    handler.postDelayed({ showGame(GameType.PATTERN) }, 750)
                } else wrong(fb)
            }
            wrap.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
    }

    // ------------------------- COMPARE -------------------------

    private fun buildCompare(area: FrameLayout, q: TextView, fb: TextView) {
        var left = Random.nextInt(1, 21)
        var right = Random.nextInt(1, 21)
        while (right == left) right = Random.nextInt(1, 21)

        val askBigger = Random.nextBoolean()
        val answer = if (askBigger) max(left, right) else min(left, right)
        setQuestion(q, if (askBigger) "哪一個數字比較大？" else "哪一個數字比較小？")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(28), dp(10), dp(28))
        }
        area.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        listOf(left, right).forEachIndexed { index, number ->
            val b = optionButton(
                number.toString(),
                if (index == 0) 0xFFE4EFFF.toInt() else 0xFFFFE9D7.toInt()
            )
            b.setOnClickListener {
                if (number == answer) {
                    success(fb, "比對正確！ ⭐ +1")
                    handler.postDelayed({ showGame(GameType.COMPARE) }, 700)
                } else wrong(fb)
            }
            row.addView(b, LinearLayout.LayoutParams(0, dp(190), 1f).apply {
                setMargins(dp(7), dp(7), dp(7), dp(7))
            })
        }
    }

    // ------------------------- ORDER -------------------------

    private fun buildOrder(area: FrameLayout, q: TextView, fb: TextView) {
        val start = Random.nextInt(1, 14)
        val answer = start + 3
        setQuestion(q, "照數字順序，下一個是多少？")

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(18), dp(10), dp(18))
        }
        area.addView(wrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val sequence = text("$start　${start + 1}　${start + 2}　?", 38f, brown, true)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            sequence, 28, 42, 1, TypedValue.COMPLEX_UNIT_SP
        )
        wrap.addView(sequence, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
        ))

        val options = linkedSetOf(answer)
        while (options.size < 3) {
            val candidate = (answer + Random.nextInt(-2, 3)).coerceAtLeast(0)
            if (candidate != answer) options += candidate
        }
        options.shuffled().forEach { number ->
            val b = optionButton(number.toString(), 0xFFE8F4DE.toInt())
            b.setOnClickListener {
                if (number == answer) {
                    success(fb, "順序答對了！ ⭐ +1")
                    handler.postDelayed({ showGame(GameType.ORDER) }, 700)
                } else wrong(fb)
            }
            wrap.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
    }


    private fun showGameLibrary() {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFF8E9.toInt())

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        page.addView(makePageHeader("遊戲庫已整合"))
        page.addView(
            text(
                "🎮 12 款遊戲已全部整合到首頁\n不再重複顯示相同遊戲",
                20f,
                brown,
                true
            ).apply {
                maxLines = 3
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
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
            Triple("🎧", "聽力挑戰", Screen.EnglishListening(null)),
            Triple("✅", "英文測驗", Screen.EnglishQuiz(null)),
            Triple("🎤", "發音練習", Screen.EnglishPronunciation(null))
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
                    setOnClickListener {
                        speakEnglishMenuThenNavigate(item.second, item.third)
                    }
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
        handler.postDelayed({ speakChinese("選一個主題開始學習") }, 500L)

        EnglishWordBank.categories.forEach { (categoryName, fallbackIcon) ->
            val categoryWords = EnglishWordBank.wordsIn(categoryName)
            val heroEmoji = categoryWords.firstOrNull()?.emoji ?: fallbackIcon

            // v0.7.8：右側空間有限，只展示 3 個範例，避免第 4 個被箭頭/邊界切掉。
            val sampleEmoji = categoryWords.take(3).joinToString("   ") { it.emoji }
            val description = EnglishWordBank.categoryDescription(categoryName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(12), dp(14))
                background = rounded(Color.WHITE, 26)
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true

                // 不裁切任何子 View。實際高度由內容決定，不再用固定 170dp。
                clipChildren = false
                clipToPadding = false
                minimumHeight = dp(180)
            }

            val iconBox = text(heroEmoji, 50f, Color.BLACK, false).apply {
                background = rounded(0xFFFFE3EC.toInt(), 23)
                includeFontPadding = true
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = false
                isFocusable = false
            }
            card.addView(
                iconBox,
                LinearLayout.LayoutParams(dp(106), dp(106)).apply {
                    marginEnd = dp(14)
                }
            )

            val words = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(3), 0, dp(3))
            }

            val title = text(
                categoryName,
                24f,
                0xFF19191F.toInt(),
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                includeFontPadding = true
                setPadding(dp(2), dp(2), dp(2), dp(4))
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 19, 25, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }

            val desc = text(
                description,
                15f,
                0xFF303038.toInt(),
                false,
                Gravity.START
            ).apply {
                // 關鍵修正：不再給副標題固定 58dp，高度依 1~3 行文字自行撐開。
                maxLines = 3
                minLines = 1
                includeFontPadding = true
                setPadding(dp(2), dp(5), dp(2), dp(6))
                setLineSpacing(dp(3).toFloat(), 1.05f)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 12, 16, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }

            val samples = text(
                sampleEmoji,
                24f,
                0xFF19191F.toInt(),
                false,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                maxLines = 1
                includeFontPadding = true
                // Samsung Emoji 字型的下緣需要額外空間，避免圖案底部被切。
                setPadding(dp(2), dp(7), dp(2), dp(12))
                minHeight = dp(52)
            }

            // 三個 View 全部 WRAP_CONTENT；沒有任何互相覆蓋的固定圖層。
            words.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            words.addView(
                desc,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            words.addView(
                samples,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            card.addView(
                words,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val arrow = text("›", 40f, 0xFF17171B.toInt(), true).apply {
                isClickable = false
                isFocusable = false
            }
            card.addView(
                arrow,
                LinearLayout.LayoutParams(dp(34), dp(72)).apply {
                    marginStart = dp(4)
                }
            )

            card.setOnClickListener {
                speakEnglishMenuThenNavigate(
                    categoryName,
                    Screen.EnglishWords(categoryName)
                )
            }

            // 卡片本身也使用 WRAP_CONTENT；文字變兩行/三行時整張卡會一起變高。
            content.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            content.addSpace(12)
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
                playCorrectSound()
                knownButton.text = "✅ 已會了　⭐ +1"
            } else {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            }
        }

        renderWord(false)
        handler.postDelayed({ speakEnglishUS(currentWord().english) }, 500L)
    }

    /**
     * 聽力挑戰 / 英文測驗 / 發音練習的真正「子分頁」。
     * 先選分類，再進入題目，避免四張英文卡只剩一個平面頁面。
     */
    private fun showEnglishGameCategoryList(
        pageTitle: String,
        instruction: String,
        destination: (String) -> Screen
    ) {
        root.removeAllViews()
        root.setBackgroundColor(0xFFFFFAF0.toInt())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll)

        content.addView(makePageHeader(pageTitle))
        content.addSpace(8)

        val intro = text(
            instruction, 18f, brown, true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            background = rounded(0xFFFFF1C9.toInt(), 19)
            minHeight = dp(52)
        }
        content.addView(intro, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addSpace(10)
        handler.postDelayed({ speakChinese(instruction) }, 500L)

        EnglishWordBank.categories.forEach { (categoryName, fallbackIcon) ->
            val categoryWords = EnglishWordBank.wordsIn(categoryName)
            val heroEmoji = categoryWords.firstOrNull()?.emoji ?: fallbackIcon
            val sampleEmoji = categoryWords.take(3).joinToString("   ") { it.emoji }
            val description = EnglishWordBank.categoryDescription(categoryName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(12), dp(14))
                background = rounded(Color.WHITE, 24)
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
                contentDescription = categoryName
                clipChildren = false
                clipToPadding = false
                minimumHeight = dp(176)
            }

            val iconBox = text(heroEmoji, 48f).apply {
                background = rounded(0xFFFFE3EC.toInt(), 22)
                includeFontPadding = true
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = false
                isFocusable = false
            }
            card.addView(
                iconBox,
                LinearLayout.LayoutParams(dp(102), dp(102)).apply {
                    marginEnd = dp(14)
                }
            )

            val words = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            words.addView(
                text(
                    categoryName,
                    23f,
                    0xFF19191F.toInt(),
                    true,
                    Gravity.START or Gravity.CENTER_VERTICAL
                ).apply {
                    maxLines = 1
                    includeFontPadding = true
                    setPadding(dp(2), dp(2), dp(2), dp(4))
                    isClickable = false
                    isFocusable = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            words.addView(
                text(
                    description,
                    15f,
                    0xFF303038.toInt(),
                    false,
                    Gravity.START
                ).apply {
                    maxLines = 3
                    includeFontPadding = true
                    setPadding(dp(2), dp(5), dp(2), dp(6))
                    setLineSpacing(dp(3).toFloat(), 1.05f)
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        this, 12, 16, 1, TypedValue.COMPLEX_UNIT_SP
                    )
                    isClickable = false
                    isFocusable = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            words.addView(
                text(
                    sampleEmoji,
                    24f,
                    0xFF19191F.toInt(),
                    false,
                    Gravity.START or Gravity.CENTER_VERTICAL
                ).apply {
                    maxLines = 1
                    includeFontPadding = true
                    setPadding(dp(2), dp(6), dp(2), dp(9))
                    minHeight = dp(48)
                    isClickable = false
                    isFocusable = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            card.addView(
                words,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            card.addView(
                text("›", 38f, 0xFF17171B.toInt(), true).apply {
                    isClickable = false
                    isFocusable = false
                },
                LinearLayout.LayoutParams(dp(34), dp(70)).apply {
                    marginStart = dp(4)
                }
            )

            card.setOnClickListener {
                speakEnglishMenuThenNavigate(categoryName, destination(categoryName))
            }

            content.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            content.addSpace(11)
        }
    }

    private fun showEnglishQuiz(category: String?) {
        if (category == null) {
            showEnglishGameCategoryList(
                "英文測驗",
                "選一個主題開始測驗"
            ) { selected -> Screen.EnglishQuiz(selected) }
            return
        }
        root.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)
        page.addView(makePageHeader("英文測驗・$category"))
        page.addSpace(8)
        val quizPool = EnglishWordBank.wordsIn(category).ifEmpty { EnglishWordBank.all }

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
            val target = quizPool.random()
            val options = linkedSetOf(target)
            while (options.size < 3) {
                options.add((quizPool + EnglishWordBank.all).random())
            }
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
                            playCorrectSound()
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

    private fun showEnglishListening(category: String?) {
        if (category == null) {
            showEnglishGameCategoryList(
                "聽力挑戰",
                "選一個主題開始聽力挑戰"
            ) { selected -> Screen.EnglishListening(selected) }
            return
        }

        root.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(12))
            setBackgroundColor(0xFFF7FBFF.toInt())
        }
        root.addView(page)
        page.addView(makePageHeader("聽力挑戰・$category"))
        page.addSpace(8)
        val listeningPool = EnglishWordBank.wordsIn(category).ifEmpty { EnglishWordBank.all }
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
        var target = listeningPool.first()
        var firstRound = true

        fun nextRound() {
            target = listeningPool.random()
            val options = linkedSetOf(target)
            while (options.size < 4) {
                options.add((listeningPool + EnglishWordBank.all).random())
            }
            grid.removeAllViews()
            options.shuffled().forEach { opt ->
                val b = optionButton(opt.emoji, Color.WHITE).apply {
                    textSize = 48f
                    setOnClickListener {
                        if (opt == target) {
                            stars++
                            recordCompletedChallenge()
                            saveProgress()
                            playCorrectSound()
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
            if (firstRound) {
                firstRound = false
                handler.postDelayed({ speakListeningPromptThenWord(target.english) }, 500L)
            } else {
                handler.postDelayed({ speakEnglishUS(target.english) }, 500L)
            }
        }
        replay.setOnClickListener { speakEnglishUS(target.english) }
        nextRound()
    }

    private fun showEnglishPronunciation(category: String?) {
        if (category == null) {
            showEnglishGameCategoryList(
                "發音練習",
                "選一個主題開始發音練習"
            ) { selected -> Screen.EnglishPronunciation(selected) }
            return
        }

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

        page.addView(makePageHeader("發音練習・$category"))
        page.addSpace(8)

        val pronunciationPool = EnglishWordBank.wordsIn(category).ifEmpty { EnglishWordBank.all }
        val targetWord = pronunciationPool.random()
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

    private fun speakEnglishMenuThenNavigate(label: String, destination: Screen) {
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        pendingEnglishNavigation = destination
        val started = speakWithLocale(
            label,
            Locale.TAIWAN,
            "english_menu_title"
        )
        handler.postDelayed({
            if (pendingEnglishNavigation == destination) {
                pendingEnglishNavigation = null
                navigateTo(destination)
            }
        }, if (started) 2200L else 700L)
    }

    private fun speakListeningPromptThenWord(word: String) {
        pendingListeningWord = word
        val started = speakWithLocale(
            "聽單字，選出正確圖片",
            Locale.TAIWAN,
            "english_listening_prompt"
        )
        if (!started) {
            handler.postDelayed({
                if (pendingListeningWord == word) {
                    pendingListeningWord = null
                    speakEnglishUS(word)
                }
            }, 700L)
        }
    }

    private fun speakChinese(text: String) {
        speakWithLocale(text, Locale.TAIWAN, "zh_question")
    }

    private fun speakEnglishUS(text: String) {
        englishAccent = Locale.US
        speakWithLocale(text, Locale.US, "english_word")
    }

    private fun speakEnglish(text: String, locale: Locale = Locale.US) {
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
        if (pronunciationTarget.isBlank()) return
        pronunciationStatusView?.text = "先聽示範…"
        pronunciationResultView?.text = "辨識內容：—"
        pronunciationScoreView?.text = "發音分數：—"
        englishAccent = Locale.US
        if (!ttsReady) {
            pronunciationStatusView?.text = "語音引擎準備中，請再按一次跟讀"
            return
        }
        if (!configureTtsLocale(Locale.US)) {
            pronunciationStatusView?.text = "請先安裝美式英文語音資料"
            return
        }
        // 跟讀示範再慢一點，但音高保持自然，避免機械/卡通感。
        tts.setSpeechRate(0.76f)
        tts.setPitch(1.045f)
        tts.stop()
        tts.speak(
            pronunciationTarget,
            TextToSpeech.QUEUE_FLUSH,
            speechParams(),
            "pronunciation_demo"
        )
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
                        playCorrectSound()
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
    if (focus == MapFocus.CHEST) {
        showStarChestRewards()
    } else {
        showDailyMissionPage()
    }
}

private fun showDailyMissionPage() {
    root.removeAllViews()
    root.setBackgroundColor(0xFFF8FCEB.toInt())

    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(14))
    }
    root.addView(
        page,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    page.addView(makePageHeader("每日任務"))
    page.addSpace(8)

    page.addView(
        text(
            "✅ 今天完成 $dailyProgress / 5 個挑戰",
            22f,
            0xFF4D8E1D.toInt(),
            true
        ).apply {
            background = rounded(0xFFEAF7CF.toInt(), 22)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(78)
        )
    )
    page.addSpace(8)

    val tasks = listOf(
        "完成任一腦力遊戲",
        "完成一題英文測驗",
        "完成一題聽力挑戰",
        "學會一個英文單字",
        "完成一次發音練習"
    )

    tasks.forEachIndexed { index, task ->
        val done = index < dailyProgress
        page.addView(
            text(
                if (done) "✅ $task" else "⬜ $task",
                18f,
                if (done) 0xFF4C8F27.toInt() else brown,
                true,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                setPadding(dp(14), 0, dp(10), 0)
                background = rounded(
                    if (done) 0xFFE8F6D7.toInt() else Color.WHITE,
                    18
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
        )
    }
}

private fun showStarChestRewards() {
    root.removeAllViews()
    root.setBackgroundColor(0xFFFFF8E9.toInt())

    val screenHeightDp = resources.configuration.screenHeightDp
    val compact = screenHeightDp < 720

    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(if (compact) 8 else 12), dp(6), dp(if (compact) 8 else 12), dp(8))
    }
    root.addView(
        page,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    page.addView(
        makePageHeader("星星寶箱"),
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(if (compact) 48 else 56)
        )
    )
    page.addSpace(5)

    val progress = starChestProgress()
    val hero = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = rounded(0xFFFFE7A8.toInt(), 22, 0x33E6A200, 1)

        val chest = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.mission_treasure)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(
            chest,
            LinearLayout.LayoutParams(
                dp(if (compact) 72 else 92),
                dp(if (compact) 72 else 92)
            )
        )

        val words = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        words.addView(
            text(
                "收集星星開寶箱",
                if (compact) 18f else 21f,
                brown,
                true,
                Gravity.START
            ).apply { maxLines = 1 }
        )
        words.addView(
            text(
                "完成遊戲與英文挑戰，累積星星解鎖獎勵",
                if (compact) 12f else 14f,
                0xFF7A654E.toInt(),
                false,
                Gravity.START
            ).apply { maxLines = 2 }
        )
        words.addView(
            text(
                "⭐ $progress / 30",
                if (compact) 24f else 28f,
                0xFFE57900.toInt(),
                true,
                Gravity.START
            )
        )
        addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }
    page.addView(
        hero,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(if (compact) 96 else 118)
        )
    )
    page.addSpace(6)

    val rewards = listOf(
        Triple(5, "小徽章", 0xFFE8F7D8.toInt()),
        Triple(10, "貼紙", 0xFFFFECD6.toInt()),
        Triple(15, "驚喜卡", 0xFFF0E2FF.toInt()),
        Triple(20, "彩色徽章", 0xFFE1F0FF.toInt()),
        Triple(25, "星星獎勵", 0xFFFFE3EA.toInt()),
        Triple(30, "終極寶箱", 0xFFFFE8A4.toInt())
    )

    val grid = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    rewards.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        pair.forEachIndexed { index, reward ->
            val threshold = reward.first
            val label = reward.second
            val color = reward.third
            val unlocked = progress >= threshold

            val card = makeStarChestRewardCard(
                threshold = threshold,
                label = label,
                color = color,
                unlocked = unlocked,
                compact = compact
            ) {
                when {
                    threshold == 30 && canOpenStarChest() -> openStarChest()
                    unlocked -> {
                        playCorrectSound()
                        toastFeedback("已解鎖：$label")
                    }
                    else -> toastFeedback("再收集 ${threshold - progress} 顆星即可解鎖「$label」")
                }
            }

            row.addView(
                card,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    if (index == 0) marginEnd = dp(4) else marginStart = dp(4)
                }
            )
        }
        grid.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        )
    }

    page.addView(
        grid,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    page.addSpace(4)
    page.addView(
        text(
            "🏅 我的徽章　　已累積 ⭐ $stars",
            if (compact) 14f else 16f,
            brown,
            true,
            Gravity.START or Gravity.CENTER_VERTICAL
        ).apply {
            setPadding(dp(12), 0, dp(8), 0)
            background = rounded(0xFFFFE9A9.toInt(), 18)
            setOnClickListener { navigateTo(Screen.Achievements) }
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(if (compact) 42 else 48)
        )
    )
}

private fun makeStarChestRewardCard(
    threshold: Int,
    label: String,
    color: Int,
    unlocked: Boolean,
    compact: Boolean,
    onClick: () -> Unit
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(4), dp(5), dp(4))
        background = rounded(color, if (compact) 17 else 22, Color.WHITE, 1)
        elevation = dp(1).toFloat()
        isClickable = true
        isFocusable = true

        addView(
            text(
                "${threshold}星",
                if (compact) 13f else 15f,
                if (unlocked) 0xFFE27900.toInt() else 0xFF777777.toInt(),
                true
            ).apply {
                maxLines = 1
                setPadding(0, 0, 0, 0)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.55f
            )
        )

        // 每一個獎勵都使用「寶箱」drawable，不再使用關卡節點/旗幟。
        val chest = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.mission_treasure)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = if (unlocked) 1f else 0.55f
        }
        addView(
            chest,
            LinearLayout.LayoutParams(
                dp(if (compact) 48 else 62),
                0,
                1.25f
            )
        )

        addView(
            text(
                if (unlocked) label else "🔒 $label",
                if (compact) 12f else 14f,
                brown,
                true
            ).apply {
                maxLines = 1
                setPadding(dp(2), 0, dp(2), 0)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 9, if (compact) 13 else 15, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.7f
            )
        )

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }
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

        val iconBubble = FrameLayout(this@MainActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFBFBFB.toInt())
            }
            elevation = dp(1).toFloat()
            isClickable = false
            isFocusable = false
        }
        val statusIcon = ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
        }
        iconBubble.addView(statusIcon, FrameLayout.LayoutParams(dp(39), dp(39), Gravity.CENTER))
        addView(iconBubble, LinearLayout.LayoutParams(dp(50), dp(50)))

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
    playCorrectSound()
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
