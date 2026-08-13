# 小小腦力樂園 Kids Focus Math Game

適合 5–7 歲幼童的 Android 學習遊戲原型。

## 目前包含
- 找一找：視覺搜尋
- 找一樣：圖形辨識
- 顏色圖形：顏色＋形狀辨識
- 迷宮：拖曳避障
- 連連看：相同圖案配對
- 數一數：1–10 數量概念
- 數學小高手：基礎加減法
- 記憶挑戰：短期記憶

## 建置方式
本專案附 GitHub Actions。
推送到 GitHub 後，到 Actions 執行 **Build Android APK**，
完成後下載 `KidsFocusMath-debug-apk`。

本地環境：
- JDK 17
- Android SDK 35
- Gradle 8.7

執行：
```bash
gradle assembleDebug
```

APK 位置：
`app/build/outputs/apk/debug/app-debug.apk`
