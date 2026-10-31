# مستند بازطراحی — X Game Booster 4.1.0

## ۱. روش کار

اپ اصلی با ابزارهای تحلیل استاتیک (manifest + dex scan) بررسی شد و نقشه‌ی امکانات و موتورهای کاری استخراج شد. سپس پروژه از صفر و بدون استفاده از هیچ کدی از اپ اصلی بازنویسی شد. هیچ کد، ریسورس یا باینری از APK اصلی کپی نشده است.

## ۲. شناسه‌ها (مطابق اپ اصلی)

| مورد | مقدار |
|---|---|
| پکیج | `com.bytecycle.xgamebooster` |
| versionCode | 22 |
| versionName | 4.1.0 Release |
| minSdk | 23 |
| targetSdk | 34 |

## ۳. نگاشت موتورها

### ۳.۱ بوستر پینگ — VPN Tunnel (NetGuard-style)
اپ اصلی از سرویس VPN با تونل DNS استفاده می‌کرد. بازطراحی:
- `VpnService` + `Builder`: آدرس `10.111.222.1/32`، MTU 1500، فقط روتِ IP سرورهای DNS گرفته می‌شود تا ترافیک عادی دست‌نخورده بماند.
- حلقه‌ی خواندن از TUN: پارس هدر IPv4 → فیلتر UDP:53.
- رله: هر پرس‌وجو با سوکت `protect()`-شده به DNS منتخب ارسال و پاسخ با بازنویسی IP/UDP header (شامل checksum صحیح) به TUN برگردانده می‌شود.
- پروب RTT واقعی روی ۱۱ سرور (Google، Cloudflare، Quad9، Level3، شکن ×۲، رادار ×۲، رادار گیم) و انتخاب سریع‌ترین.
- حالت دستی انتخاب DNS از تنظیمات + ری‌استارت خودکار تونل.

### ۳.۲ بوستر رم و بازی
- `BoosterService` به‌صورت Foreground Service با نمایش «X مگابایت آزاد شد».
- `MemoryUtils.boost()`: `killBackgroundProcesses` روی همه‌ی اپ‌های غیرسیستمی + پروسه‌های background.
- نمایش درصد مصرف رم با ProgressBar زنده.
- «بوست و اجرا»: بوست خودکار قبل از لانچ بازی (قابل خاموش‌کردن).

### ۳.۳ حالت شبح (Ghost)
- مخفی‌سازی کامپوننت لانچر با `setComponentEnabledSetting(COMPONENT_ENABLED_STATE_DISABLED)`.
- نوتیفیکیشن دائمی با اکشن «بازگرداندن آیکون» → `GhostReceiver`.

### ۳.۴ HUD / کراس‌هیر
- اورلی سیستمی با `SYSTEM_ALERT_WINDOW` (`TYPE_APPLICATION_OVERLAY` در ۲۶+).
- کراس‌هیر غیرقابل‌لمس (touchable=false) تا بازی زیرش سالم بماند — ۴ استایل، اندازه ۸–۴۸، ۴ رنگ.
- نوار وضعیت گوشه‌ی صفحه: مصرف RAM + پینگ DNS تونل (به‌روزرسانی هر ثانیه).
- اعمال تغییرات بدون بستن سرویس (ACTION_RELOAD).

### ۳.۵ ضبط صفحه
- `RecordRequestActivity` → دریافت مجوز MediaProjection → `RecordService` (FGS type mediaProjection).
- خروجی MP4/H264 با ۸Mbps؛ روی اندروید ۱۰+ از طریق MediaStore با `IS_PENDING` در `Movies/XGameBooster`.
- توقف از نوتیفیکیشن یا دکمه‌ی اپ؛ توقف خودکار با callback پروجکشن.

### ۳.۶ حساب و پریمیوم
- لاگین محلی (SharedPreferences) + ورود مهمان — بدون بک‌اند.
- پریمیوم: صفحه اشتراک با ۳ پلن، فعال‌سازی محلی (بدون درگاه پرداخت).

## ۴. ابزار بیلد

| مرحله | ابزار |
|---|---|
| کامپایل ریسورس | `aapt2 compile` (build-tools 34) |
| لینک + R.java | `aapt2 link` با platform-34 |
| کامپایل جاوا | `javac 17` (-source/-target 8) |
| دیکس | `d8 --min-api 23` |
| بسته‌بندی | `zip` + `zipalign -f 4` |
| امضا | `apksigner` (v1+v2) با keystore اختصاصی |

نکته: فایل platform-34 موجود در محیط، متد `VpnService.establish()` را نداشت؛ با یک استاب کامپایلی (`work/sdk/vpnstub`) در `android-patched.jar` ترمیم شد — فقط برای کامپایل، در ران‌تایم کلاس واقعی دستگاه استفاده می‌شود.
