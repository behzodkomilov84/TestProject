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
- ✅ **BAJARILDI — Payme/Click onlayn to'lov integratsiyasi**: `ROLE_ADMIN`
  obunasini foydalanuvchi o'zi (OWNER ishtirokisiz) `/profile`'dan sotib olishi
  mumkin. To'liq JSON-RPC (Payme) va Prepare/Complete (Click) protokollari,
  idempotentlik, chargeback/qaytarish (avtomatik ADMIN'ni bekor qilish) —
  batafsil: `docs/PAYMENTS.md`. **Cheklov**: haqiqiy merchant akkaunt yo'qligi
  sabab faqat sintetik (o'zim simulyatsiya qilgan) so'rovlar bilan sinaldi —
  production'ga chiqarishdan oldin Payme/Click'ning test (sandbox) muhitida
  sertifikatsiyadan o'tkazish shart.
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
  paketidagi BARCHA 28 ta servis JUnit 5 + Mockito bilan qoplandi (315 ta
  test, `src/test/java/.../service/`). Ayniqsa e'tiborli qismlar:
  `SubscriptionService`/`CourseSubscriptionService` (ADMIN/kurs kirish
  huquqini berish-olib tashlash, `reverseOnline`/`expireSubscriptions`dagi
  "boshqa faol obuna bormi" tekshiruvi), `PaymentOrderService`/`PaymeService`/
  `ClickService` (webhook idempotentligi, MD5/Basic-Auth imzo tekshiruvi,
  chargeback/reversal oqimi — avval qo'lda sinov so'rovlari bilan
  tekshirilgan stsenariylar endi avtomatik regressiya sifatida qulflangan),
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
  ko'tariladi, JDK 17 o'rnatiladi, `mvn clean verify` orqali BARCHA 316 ta test
  (315 unit + `TestApplicationTests`ning to'liq Spring context yuklanishi)
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

## Ustuvorlik bo'yicha tavsiya

~~Parolni tiklash~~, ~~Login urinishlarini cheklash~~, ~~Rol audit log~~,
~~Bildirishnoma markazi~~, ~~HTTPS/SSL~~, ~~Fayl antivirus tekshiruvi~~,
~~To'lov tarixi/hisobot~~, ~~Online kurslar~~, ~~Obuna eslatmasi~~,
~~Email integratsiyasi~~, ~~Foydalanish shartlari/Maxfiylik siyosati~~,
~~Payme/Click integratsiyasi~~ (kod tayyor, sertifikatsiya kutilmoqda),
~~Avtomatik unit testlar (servis qatlami)~~, ~~CI/CD~~,
~~Backup strategiyasi~~ (kod/skript tayyor, real konteynerda hali sinalmagan) — bajarildi.

Qolgan (tarif rejalar, refund siyosati, keng qamrovli integration testlar,
markazlashtirilgan loglash) — kattaroq va alohida rejalashtirish talab
qiladigan ishlar.
