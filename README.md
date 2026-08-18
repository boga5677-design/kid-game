# 小小腦力樂園 v0.8.3

## 聲音
所有 TTS 呼叫共用同一套「輕柔女聲優先」：
- 中文、英文、首頁遊戲名稱、題目自動朗讀、重播、單字、聽力、測驗、發音示範都會先跑同一個女聲 Voice 選擇。
- 明確 female / woman / girl / feminine / fem voice 強力優先。
- 明確 male / man voice 強力降權。
- 女聲內再優先 neural / natural / premium / enhanced / wavenet / studio。
- 中文速度 0.85，pitch 1.055。
- 英文速度 0.83，pitch 1.045。
- 跟讀示範速度 0.76，pitch 1.045。
- TTS volume 0.92，降低過硬的電子感。

> Android TTS 沒有統一 gender API；如果手機的 TTS 引擎完全沒有女聲 Voice，
> App 只能選同語系最高品質 Voice，再套輕微的女老師音高設定。

## 圖層 / 被擋內容修正
依 Samsung 實機首頁截圖重新檢查：
- 每日任務與星星寶箱的 0/5、0/30 原本會被卡片下緣切掉。
- 原因：任務卡高度過低，且 title/value 被固定切成上下各 50%。
- 任務卡高度提高為 72 / 78 / 86dp。
- title/value 改為 WRAP_CONTENT，不再用 0dp + weight 各半。
- 任務 icon 稍縮，文字保留完整 font padding。
- 毛孩區略縮，仍維持單一頁面。
- 毛孩與第一列遊戲增加安全間距。
- 12款遊戲 Grid 開啟 clipChildren/clipToPadding，避免不同列 elevation/文字互蓋。
- 遊戲 icon 稍縮，保留標題垂直空間。
- 英文分類卡 Emoji 底部 padding 再增加，避免 Samsung Emoji 下緣裁切。
- 英文分類卡仍維持 WRAP_CONTENT，沒有恢復固定副標題高度。

## 其他
- 12 款遊戲仍全部在首頁。
- 不重複顯示遊戲庫。
- 星星寶箱維持寶箱獎勵制，不使用關卡。
- 數學名稱維持「數學練習」。
- 固定 APK 簽名保留，原本 4 個 GitHub Secrets 不用重設。

## Version
- versionCode = 36
- versionName = 0.8.3
