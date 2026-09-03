# فیلم باف — اپ Android

## چه چیزی اضافه شد؟
- **پلیر داخلی** (Media3 / ExoPlayer) با پخش **مستقیم از CDN**
- زیرنویس روی همان timeline ویدیو (منطق مشابه VLC)
- وقتی در اپ روی «پلیر فیلم باف» / مسیر `/play` بزنید → **بدون مرورگر** پلیر خود اپ باز می‌شود

## ⚠️ این پوشه سورس است، نه APK
سایت‌های «ZIP را APK کن» معمولاً **کار نمی‌کنند** چون پروژهٔ Gradle کامل می‌خواهند.

---

## روش پیشنهادی بدون Android Studio (ایران)

### GitHub Actions (رایگان)
1. یک ریپو در GitHub بساز و محتویات پوشه `TinTechApp` را آپلود کن
2. برو **Actions** → **Build APK** → **Run workflow**
3. بعد از اتمام، از **Artifacts** فایل `FilmBuff-debug-apk` را دانلود کن
4. APK را روی گوشی نصب کن (Install unknown apps)

این روش **نیازی به Android Studio روی سیستم شما ندارد**.

---

## روش با خط فرمان (اگر JDK 17 و SDK داری)
```bash
cd TinTechApp
chmod +x gradlew
./gradlew assembleDebug
```
خروجی: `app/build/outputs/apk/debug/app-debug.apk`

---

## Android Studio (اگر در دسترس بود)
File → Open → پوشه `TinTechApp` → Build → Build APK(s)

---

## تنظیم دامنه ورکر
در `app/build.gradle`:
```
buildConfigField "String", "APP_BASE_URL", "\"https://movie-search-bot.barmonn.workers.dev/menu\""
```

## نسخه
- versionName `1.1.0` — پلیر داخلی
