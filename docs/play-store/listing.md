# Google Play — بيانات النشر / Store submission sheet

نص جاهز للنسخ إلى Play Console. راجعه قبل اللصق.

## 1. صفحة المتجر (Main store listing)

**اسم التطبيق (≤30):** تلاوة - المصحف والتلاوة والمراجعة
**Title (EN, ≤30):** Tilawa – Quran & Recitation

**الوصف المختصر (≤80):**
المصحف بالرسم العثماني، تلاوات آية بآية مع تظليل حي، ومراجعة حفظك بصوتك.

**Short description (EN, ≤80):**
Uthmani mushaf, ayah-by-ayah recitations with live highlight, and memorization review.

**الوصف الكامل:**
تلاوة تطبيق مجاني بلا إعلانات لقراءة القرآن الكريم والاستماع إليه ومراجعة الحفظ.

• المصحف كاملاً بالرسم العثماني وبتخطيط صفحات المصحف المطبوع.
• الاستماع لنخبة من القرّاء آية بآية، مع تظليل الآية المتلوّة والانتقال إليها تلقائياً.
• التشغيل في الخلفية والتحكم من الإشعار وشاشة القفل.
• تكرار الآية أو السورة والانتقال التلقائي للسورة التالية.
• مراجعة الحفظ: اتلُ بصوتك ويقارن التطبيق تلاوتك بالآية كلمة كلمة ويُظهر الكلمات الفائتة.
• فهرس السور والأجزاء والبحث باسم السورة.
• يعمل دون اتصال بعد تنزيل النص والتلاوات.
• الوضع الليلي وتكبير الخط.

لا حسابات، ولا إعلانات، ولا تتبع.

**Full description (EN):**
Tilawa is a free, ad-free app to read the Holy Quran, listen to it, and review your memorization.

• The complete mushaf in Uthmani script with printed-page layout.
• Ayah-by-ayah recitations from renowned reciters, with live highlighting and auto-scroll.
• Background playback with notification and lock-screen controls.
• Repeat an ayah or surah; continue automatically to the next surah.
• Memorization review: recite aloud and the app compares your recitation word by word and shows missed words.
• Surah and juz index, search by surah name.
• Works offline after downloading text and recitations.
• Dark mode and adjustable font size.

No accounts, no ads, no tracking.

**التصنيف:** Books & Reference (بديل: Education)
**البريد الإلكتروني للتواصل:** CONTACT_EMAIL
**سياسة الخصوصية:** https://inafea.github.io/tilawa/privacy.html

## 2. الرسوميات المطلوبة (Graphics)

| العنصر | المواصفات | الحالة |
|---|---|---|
| أيقونة التطبيق | 512×512 PNG 32-bit، ≤1MB | ✅ graphics/icon-512.png |
| صورة مميزة (Feature graphic) | 1024×500 JPG/PNG بلا شفافية | ✅ graphics/feature-graphic.png |
| لقطات هاتف | 2–8، بنسبة 16:9 أو 9:16، الضلع 320–3840px | ☐ صفحة المصحف، التشغيل مع التظليل، المراجعة، الفهرس، الوضع الليلي |
| لقطات جهاز لوحي (اختياري) | 7" و 10" | ☐ |
| فيديو الخدمة الأمامية | رابط YouTube غير مدرج يُظهر التشغيل في الخلفية والإشعار | ☐ |

## 3. نموذج أمان البيانات (Data safety)

| السؤال | الإجابة |
|---|---|
| هل يجمع التطبيق أو يشارك أياً من أنواع بيانات المستخدم المطلوبة؟ | **لا**. لا يُرسل التطبيق بيانات إلى المطوّر أو أطراف أخرى. الصوت يعالجه مزوّد التعرّف على الكلام في النظام (Google) وفق سياسته، ولا يتلقاه التطبيق أو يخزّنه. ملاحظة: إن رأيت أن تفصح احتياطاً، اختر "Audio → Voice or sound recordings" بغرض "App functionality"، معالجة مؤقتة (ephemeral)، غير مشاركة |
| هل البيانات مشفّرة أثناء النقل؟ | نعم (كل الاتصالات HTTPS) |
| هل يمكن للمستخدم طلب حذف البيانات؟ | لا توجد بيانات على خوادمنا. البيانات المحلية تُحذف بمسح بيانات التطبيق |

## 4. إقرارات المحتوى (App content)

| البند | الإجابة |
|---|---|
| الإعلانات | لا يحتوي إعلانات |
| الوصول للتطبيق | كل الميزات متاحة دون تسجيل دخول |
| الجمهور المستهدف | 13+ (أو 18+)؛ ليس موجّهاً للأطفال |
| تصنيف المحتوى (IARC) | مرجعي/تعليمي؛ لا عنف، لا محتوى جنسي، لا مقامرة، لا تفاعل بين المستخدمين، لا مشاركة موقع → متوقع: الجميع / PEGI 3 |
| تطبيق إخباري | لا |
| تطبيق صحي | لا |
| الخدمات المالية | لا |
| أذونات الخدمة الأمامية | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: تشغيل التلاوات في الخلفية بطلب المستخدم مع إشعار تحكم. أرفق رابط الفيديو |
| حكومي | لا |

## 5. الإصدار (Release)

1. أنشئ مفتاح الرفع (upload key) مرة واحدة واحتفظ به ونسخة احتياطية منه:
   `keytool -genkeypair -v -keystore tilawa-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
2. أضف أسرار GitHub: `KEYSTORE_B64` (`base64 -w0 tilawa-upload.jks`)، `KEYSTORE_PASSWORD`، `KEY_ALIAS`، `KEY_PASSWORD`.
3. حمّل `app-release.aab` من مخرجات CI.
4. Play Console ← Testing ← Closed testing: ارفع الحزمة، فعّل Play App Signing، أضف ≥12 مختبِراً، وانتظر 14 يوماً متواصلة.
5. تقدّم بطلب الوصول للإنتاج، ثم انشر.
6. ارفع `versionCode` في كل رفعة لاحقة.
