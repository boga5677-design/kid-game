# 小小腦力樂園 v0.8.0 — 全更新整合版

這一包不是把舊 ZIP 丟進 repo，而是已把更新直接整合進真正會被 Android 建置的 `app/` 原始碼。

## 已整合

### 首頁 / 遊戲
- 首頁保留原本 8 款主要遊戲。
- 「數學小高手」正式改為「數學練習」。
- 遊戲庫保留 12 款：
  找一找、找一樣、顏色圖形、迷宮、連連看、數一數、數學練習、記憶挑戰、
  找不同、規律接龍、比一比、排順序。
- 寶藏地圖仍維持原本 8 個主遊戲節點，避免地圖過度擁擠。
- 保留 Android 實體返回鍵上一頁。
- 保留首頁毛孩圖上緣殘影修正。

### 英文小教室
- 單字學習：分類子頁 → 單字卡。
- 聽力挑戰：分類子頁 → 該分類聽力題。
- 英文測驗：分類子頁 → 該分類測驗題。
- 發音練習：分類子頁 → 該分類跟讀。
- 進題後自動朗讀，並保留重播。
- 單字頁自動念美式英文。
- 數字 0~100 直接顯示阿拉伯數字，不再全部使用同一個 🔢 圖示。

### 英文分類卡切字修正
- 卡片本身：WRAP_CONTENT。
- 標題：WRAP_CONTENT。
- 副標題：WRAP_CONTENT，最多 3 行。
- 範例 Emoji：WRAP_CONTENT + 額外底部 padding。
- 取消造成 Samsung 實機切字的固定 170 / 140 / 58 / 40dp 圖層高度。
- `clipChildren = false`、`clipToPadding = false`。
- 每張分類卡只顯示 3 個範例圖，避免最右側被箭頭裁掉。

### 語音
- TTS 初始化失敗會再嘗試一次。
- 中文與英文語系分開 fallback，不會因缺中文語音包導致英文一起靜音。
- 優先同語系高品質 Voice，並加權 natural / neural / premium / enhanced / network 類型。
- 不再用過高 pitch 模擬小孩，改成接近真人老師的自然音高與稍慢教學速度。

### 答對音效
- 所有一般遊戲答對統一走 `playCorrectSound()`。
- 英文測驗、聽力挑戰、單字「會了」、發音高分都會播放答對音效。
- GitHub Actions 建置前會執行 `scripts/fetch_correct_sound.py`，
  從先前指定的 Chinaz 頁面抓取答對音效；抓不到會停止建置，避免默默換成別的聲音。

### 固定 APK 簽名
- 延續已設定的 4 個 GitHub Repository Secrets：
  - ANDROID_KEYSTORE_BASE64
  - ANDROID_KEYSTORE_PASSWORD
  - ANDROID_KEY_ALIAS
  - ANDROID_KEY_PASSWORD
- 不需要重新設定 Secrets。
- v0.8.0 使用同一把固定簽名金鑰後，可延續直接覆蓋更新。

## 版本
- versionCode = 33
- versionName = 0.8.0

## 建置前快速確認
GitHub 上真正的：
`app/src/main/java/com/boga/kidgame/MainActivity.kt`

應搜尋得到：
- `BUILD_MARKER_v0_8_0`
- `MATH("數學練習"`
- `ODD("找不同"`
- `object GameLibrary`

真正的：
`app/build.gradle.kts`

應搜尋得到：
- `versionCode = 33`
- `versionName = "0.8.0"`

如果 GitHub 上還看到 `versionName = "0.6.8"`，代表上傳位置錯誤，請不要跑 Actions。
