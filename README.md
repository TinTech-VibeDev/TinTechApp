# FilmBuff Desktop 1.0.2

ویندوز WebView برای `https://f.filmbuff.ir/menu`

## خروجی Actions
- `FilmBuff-Setup-1.0.2-x64.exe` — نصب‌کننده + شورتکات دسکتاپ
- `FilmBuff-Portable-1.0.2-x64.exe` — بدون نصب

## مهم: ساختار ریپو
فایل‌ها باید در **root** ریپو باشند:

```
package.json
package-lock.json
main.js
preload.js
assets/
build/icon.ico
.github/workflows/release.yml
```

## اجرای Actions
Actions → **Release Desktop** → Run workflow

یا: `git tag v1.0.2 && git push origin v1.0.2`

## بیلد محلی (ویندوز)
```bat
npm install
npm run dist
```

## وابستگی‌ها
- electron@33.2.1
- electron-builder@25.1.8
