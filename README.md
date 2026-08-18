# 小小腦力樂園 v0.8.2

## v0.8.2 修正

### 首頁
- 12 款遊戲全部直接整合到首頁。
- 4 欄 × 3 列，依螢幕高度自動進入 compact / veryCompact。
- 首頁不使用 ScrollView，維持單一頁面。
- 移除首頁「遊戲庫 12款」重複入口。
- 舊 GameLibrary 畫面若被 navigation stack 呼叫，只顯示「已整合」說明，不再重複 12 款遊戲。
- 修正 Android 狀態列遮住「小小腦力樂園」標題。

### 星星寶箱
- 星星寶箱完全取消關卡/冒險地圖節點。
- 改成 5 / 10 / 15 / 20 / 25 / 30 星的寶箱獎勵格。
- 所有獎勵都使用 `mission_treasure` 寶箱圖示。
- 30 星可開終極寶箱並沿用既有徽章機制。
- 每日任務改為簡單任務清單，不再共用關卡地圖。

### 女聲
- TTS 明確優先 `female / woman / girl / fem` voice。
- 同語言、同國家優先。
- 女聲中再優先 neural / natural / wavenet / studio / premium / enhanced 高品質 voice。
- 明確 male / man voice 大幅降權。
- 若裝置沒有 gender 標記，改選同語系最高品質 voice。
- 中文速度 0.88 / pitch 1.08。
- 美式英文速度 0.86 / pitch 1.07。
- 跟讀示範速度 0.78 / pitch 1.07。

### 其他
- 保留「數學練習」名稱。
- 保留英文分類、阿拉伯數字、題目朗讀、答對音效。
- 保留固定 APK 簽名；原本 4 個 GitHub Secrets 不用重設。

## Version
- versionCode = 35
- versionName = 0.8.2
