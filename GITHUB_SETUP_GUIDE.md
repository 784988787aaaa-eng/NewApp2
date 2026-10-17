# دليل رفع وتشغيل المشروع على منصة GitHub — SmartLedger Pro Android

تم تجهيز هذا المشروع بكافة متطلبات البناء الآلي (CI/CD) لمنصة **GitHub Actions**، ليعمل بمجرد رفعه إلى أي مستودع GitHub وبناء ملف APK تلقائياً.

---

## 1. محتويات ملفات منصة GitHub وقرادل (GitHub & Gradle Matrix)

* **بيئة قرادل (`Gradle Distribution`)**: الإصدار المعتمد `Gradle 8.4-bin` المتوافق تماماً مع `Android Gradle Plugin 8.2.2` و `Kotlin 1.9.22`.
* **محرك الجافا (`Java SDK`)**: `JDK 17 (Temurin)`، وهو الإصدار القياسي المطلوب من Google لمشاريع Android الحديثة.
* **إعدادات الأداء (`gradle.properties`)**: تفعيل الذاكرة `2048m`، والتوازي `org.gradle.parallel=true`، والتخزين المؤقت `org.gradle.caching=true`.
* **ملف سير العمل الآلي (`.github/workflows/android-build.yml`)**:
  * يُشغّل عند كل `push` أو `pull_request` على فروع `main` و `master` أو تشغيل يدوي `workflow_dispatch`.
  * يقوم بتحميل بيئة `Ubuntu Latest` وتثبيت `JDK 17` و `Gradle 8.4`.
  * ينفذ `./gradlew assembleDebug` لبناء حزمة أندرويد.
  * يرفع تلقائياً ملف الـ APK النهائي كأثر قابل للتحميل (`SmartLedger-Pro-v2.1.0-Debug`).

---

## 2. خطوات الرفع السريع إلى GitHub (3 خطوات سهلة)

افتح موجه الأوامر (Terminal / PowerShell) داخل مجلد المشروع ونفّذ:

```bash
# 1. تهيئة مستودع Git محلياً (إذا لم يكن مهيئاً)
git init
git add .
git commit -m "Initial commit: Complete SmartLedger Pro Android App with GitHub Actions CI/CD"

# 2. ربط المستودع برابط مستودعك على GitHub
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git

# 3. دفع الكود إلى GitHub
git push -u origin main
```

---

## 3. كيفية تحميل الـ APK بعد البناء الآلي على GitHub

1. بمجرد تنفيذ أمر `git push`، افتح صفحة مستودعك على موقع **GitHub**.
2. اضغط على تبويب **Actions** في الشريط العلوي.
3. ستشاهد عملية البناء المسماة **«Build & Package Android APK»** تعمل تلقائياً وتتحول إلى علامة صح خضراء (✔) خلال نحو دقيقتين.
4. اضغط على العملية الناجحة، وانزل إلى أسفل الصفحة في قسم **Artifacts**.
5. ستجد الملف الجاهز للتحميل والتثبيت المباشر على الهاتف: **`SmartLedger-Pro-v2.1.0-Debug`**.
