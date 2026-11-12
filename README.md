# 新聞下載器（Android）

把桌面版 Python 程式 `news_scraper_gui.py`（Tkinter）改寫成原生 Android App。
貼上新聞網址 → 自動抓標題與內文 → 存成 `.txt`（有封面圖就順便存 `.jpg`）到
**手機儲存空間/Download/新聞下載器**。

## 下載安裝（APK 由 GitHub Actions 自動編譯）

[**⬇️ 下載最新 APK**](https://github.com/pliok7485/news-downloader-android/releases/latest/download/news-downloader.apk)

手機開啟上面連結 → 允許「安裝未知來源的應用程式」→ 安裝。
（也可以到 [Releases](https://github.com/pliok7485/news-downloader-android/releases) 頁面下載。）

需求：Android 8.0（API 26）以上。

## 功能

- 一次貼多個網址（每行一個），批次抓取
- 沿用桌面版的網站選擇器表（三立、民視、聯合、中時、TVBS、ETtoday、科技新報…等多個新聞站）
- 抓不到網站設定就自動退回通用規則（挑 p 文字量最大的容器）
- 內建廣告／導覽文字黑名單剔除、段落去重
- 自動下載 `og:image` 封面圖並轉 JPEG
- 代理伺服器（`http://` 或 `socks5://`，支援 Tor／Orbot）
- 日間／夜間模式切換（記住上次選擇）
- 在瀏覽器按「分享」→「新聞下載器」直接把網址送進來
- 輸出位置固定為 `Download/新聞下載器`（Android 10+ 走 MediaStore，不需權限）

## 和桌面版的差異

| 項目 | 桌面版 (Python) | Android 版 |
| --- | --- | --- |
| 介面 | tkinter | 原生 Kotlin + Material |
| 抓取 | curl_cffi（Chrome TLS 偽裝）+ requests | HttpURLConnection（桌面 Chrome 標頭 + Cookie 預熱） |
| 解析 | BeautifulSoup + trafilatura | Jsoup（同樣的選擇器與清洗規則） |
| 輸出資料夾 | 自選 | 固定 `Download/新聞下載器` |

`curl_cffi` 的 TLS 指紋偽裝在 Android 上沒有可用的預編譯版本，因此改用一般 HTTP 請求；
少數只擋 TLS 指紋的站台可能抓不到，其餘行為與桌面版一致。

## 專案結構

```
android/
  app/build.gradle.kts                       簽章（金鑰由 Secrets 提供）、依賴設定
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/pliok/newsdownloader/
    MainActivity.kt                          UI、執行緒、分享意圖、日夜模式
    NewsScraper.kt                           抓取 + 清洗（對應 Python 的 scrape_news）
    HttpFetcher.kt                           HTTP / Cookie / 代理
    SiteConfigs.kt                           網站選擇器表 + 黑名單
    Storage.kt                               存到 Download/新聞下載器
  app/src/main/res/layout/activity_main.xml
tools/check_layouts.py                       建置前檢查 layout 參數
.github/workflows/build-apk.yml              自動編 APK + 發 Release
```

## 簽章金鑰（重要）

金鑰不存在這個 repo 裡（repo 是公開的，金鑰放進來等於任何人都能簽出「同一個發行者」的假 APK）。
CI 是在建置時從 GitHub Secrets 還原：

| Secret 名稱 | 內容 |
| --- | --- |
| `NEWS_KEYSTORE_B64` | 金鑰檔 `news-release.jks` 的 base64（單行，`base64 -w0`） |
| `NEWS_KEYSTORE_PASSWORD` | 金鑰密碼（key password 未另設時沿用此值） |

設定位置：Repo → Settings → Secrets and variables → Actions → New repository secret。
金鑰本體請自行保管（遺失後無法再覆蓋安裝既有版本，只能換簽章重裝）。

本機要自己編簽章版：

```bash
cp /path/to/news-release.jks android/app/
export ANDROID_HOME=~/android-sdk
export NEWS_KEYSTORE_PASSWORD='你的密碼'
cd android && gradle assembleRelease
```

## 自己改

1. 改 `SiteConfigs.kt` 的 `MAP` 可新增或調整網站選擇器。
2. 推到 `main` 分支後 GitHub Actions 自動重編，約 3 到 5 分鐘後 Releases 的
   `latest` 就會更新（同一簽章，可直接覆蓋安裝）。
3. 建置失敗的 Gradle log 會自動推到 `ci-logs` 分支的 `build-failure.log`。
