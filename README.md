# 小小腦力樂園 v0.8.4

## 本版針對實機回報修正

### 毛孩完整顯示
- `home_pets.png` 原始尺寸 941 × 388，比例約 2.43:1。
- 上一版使用 `CENTER_CROP` 且高度只有 66 / 80 / 96dp，所以會裁掉耳朵、腳與姓名牌。
- v0.8.4 改為 `FIT_CENTER + adjustViewBounds=true`。
- 毛孩區高度依螢幕寬度與原圖比例計算：
  - veryCompact：112~128dp
  - compact：128~146dp
  - 一般：142~160dp
- 遊戲圖示略縮，讓首頁仍維持單頁。

### 輕柔女聲
- 不再只靠「female 字串評分」。
- App 啟動先嘗試 Google Speech Services (`com.google.android.tts`)。
- Google TTS 不可用才退回手機預設 TTS。
- 中文優先：
  1. 明確 female / #female_ voice
  2. `cmn-tw-x-ctc*` 女聲候選
  3. 同語系最高品質 voice
- 美式英文優先：
  1. `#female_*`
  2. `en-us-x-sfg*`
  3. 同語系最高品質 voice
- 若真的找不到任何女聲候選，pitch 會提高至：
  - 中文 1.13
  - 英文 1.12
  讓聲線確實偏向女聲，而不是上一版聽起來幾乎沒變。
- 語速：
  - 中文 0.86
  - 英文 0.84
  - 跟讀 0.76
- 音量 0.95。

### 其他
- 12 款遊戲維持首頁單頁整合。
- 星星寶箱仍為寶箱獎勵制。
- 數學名稱維持「數學練習」。
- 固定 APK 簽名保留，原本四個 GitHub Secrets 不需重設。

## Version
- versionCode = 37
- versionName = 0.8.4
