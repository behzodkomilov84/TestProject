# Loyihaning kamchiliklari va keyingi rivojlanish yo'nalishlari

Ushbu hujjat loyihani professional darajaga olib chiqish uchun qo'shilishi mumkin bo'lgan
funksiyalar va tuzatilishi kerak bo'lgan kamchiliklarni ro'yxatlaydi. Bugun qo'shilgan
"to'lov asosida ADMIN roli" logikasi bilan bog'liq holda tayyorlangan, lekin butun loyihani
qamrab oladi.

## 1. Bugun qo'shilgan: to'lov asosida ADMIN (obuna) tizimi

- Yangi `Subscription` jadvali: har bir to'lov yozuvi — summa, manba (MANUAL / TELEGRAM / ONLINE),
  holat (PENDING / CONFIRMED / EXPIRED / CANCELLED), boshlanish/tugash sanasi.
- OWNER `/users` sahifasida to'lovni qo'lda qayd qilib, darhol ADMIN bera oladi (muddat bilan).
- Foydalanuvchi Telegram botga `/pay <summa>` yuborsa, PENDING so'rov yaratiladi — OWNER
  buni `/users` sahifasida ko'rib, tasdiqlaydi yoki rad etadi.
- Har kuni tunda (00:30) muddati o'tgan obunalar avtomatik `EXPIRED` bo'ladi va, agar
  foydalanuvchida boshqa faol obuna qolmagan bo'lsa, ADMIN roli avtomatik olib tashlanadi.
- ✅ **BAJARILDI — Click onlayn to'lov integratsiyasi**: `ROLE_ADMIN`
  obunasini foydalanuvchi o'zi (OWNER ishtirokisiz) `/profile`'dan sotib olishi
  mumkin. To'liq Prepare/Complete protokoli, idempotentlik, chargeback/qaytarish
  (avtomatik ADMIN'ni bekor qilish) — batafsil: `docs/PAYMENTS.md`. Click
  merchant kabinetida faollashtirilgan, imzo tekshiruvi production'da haqiqiy
  kalitlar bilan tasdiqlangan. **Cheklov**: birinchi haqiqiy to'lov urinishida
  Click'ning serverlari serverimizga (O'zbekiston hududidan tashqarida)
  ulana olmadi — Click support bilan IP/domen whitelist masalasi hal
  qilinmoguncha real to'lov to'liq sinalmagan.
  ~~Payme integratsiyasi~~ olib tashlandi (2026) — Payme "o'zini o'zi band
  qilgan" (SZ) maqomdagilar bilan shartnoma tuzmasligi aniqlandi, eng kami
  yakka tartibdagi tadbirkor (YaTT) bo'lish talab qilinadi.
- **Cheklov**: Telegram orqali yuborilgan to'lov cheki/skrinshoti hozircha avtomatik
  tekshirilmaydi — OWNER buni Telegram chatining o'zida ko'rib, keyin saytda tasdiqlaydi.
  To'liq avtomatlashtirish uchun rasmni saqlab, admin panelda ko'rsatish kerak bo'ladi.

## 2. Xavfsizlik

- ✅ **BAJARILDI — Parolni tiklash (forgot password)**: email (Brevo SMTP) va Telegram
  kanallari orqali tasdiqlash kodi yuboriladi (`PasswordResetService`, `EmailService`).
- ✅ **BAJARILDI — Login urinishlarini cheklash**: 5 marta noto'g'ri urinishdan keyin
  hisob 10 daqiqaga bloklanadi (`LoginAttemptService`); OWNER `/users` sahifasida
  qo'lda blokdan chiqara oladi.
- ✅ **BAJARILDI — Rol o'zgarishlari uchun audit log**: `RoleAuditLog` — checkbox
  orqali qo'lda va `Subscription` orqali avtomatik berilgan/olib tashlangan barcha
  rollar birlashgan tarixda saqlanadi, `/users` sahifasida ko'rinadi.
- ✅ **BAJARILDI — HTTPS/SSL**: `docker-compose.prod.yml` + nginx reverse proxy +
  Let's Encrypt (avtomatik yangilanadigan sertifikat). Batafsil: `docs/DEPLOYMENT.md`.
- ✅ **BAJARILDI — Fayl yuklashda antivirus/tekshiruv**: har bir yuklangan rasm/video
  ikki bosqichda tekshiriladi — (1) Apache Tika orqali faylning haqiqiy turi (magic-byte)
  aniqlanadi, client yuborgan Content-Type header'iga ishonilmaydi (masalan, ".jpg" deb
  nomlangan .exe/PHP-shell ushlanadi); (2) ClamAV (`clamav` Docker servisi) orqali
  virus/zararli kod skanerlanadi. Docker orqali ishlaganda avtomatik yoqiladi
  (`app.upload.clamav.enabled`); Docker'siz (IntelliJ) ishga tushirilganda o'chirilgan —
  faqat Tika tekshiruvi ishlaydi.

## 3. Funksional kamchiliklar / yetishmayotgan logika

- ✅ **BAJARILDI — Bildirishnoma markazi**: saytning o'zida 🔔 (navbar), ADMIN
  tasdiqlash/muddat tugashi/yangi topshiriq/guruhga taklif/blokdan chiqarish
  hodisalari uchun (`NotificationService`, `/api/notifications`).
- ✅ **BAJARILDI — Online kurslar (JavaRush uslubida)**: OWNER kurs/bo'lim yaratadi
  (matn yoki video — YouTube/yuklangan fayl/boshqa manba), ADMIN/USER obuna orqali
  kirish huquqi oladi, bo'limlar ketma-ket ochiladi (`Course`, `CourseSection`,
  `CourseSubscription`, `/courses`).
- ✅ **BAJARILDI — Email integratsiyasi**: ro'yxatdan o'tishda email tasdiqlash
  (`EmailVerificationService`, `/verify-email` — kod kiritilmaguncha hisob
  `isEnabled()=false`, kirish bloklanadi; mavjud userlar `email_verified=TRUE`
  bilan backfill qilindi, ular login qilishda davom etadi) va OWNER uchun
  `/payments` sahifasidan hisobotni bir tugma bilan o'z emailiga yuborish
  (`EmailService.sendSubscriptionReport`).
- ✅ **BAJARILDI — To'lov tarixi va hisobot**: OWNER uchun `/payments` sahifasi —
  jami/oylik tushum, faol obunachilar, to'liq to'lov tarixi (`SubscriptionStatsDto`,
  `GET /api/subscriptions/stats`).
- ✅ **BAJARILDI — Obunani avtomatik eslatish**: `SubscriptionReminderService`
  (`DeadlineReminderService` patternida) — ADMIN obunasi tugashiga 3 kun qolganda
  har kuni 09:00'da Telegram va saytdagi bildirishnoma orqali eslatma yuboriladi.
- **Guruh/sinf darajasida chegirma yoki tarif rejalar** yo'q — hammaga bir xil erkin summa.

## 4. Kod sifati va infratuzilma

- ✅ **BAJARILDI — Avtomatik unit testlar (servis qatlami to'liq)**: `service`
  paketidagi BARCHA servislar JUnit 5 + Mockito bilan qoplandi
  (`src/test/java/.../service/`). Ayniqsa e'tiborli qismlar:
  `SubscriptionService`/`CourseSubscriptionService` (ADMIN/kurs kirish
  huquqini berish-olib tashlash, `reverseOnline`/`expireSubscriptions`dagi
  "boshqa faol obuna bormi" tekshiruvi), `PaymentOrderService`/`ClickService`
  (webhook idempotentligi, MD5 imzo tekshiruvi, chargeback/reversal oqimi —
  avval qo'lda sinov so'rovlari bilan tekshirilgan stsenariylar endi
  avtomatik regressiya sifatida qulflangan),
  `TeacherService`/`StudentService`/`AssignmentAttemptService` (ruxsat
  tekshiruvlari, taklif/topshiriq holat mashinasi, vaqt hisoblash),
  `CourseService` (bo'limlarning ketma-ket ochilish mantig'i),
  `FileStorageService`/`ExcelService`/`ClamAvScanService` (haqiqiy fayl
  baytlari — PNG imzosi, real .xlsx, soxta TCP-server — bilan magic-byte
  spoofing va "fail closed" xavfsizlik siyosatini tekshiradi),
  `UserServiceImpl`, `QuestionService`, `PhoneNumberService` va boshqalar.
  Qolgan
  integration/`@SpringBootTest` darajasidagi testlar hali yo'q.
- ✅ **BAJARILDI — CI/CD**: GitHub Actions (`.github/workflows/ci.yml`) — `master`ga
  har push/pull request'da avtomatik ishga tushadi: MySQL 8 servis konteyner
  ko'tariladi, JDK 17 o'rnatiladi, `mvn clean verify` orqali BARCHA 298 ta test
  (297 unit + `TestApplicationTests`ning to'liq Spring context yuklanishi)
  bajariladi va ilova qadoqlanadi (`.jar`). Muvaffaqiyatsiz bo'lsa ham
  surefire hisobotlari artifact sifatida saqlanadi.
- ✅ **BAJARILDI — Backup strategiyasi**: `docker-compose.prod.yml`dagi
  `backup` servisi har kuni 03:00'da MySQL (`mysqldump --single-transaction`)
  va `uploads/` papkasining zaxira nusxasini `./backups`ga oladi, eski
  nusxalarni (`BACKUP_RETENTION_DAYS`, standart 14 kun) avtomatik o'chiradi,
  tiklash (`restore-db.sh`) skripti bilan birga. Serverdan tashqariga
  (bulutga) ko'chirish uchun host cron + rclone yo'riqnomasi ham berilgan.
  Batafsil: `docs/BACKUP.md`. **Haqiqiy Docker konteynerda to'liq uchma-uch
  sinovdan o'tkazildi** (izolyatsiya qilingan alohida compose loyihasida,
  asosiy dev stack'ga tegmasdan): mysqldump/tar ishlab chiqargan fayllar
  qo'lda tekshirildi (`zcat`/`tar -tzvf`), restore-db.sh ham sinaldi —
  shu jarayonda haqiqiy bug topildi va tuzatildi (oddiy mysqldump-import
  dump olingandan KEYIN qo'shilgan jadvallarni o'chirmas edi — endi avval
  `DROP DATABASE` qilib, dump haqiqatan ham to'liq "almashtirish" bo'lishini
  kafolatlaydi).
- ✅ **BAJARILDI — Markazlashtirilgan xato kuzatuvi (Sentry)**: `logback-spring.xml`dagi
  `SentryAppender` orqali — ERROR va undan yuqori darajadagi barcha loglar
  Sentry'ga yuboriladi (Spring Boot'ning o'z auto-konfiguratsiya starteri
  emas, Boot versiyasidan mustaqil sof logback appender ishlatilgan).
  `SENTRY_DSN` .env'da bo'sh bo'lsa (standart), appender jim o'zini
  o'chiradi — konsolga/faylga yozish avvalgidek davom etadi, hech qanday
  qo'shimcha sozlash shart emas. Yoqish uchun: `.env.example`'ga qarang.

## 5. Huquqiy / monetizatsiya

- ✅ **BAJARILDI — Foydalanish shartlari va maxfiylik siyosati**: `/terms` va
  `/privacy` sahifalari (draft, yuridik ko'rikdan o'tmagan — sahifada shu haqda
  ogohlantirish bor), ro'yxatdan o'tishda majburiy roziliknoma checkbox'i bilan.
- **To'lov qaytarish (refund) siyosati** aniqlanmagan — agar ADMIN huquqi noto'g'ri
  berilgan/bekor qilinishi kerak bo'lsa, qanday tartibda pul qaytarilishi hujjatlashtirilmagan.

## 6. Telegram bot — to'liq funksionallik (rejalashtirilmoqda)

Hozirgi bot juda cheklangan — faqat USER (o'quvchi) uchun (akkaunt ulash,
topshiriqlar, test topshirish, natijalar, `/pay` so'rovi). Maqsad: saytdagi
deyarli barcha funksiyani, foydalanuvchi roliga (OWNER/ADMIN/USER) qarab,
botdan turib ishlatish mumkin bo'lishi.

**0-bosqich — arxitektura (fundament)** ✅ BAJARILDI
- [x] Suhbat holati (conversation state) — yangi `TelegramSession` jadvali
  (`chat_id`, `state`, `temp_data` JSON) — ko'p bosqichli oqimlar
  (profil tahrirlash, gruppa/topshiriq yaratish) uchun. Bazada saqlanadi
  (xotirada emas), chunki production tez-tez qayta ishga tushadi (deploy).
  (`TelegramSession`, `TelegramSessionRepository`, `TelegramSessionService`, `BotState`)
- [x] Native Telegram buyruqlar menyusi (`BotFather setMyCommands`) —
  `TelegramConfig.setupCommandMenu()`, bot muvaffaqiyatli ro'yxatdan
  o'tgach avtomatik o'rnatiladi.
- [x] Rolga qarab asosiy `ReplyKeyboardMarkup` menyusi (`/start`/`/menu`da
  rol aniqlanib, mos menyu ko'rsatiladi) — `TelegramMenuService.buildMainMenu`.

**1-bosqich — umumiy (barcha rollar)** ✅ BAJARILDI
- [x] 👤 Profil: ko'rish (username/email/telefon/rol), tahrirlash.
  Parolni o'zgartirish ham — xavfsizlik ogohlantirishi bilan (Telegram
  matn tarixida qolishi haqida foydalanuvchiga eslatiladi).
  (`TelegramProfileService`, saytdagi bilan bir xil `ProfileService`
  orqali — validatsiya/xatolik xabarlari ikkala joyda ham bir xil.)
- [x] 🔔 Bildirishnomalar: ro'yxat, o'qilgan deb belgilash (`NotificationService`).
- [x] 💳 Obunam: joriy ADMIN obuna holati, Click orqali to'lash (1 oylik,
  saytdagi `startPayment('CLICK')` bilan bir xil oqim).
- [x] 📚 Kurslar: ro'yxat, obuna holati (batafsil ko'rish/obuna bo'lish
  hozircha saytga yo'naltiradi — to'liq interaktiv oqim keyingi bosqichda).
- [x] ℹ️ Yordam — barcha buyruqlar tavsifi bilan.

Test: 22 ta yangi unit test (`TelegramSessionServiceTest`,
`TelegramProfileServiceTest`, `TelegramMenuServiceTest`).

**2-bosqich — USER (o'quvchi) qo'shimcha** ✅ BAJARILDI
- [x] 🎯 Mustaqil test — fan tanlab, tasodifiy savollar bilan mashq
  (saytdagi `/testConfigPage`+`testSession` oqimining bot varianti).
  `TelegramPracticeTestService`: fan -> savol soni (5/10/15/20/mavjud
  barchasi) -> savol-javob (inline tugmalar) -> natija. Haqiqiy
  `TestSessionService.startTest`/`finishTest` orqali — saytdagi bilan
  bir xil ballash mantig'i. `TestSessionService.startTest()` savollarni
  bazada saqlamagani uchun, bot davom etayotgan testning holatini
  (savollar, joriy indeks, tanlangan javoblar) `TelegramSession.tempData`da
  (JSON, `PracticeTestState`) o'zi saqlab boradi.
  Test: 9 ta yangi unit test (`TelegramPracticeTestServiceTest`) — jumladan
  to'liq oqim (2 savol, bittasi noto'g'ri) haqiqiy `TelegramSessionService`
  bilan (JSON round-trip) tekshirilgan.

**3-bosqich — ADMIN (o'qituvchi) qo'shimcha** ✅ BAJARILDI
- [x] 👥 Gruppalarim — a'zolar ro'yxati (holat bilan: ✅/⏳/❌), yangi guruh
  yaratish, username bo'yicha o'quvchi taklif qilish. (`TelegramTeacherService`)
- [x] 📝 Topshiriq berish — guruh -> (o'qituvchining saytda saqlagan)
  savollar paketi -> muddat (1/3 kun, 1/2 hafta) tanlab, butun guruhga
  bir yo'la. Haqiqiy `TeacherService.assignQuestionSetToStudents` orqali
  (bitta guruhga bitta topshiriq cheklovi, bildirishnoma avtomatik).
- [x] 📈 O'quvchilar natijalari — topshiriqlar ro'yxati (bajarilgan/jami),
  har biri bo'yicha har bir o'quvchining holati va foizi.
- [x] 🗂 Savollar boshqaruvi — fan/mavzu tanlab, .xlsx faylni to'g'ridan-
  to'g'ri botga yuborib import qilish (haqiqiy `ExcelService` orqali —
  saytdagi bilan bir xil magic-byte/ClamAV validatsiyasi va qator-qator
  xatolik izolyatsiyasi). Telegram'dan yuklab olingan fayl yangi
  `ByteArrayMultipartFile` orqali mavjud servisga moslashtirildi —
  HTTP so'rovisiz, kod takrorlanmasdan. (`TelegramQuestionImportService`)
- [x] 💬 Topshiriq chatlari — har bir topshiriq uchun umumiy chat (o'qituvchi
  + guruh o'quvchilari), saytdagi bilan bir xil `AssignmentService` orqali.
  (`TelegramAssignmentChatService`)

Test: 27 ta yangi unit test (`TelegramTeacherServiceTest`,
`TelegramAssignmentChatServiceTest`, `TelegramQuestionImportServiceTest`).
Yangi DB ustuni/jadval kerak bo'lmadi (mavjud entity'lar qayta ishlatildi).

**4-bosqich — OWNER qo'shimcha** ✅ BAJARILDI
- [x] 👑 Foydalanuvchilar — username bo'yicha qidirish, rol berish/olib
  tashlash (✅/⬜ tugmalar, `UserServiceImpl.addRole/removeRole` orqali —
  saytdagi bilan bir xil o'z-o'zini o'zgartira olmaslik cheklovi), bloklangan
  hisobni blokdan chiqarish. (`TelegramOwnerService`)
- [x] 💰 To'lovlar — qisqa hisobot (jami/oylik tushum, faol obunachilar) +
  kutilayotgan so'rovlarni tasdiqlash/rad etish (`SubscriptionService`).
- [x] ⚙️ Tizim sozlamalari — Click minimal tranzaksiya summasini ko'rish/
  o'zgartirish (avvalgi bosqichlarda qo'shilgan `PaymentOrderService`
  orqali, saytdagi `/users` sahifasi bilan bir xil).
- [x] 📢 E'lon yuborish (broadcast) — matn yozib, oldindan ko'rib chiqib
  (necha kishiga yuborilishi ko'rsatiladi) tasdiqlagach, botga ulangan
  BARCHA foydalanuvchiga yuboriladi. Bitta yetkazib berish muvaffaqiyatsiz
  bo'lsa ham (masalan foydalanuvchi botni bloklagan), qolganlari davom etadi.

Yo'l-yo'lakay tuzatildi: pul summasini formatlash (`formatSom`) JVM standart
lokaliga bog'liq edi (`String.format("%,.0f", ...)` ba'zi lokallarda
vergul o'rniga boshqa belgi ishlatadi) — endi lokaldan mustaqil, qo'lda
guruhlanadi.

Test: 16 ta yangi unit test (`TelegramOwnerServiceTest`). Yangi DB
o'zgarishi kerak bo'lmadi. Shu bilan Telegram bot rejasining barcha
4 bosqichi (0-4) yakunlandi — 5-bosqich (botda to'g'ridan-to'g'ri
ro'yxatdan o'tish) ixtiyoriy, alohida muhokama qilinadi.

**5-bosqich (ixtiyoriy, alohida muhokama)**
- [ ] Botda to'g'ridan-to'g'ri ro'yxatdan o'tish (saytga kirmasdan) — email
  tasdiqlash oqimi bilan qanday integratsiya qilinishi alohida
  loyihalashtirishni talab qiladi.

## Ustuvorlik bo'yicha tavsiya

~~Parolni tiklash~~, ~~Login urinishlarini cheklash~~, ~~Rol audit log~~,
~~Bildirishnoma markazi~~, ~~HTTPS/SSL~~, ~~Fayl antivirus tekshiruvi~~,
~~To'lov tarixi/hisobot~~, ~~Online kurslar~~, ~~Obuna eslatmasi~~,
~~Email integratsiyasi~~, ~~Foydalanish shartlari/Maxfiylik siyosati~~,
~~Click integratsiyasi~~ (production'da faol, real to'lov ham tasdiqlangan —
IP whitelist muammosi hal qilindi), ~~Avtomatik unit testlar (servis
qatlami)~~, ~~CI/CD~~, ~~Backup strategiyasi~~, ~~Markazlashtirilgan xato
kuzatuvi (Sentry)~~, ~~Telegram bot — to'liq funksionallik (0-4 bosqich)~~
— bajarildi.

Qolgan (tarif rejalar, refund siyosati, keng qamrovli integration testlar,
botda ro'yxatdan o'tish) — kattaroq va alohida rejalashtirish talab
qiladigan ishlar.
