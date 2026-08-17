from pathlib import Path

main = Path("app/src/main/java/com/boga/kidgame/MainActivity.kt").read_text(encoding="utf-8")
data = Path("app/src/main/java/com/boga/kidgame/EnglishData.kt").read_text(encoding="utf-8")
build = Path("app/build.gradle.kts").read_text(encoding="utf-8")

checks = {
    "v0.8.1 marker": "BUILD_MARKER_v0_8_1" in main,
    "數學練習": 'MATH("數學練習"' in main,
    "12款遊戲 - 找不同": 'ODD("找不同"' in main,
    "12款遊戲 - 規律接龍": 'PATTERN("規律接龍"' in main,
    "遊戲庫 Screen": "object GameLibrary" in main,
    "遊戲庫函式": "private fun showGameLibrary()" in main,
    "英文分類子頁": "data class EnglishListening(val category: String? = null)" in main,
    "英文副標題最多3行": "maxLines = 3" in main,
    "卡片取消裁切": "clipChildren = false" in main,
    "數字直接顯示阿拉伯數字": 'EnglishWord("數字", n.toString()' in data,
    "答對音效": "private fun playCorrectSound()" in main,
    "versionCode 34": "versionCode = 34" in build,
    "versionName 0.8.1": 'versionName = "0.8.1"' in build,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("OK   " if ok else "FAIL ") + name)

if failed:
    raise SystemExit("驗證失敗：" + "、".join(failed))
print("v0.8.1 建置修正版靜態驗證通過。")
