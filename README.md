# X Game Booster — نسخه بازطراحی‌شده ۴.۱.۰

بازطراحی کامل و مستقل اپ **X Game Booster 4.1.0 Release** با همان پکیج، همان نسخه و همان موتورهای کاری — بدون هیچ وابستگی خارجی (اندروید خالص، بدون کتابخانه، بدون Gradle).

- پکیج: `com.bytecycle.xgamebooster`
- نسخه: `4.1.0 Release` (versionCode 22)
- minSdk: 23 — targetSdk: 34

## موتورهای کاری (Engine Map)

| موتور اپ اصلی | پیاده‌سازی بازطراحی | فایل |
|---|---|---|
| بوستر پینگ (VPN Tunnel/DNS) | سرویس VPN با اینترفیس TUN، رله کامل UDP:53 با بازنویسی پکت و checksum، تشخیص خودکار سریع‌ترین DNS (Google / Cloudflare / Quad9 / Level3 / شکن / رادار) با اندازه‌گیری RTT واقعی | `src/.../vpn/PingVpnService.java` |
| بوستر رم و بازی | پاک‌سازی پروسه‌های پس‌زمینه اپ‌های کاربر با `ActivityManager.killBackgroundProcesses` + نمایش رم آزاد و درصد مصرف | `src/.../boost/` |
| حالت شبح (Ghost) | مخفی‌سازی آیکون اپ از لانچر (`setComponentEnabledSetting`) + نوتیفیکیشن دائمی «بازگرداندن آیکون» | `src/.../ghost/GhostReceiver.java` |
| HUD / کراس‌هیر | اورلی سیستمی `TYPE_APPLICATION_OVERLAY` با ۴ استایل (نقطه/ضربدر/دایره/ترکیبی)، اندازه و رنگ قابل‌تنظیم + نوار وضعیت RAM و پینگ DNS | `src/.../overlay/HudService.java` |
| ضبط صفحه | `MediaProjection` + `MediaRecorder`، ذخیره در `Movies/XGameBooster` (MediaStore روی اندروید ۱۰+)، میکروفون اختیاری | `src/.../record/` |
| حساب کاربری | ورود با نام کاربری یا ورود مهمان (ذخیره محلی) | `src/.../ui/LoginActivity.java` |
| پریمیوم | صفحه اشتراک (یک‌ماهه/یک‌ساله/دائمی) با فعال‌سازی محلی | `src/.../ui/PremiumActivity.java` |

## ساختار پروژه

```
AndroidManifest.xml      مانیفست کامل (اکتیویتی‌ها، سرویس‌ها، پرمیشن‌ها — مطابق اپ اصلی)
res/                     ریسورس‌ها: تم تیره گیمینگ، لایه‌ها، آیکون‌های وکتور، آیکون لانچر
src/com/bytecycle/xgamebooster/   سورس جاوا (بدون هیچ کتابخانه‌ای)
scripts/build.sh         اسکریپت بیلد کامل: aapt2 → javac → d8 → zipalign → apksigner
scripts/gen_icons.py     ساخت آیکون لانچر
apk/                     خروجی نهایی APK امضاشده
docs/REBUILD.md          مستند بازطراحی (نگاشت موتورها)
```

## بیلد

نیازمندی‌ها: `build-tools 34`، `platform android-34`، `JDK 17`، `python3 + Pillow` (فقط برای آیکون)

```bash
./scripts/build.sh
# خروجی: build/X-Game-Booster-4.1.0-redesign.apk
```

کلید امضا: `build/xgb.keystore` — alias: `xgb` — storepass: `xgb2026`

## دانلود APK

فایل امضاشده در پوشه [`apk/`](apk/X-Game-Booster-4.1.0-redesign.apk) و همچنین در بخش [Releases](../../releases) موجود است.

## سلب مسئولیت

این پروژه صرفاً آموزشی و برای مقاصد بازطراحی است؛ نرم‌افزاری مستقل و از نو نوشته‌شده است و هیچ کد یا باینری از اپ اصلی در آن استفاده نشده است. همه‌ی علائم تجاری متعلق به صاحبان آن‌هاست. استفاده در برابر قوانین بازی‌ها یا خدمات آنلاین بر عهده‌ی کاربر است.
