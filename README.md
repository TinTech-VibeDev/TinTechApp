# FilmBuff — Android App

نسخه Android فیلم‌باف با WebView امن برای Mini App و پلیر داخلی Media3/ExoPlayer.

## آدرس اصلی
`https://movie-search-bot.barmonn.workers.dev/menu`

## تغییرات این نسخه
- نام برنامه: **FilmBuff**
- لوگوی ارسالی FilmBuff به عنوان Launcher/Splash icon
- پلیر تمام‌صفحه و بدون عنوان یا لوگوی ثابت روی تصویر
- کنترل‌ها پس از چند ثانیه کاملاً محو می‌شوند
- دوبار لمس سمت چپ/راست: ۱۰ ثانیه عقب/جلو
- انتخاب کیفیت و Audio track از داخل پلیر
- تغییر Fit / Fill / Zoom
- Subtitle button استاندارد Media3
- Picture-in-Picture
- Media3/ExoPlayer 1.10.0
- Target/Compile SDK 36
- WebView Safe Browsing، HTTPS-only و جلوگیری از نمایش صفحات متفرقه داخل WebView

## Build با GitHub Actions
Workflow در `.github/workflows/build-apk.yml` قرار دارد.

### Build آزمایشی
اگر Secretهای امضا تعریف نشده باشند، Action یک Debug APK تولید می‌کند.

### Build Release واقعی و با امضای ثابت
برای انتشار رسمی یا نصب مطمئن‌تر، یک keystore اختصاصی بساز و این Secretها را در GitHub Repository > Settings > Secrets and variables > Actions قرار بده:

- `KEYSTORE_BASE64` — کل متن فایل `KEYSTORE_BASE64.txt`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS` — برای workflow ساخته‌شده مقدار `filmbuff`
- `KEY_PASSWORD`

Workflow ساخت APK این نام‌ها را به‌صورت اصلی استفاده می‌کند و برای سازگاری با نسخه‌های قدیمی، نام‌های `ANDROID_*` را نیز می‌پذیرد.


### ساخت keystore ثابت (یک بار)
```bash
keytool -genkeypair -v -keystore filmbuff-release.jks -alias filmbuff -keyalg RSA -keysize 4096 -validity 10000
```
بعد همان فایل را امن نگه دار و مقدار Base64 آن را در Secret `KEYSTORE_BASE64` قرار بده. **کلید خصوصی را داخل ریپوی عمومی Commit نکن.**

نمونه تبدیل keystore به Base64 در Linux/macOS:
```bash
base64 -w 0 filmbuff-release.jks > keystore.txt
```

وقتی Secretها موجود باشند، Action این دو خروجی را می‌سازد:
- Signed Release APK
- Release AAB برای Google Play

## درباره Google Play Protect
هیچ کدی نمی‌تواند یا نباید Play Protect را دور بزند. برای کمینه کردن هشدارها:
1. از Debug APK برای انتشار عمومی استفاده نکن.
2. همه نسخه‌ها را با **یک release key ثابت** امضا کن.
3. برای Google Play از AAB و Play App Signing استفاده کن.
4. حساب توسعه‌دهنده را در Google/Android Developer Console تأیید کن.
5. APKهای دانلودی خارج از Play ممکن است همچنان هشدار sideload/unknown source داشته باشند؛ این رفتار امنیتی Android است و تضمین حذف آن از داخل اپ ممکن نیست.

## نسخه
`1.2.0` / versionCode `3`
