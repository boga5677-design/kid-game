# 小小腦力樂園 v0.8.1 — Build 修正版

## 本次修正

GitHub Actions 的錯誤：
`MainActivity.kt:419:35 Unresolved reference: showGameLibrary`

原因是 v0.8.0 整合時保留了：
`Screen.GameLibrary -> showGameLibrary()`

但漏掉 `private fun showGameLibrary()` 函式本體。

v0.8.1 已把遊戲庫函式完整補回，保留：
- 12 款遊戲
- 經典遊戲 8 款
- 新遊戲 4 款
- 數學名稱為「數學練習」
- 英文分類/語音/切字/阿拉伯數字/答對音效
- 固定 APK 簽名

另外修掉 `app/build.gradle.kts` 的 `Unnecessary non-null assertion (!!)` 警告。

## 版本
- versionCode = 34
- versionName = 0.8.1

## 建置前確認
`MainActivity.kt` 應搜尋得到：
- `BUILD_MARKER_v0_8_1`
- `private fun showGameLibrary()`
- `MATH("數學練習"`
