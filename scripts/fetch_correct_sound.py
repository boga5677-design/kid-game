#!/usr/bin/env python3
import html
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

PAGE_URL = "https://m.sc.chinaz.com/yinxiao/210212231791.html"
OUT = Path("app/src/main/res/raw/correct_answer_source.mp3")

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/131.0 Safari/537.36"
)

def request(url: str, referer: str | None = None) -> bytes:
    headers = {
        "User-Agent": UA,
        "Accept": "*/*",
        "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.7",
    }
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()

page = request(PAGE_URL).decode("utf-8", errors="ignore")
decoded = html.unescape(page)

# 站長素材舊版頁面通常直接在 HTML 的 <audio src="...mp3"> 放實際音訊 URL。
# 同時保留較寬鬆的 MP3 URL 掃描，因應 src 被放在其他 attribute / JS 字串。
patterns = [
    r'<audio[^>]+src=["\']([^"\']+\.mp3(?:\?[^"\']*)?)["\']',
    r'["\']((?:https?:)?//[^"\']+\.mp3(?:\?[^"\']*)?)["\']',
    r'["\']([^"\']*/Files/DownLoad/[^"\']+\.mp3(?:\?[^"\']*)?)["\']',
]

candidates: list[str] = []
for pattern in patterns:
    for match in re.findall(pattern, decoded, flags=re.I):
        url = match.strip().replace("\\/", "/")
        if url.startswith("//"):
            url = "https:" + url
        elif url.startswith("/"):
            url = urllib.parse.urljoin(PAGE_URL, url)
        elif not url.startswith(("http://", "https://")):
            url = urllib.parse.urljoin(PAGE_URL, url)
        if url not in candidates:
            candidates.append(url)

# 優先 Chinaz 自己的音訊下載站。
candidates.sort(key=lambda u: 0 if "chinaz" in u.lower() else 1)

if not candidates:
    print("ERROR: 指定頁面沒有找到 MP3 音訊網址：", PAGE_URL, file=sys.stderr)
    sys.exit(2)

last_error = None
for audio_url in candidates:
    try:
        data = request(audio_url, referer=PAGE_URL)
        if len(data) < 1500:
            raise RuntimeError(f"audio too small: {len(data)} bytes")

        # MP3 常見開頭：ID3，或 MPEG frame sync 0xFF Ex.
        if not (data.startswith(b"ID3") or (len(data) >= 2 and data[0] == 0xFF and (data[1] & 0xE0) == 0xE0)):
            # 某些 MP3 沒有標準首幀位置；只做警告，不直接誤殺。
            print("WARNING: 下載內容沒有典型 MP3 header，但仍保留供 Android MediaPlayer 驗證。")

        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_bytes(data)
        print(f"Downloaded requested correct-answer sound: {audio_url}")
        print(f"Saved: {OUT} ({len(data)} bytes)")
        sys.exit(0)
    except Exception as exc:
        last_error = exc
        print(f"Candidate failed: {audio_url} -> {exc}", file=sys.stderr)

print(f"ERROR: 無法下載指定答對音效：{last_error}", file=sys.stderr)
sys.exit(3)
