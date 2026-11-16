# سجل التعديلات الميدانية والمطابقة الفورية (Patch Registry)

> وثيقة هندسية تتبع وتوثق كل تعديل تم تنفيذه في طبقة البايت كود (Smali/DEX) وما يقابله في الكود المصدري الأصلي (Kotlin / Java) داخل مشروع HyperCeiler.

---

## 1. منظومة CorePatch الحديثة (10 خيارات متكاملة)

### [COREPATCH-01] تخطي الرجوع لإصدار أقدم (Bypass Downgrade)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_downgr`
- **الهدف:** السماح بتثبيت تطبيق بإصدار أقدم فوق تطبيق مثبت بإصدار أحدث دون قيود.
- **كود Smali المستهدف:**
  - `com/android/server/pm/PackageManagerService;->checkDowngrade` (Android <= 12)
  - `com/android/server/pm/PackageManagerServiceUtils;->checkDowngrade` (Android >= 13)
- **الكود المصدري المقابل:**
  - `PackageManagerServiceHook.kt` (تصفير `mVersionCode` و `mVersionCodeMajor` على Android Q والأقدم، والعودة بـ `null`).
  - `PackageManagerServiceUtilsHook.kt` (اعتراض `checkDowngrade` والعودة بـ `null`).

---

### [COREPATCH-02] تخطي التحقق من التوقيع والتجزئة (Bypass Verification)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_auth_creak`
- **الهدف:** السماح بتثبيت التطبيقات المعدلة أو ذات التوقيعات غير المتطابقة.
- **كود Smali المستهدف:**
  - `android/util/apk/ApkSignatureVerifier;->verify`
  - `android/util/apk/ApkSigningBlockUtils;->verifySignatureDigest`
  - `com/android/server/pm/PackageManagerServiceUtils;->verifySignatures`
  - `com/android/server/pm/ScanPackageUtils;->assertPackageIsValid`
  - `java/security/MessageDigest;->isEqual`
- **الكود المصدري المقابل:**
  - `ApkSignatureVerifierHook.kt`: إعادة كائن التوقيع الافتراضي.
  - `ApkSigningBlockUtilsHook.kt`: استبدال التجزئة المعطوبة بتجزئة متطابقة.
  - `PackageManagerServiceUtilsHook.kt`: اعتراض `verifySignatures` وإرجاع `false` (أي لا يوجد تعارض).
  - `MessageDigestHook.kt`: إعادة `true` دائماً عند مقارنة التوقيع.

---

### [COREPATCH-03] تخطي قيود محاذاة resources.arsc
- **المفتاح البرمجي:** `prefs_key_system_framework_bypass_resource_arsc`
- **الهدف:** السماح بتثبيت التطبيقات التي يكون فيها ملف `resources.arsc` مضغوطاً أو غير محاذى على 4-byte (بدءاً من Android 11).
- **كود Smali المستهدف:**
  - `android/content/res/AssetManager;->nativeLoadResourceBag` أو دوال فحص المحاذاة الداخلية.
- **الكود المصدري المقابل:**
  - `AssetManagerHook.kt`: فك قيد التحقق من ضغط ومحاذاة ملف الموارد.

---

### [COREPATCH-04] تخطي فحص تطابق التجزئة (Bypass Digest)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_digest_creak`
- **الهدف:** السماح بتحديث وتثبيت التطبيقات بتوقيع مختلف تماماً عن المثبت مسبقاً.
- **كود Smali المستهدف:**
  - `android/content/pm/PackageParser$SigningDetails;->checkCapability`
  - `android/content/pm/SigningDetails;->hasCommonAncestor`
- **الكود المصدري المقابل:**
  - `SigningDetailsHook.kt`: إرجاع `true` في توابع فحص السلف المشترك والقدرات.

---

### [COREPATCH-05] تخطي التطابق التام للتوقيعات (Bypass Exact Signature Match)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_exact_signature_check`
- **الهدف:** السماح لحزم Split APKs والـ App Bundles باستخدام توقيعات مختلفة دون رفض النظام.
- **كود Smali المستهدف:**
  - `android/content/pm/PackageParser$SigningDetails;->signaturesMatchExactly`
- **الكود المصدري المقابل:**
  - `SigningDetailsHook.kt`: اعتراض دالة `signaturesMatchExactly` وإرجاع `true`.

---

### [COREPATCH-06] استخدام التوقيعات السابقة (Use Previous Signatures - Dangerous)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_use_pre_signature`
- **الهدف:** اعتماد توقيع التطبيق المثبت مسبقاً أثناء عملية التحديث تلقائياً.
- **كود Smali المستهدف:**
  - `com/android/server/pm/InstallPackageHelper;->checkPackageSignature`
- **الكود المصدري المقابل:**
  - `InstallPackageHelperHook.kt` و `PackageManagerServiceHook.kt`.
  - واجهة المستخدم في `CorePatchSettings.java`: تضمين نافذة تحذيرية لمستخدمي Xiaomi/Redmi.

---

### [COREPATCH-07] السماح بالـ Hidden APIs لتطبيقات النظام (Allow Hidden APIs)
- **المفتاح البرمجي:** `prefs_key_system_framework_allow_hidden_apis`
- **الهدف:** السماح لتطبيقات النظام التي تم تعديل توقيعها بالوصول الكامل للـ Hidden APIs دون قيود.
- **كود Smali المستهدف:**
  - `android/content/pm/ApplicationInfo;->isAllowedToUseHiddenApis`
- **الكود المصدري المقابل:**
  - `ApplicationInfoHook.kt`: إرجاع `true` عند طلب صلاحية استخدام الدوال المخفية.

---

### [COREPATCH-08] تخطي فحص توقيع المستخدم المشترك (Bypass Shared User Verification)
- **المفتاح البرمجي:** `prefs_key_system_framework_core_patch_shared_user`
- **الهدف:** السماح للتطبيقات التي تشترك في نفس `sharedUserId` بالعمل معاً حتى لو اختلفت توقيعاتها.
- **كود Smali المستهدف:**
  - `com/android/server/pm/SharedUserSetting;->signaturesMatch`
  - `com/android/server/pm/ReconcilePackageUtils;->reconcilePackages`
- **الكود المصدري المقابل:**
  - `SharedUserSettingHook.kt` و `ReconcilePackageUtilsHook.kt`.

---

### [COREPATCH-09] تعطيل وكيل التحقق من الحزم (Disable Verification Agent)
- **المفتاح البرمجي:** `prefs_key_system_framework_disable_verification_agent`
- **الهدف:** منع خدمات الفحص الخارجية (مثل Google Play Protect) من حظر أو فحص تثبيت التطبيقات.
- **كود Smali المستهدف:**
  - `com/android/server/pm/PackageManagerService;->isVerificationEnabled`
  - `com/android/server/pm/VerificationParams;->getVerificationState`
  - `com/android/server/pm/VerifyingSession;->handleStartCopy`
- **الكود المصدري المقابل:**
  - `PackageManagerServiceHook.kt`, `VerificationParamsHook.kt`, `VerifyingSessionHook.kt`.

---

### [COREPATCH-10] تخطي قائمة الحظر النظامية (Bypass Installation Blocklist)
- **المفتاح البرمجي:** `prefs_key_system_framework_bypass_block`
- **الهدف:** تخطي قوائم الحظر التي تفرضها بعض واجهات النظام والشركات المصنعة (مثل هواتف Nothing).
- **كود Smali المستهدف:**
  - `com/android/server/pm/NtConfigListServiceImpl;->isPackageInBlockList`
- **الكود المصدري المقابل:**
  - `NtConfigListServiceImplHook.kt`: إرجاع `false` لتجاوز الحظر فوراً.
