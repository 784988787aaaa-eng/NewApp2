# دليل البناء والتهيئة والرفع إلى GitHub — الدفتر الذكي Pro (SmartLedger Pro v2.2.0)

تم تطوير وإعادة هندسة هذا المشروع بالكامل وفق أحدث معايير **Material Design 3**، و**Minimal / Premium UI**، ونظام التصميم الموحد (`SmartLedgerTokens`)، مع معالجة شاملة للمنطق المحاسبي والمخزني والنسخ الاحتياطي الموثق (`.slprobackup`) وتنظيمه في مجلدات `Backups/YYYY/MM-Month/`.

## 1. خطوات الرفع إلى GitHub وبناء ملف ה-APK تلقائياً

1. قم بتنزيل الملف المضغوط `SmartLedger_Pro_Android_v2.2.0_GitHub_Ready.zip` وفك ضغطه على جهازك.
2. ارفع جميع الملفات والمجلدات المستخرجة (بما في ذلك مجلد `.github/workflows/android-build.yml`) إلى مستودعك في **GitHub** على فرع `main` أو `master`.
3. انتقل إلى تبويب **Actions** في مستودع GitHub؛ ستجد سير العمل **Build SmartLedger Pro Android APK** يعمل تلقائياً:
   * يقوم بتجهيز `JDK 17` و `Gradle 8.4` وتوليد `gradle-wrapper.jar` تلقائياً إن لزم.
   * يشغّل اختبارات الوحدة المحاسبية (`./gradlew testDebugUnitTest`).
   * يبني حزمة التطبيق (`./gradlew assembleDebug`) ويرفع ملف `SmartLedger-Pro-v2.2.0-Debug-APK` في قسم **Artifacts** لتنزيله وتثبيته مباشرة على هاتفك.
