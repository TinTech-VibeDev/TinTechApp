# FilmBuff Desktop (Windows)

اپ دسکتاپ ویندوز با **WebView** که سایت FilmBuff را باز می‌کند.

- فایل اجرایی `.exe` (نصب‌کننده NSIS + نسخه Portable)
- بعد از اولین اجرا، **شورتکات با آیکون روی دسکتاپ** ساخته می‌شود
- User-Agent شامل `FilmBuffApp` / `FilmBuff/Native` برای تشخیص اپ

## آدرس پیش‌فرض

- منو: `https://f.filmbuff.ir/menu`
- خانه: `https://f.filmbuff.ir/`

با متغیر محیطی قابل تغییر است:

```bat
set FILMBUFF_URL=https://f.filmbuff.ir/menu
FilmBuff.exe
```

## ساخت روی سیستم خودتان

نیاز: Node.js 20+

```bash
npm install
npm run dist
```

خروجی در پوشه `dist/`:

- `FilmBuff-Setup-1.0.0-x64.exe` — نصب‌کننده (شورتکات دسکتاپ + استارت‌منو)
- `FilmBuff-Portable-1.0.0-x64.exe` — بدون نصب

## انتشار روی GitHub Release

1. این مخزن را روی GitHub بسازید.
2. در `package.json` مقدار `build.publish.owner` و `repo` را اصلاح کنید.
3. تگ بزنید:

```bash
git tag v1.0.0
git push origin v1.0.0
```

ورک‌فلو `.github/workflows/release.yml` روی تگ `v*` بیلد ویندوز را به Release ضمیمه می‌کند.

یا دستی از Actions → **Release Desktop** → Run workflow.
