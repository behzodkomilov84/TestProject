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
- **Cheklov**: `ONLINE` manba hozircha faqat enum sifatida tayyor — Payme/Click kabi haqiqiy
  to'lov shlyuzi ulanmagan. Buning uchun quyidagilar kerak bo'ladi:
  - Payme/Click'da merchant (savdogar) akkaunt ochish va kalitlarni olish;
  - webhook endpoint (`/api/payments/online/callback`) yozish va shlyuz tomonidan
    yuboriladigan holat (success/fail) hamda summani tekshirish;
  - tranzaksiya ID orqali takroriy so'rovlarni oldini olish (idempotency).
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
- **Email integratsiyasi qisman** — parolni tiklashda bor (Brevo SMTP), lekin
  ro'yxatdan o'tishda email tasdiqlash yoki hisobotlarni email orqali yuborish
  hali yo'q.
- **To'lov tarixi va hisobot** — OWNER uchun "qaysi oyda qancha ADMIN to'lovi tushdi"
  degan statistika/hisobot sahifasi yo'q (buni keyingi qadam sifatida qo'shish oson,
  chunki `Subscription` jadvali allaqachon shu ma'lumotni saqlaydi).
- **Obunani avtomatik eslatish** — ADMIN obunasi tugashiga 3 kun qolganda foydalanuvchiga
  Telegram orqali "obunangizni yangilang" degan eslatma yuborilmaydi (hozir `DeadlineReminderService`
  bor, xuddi shu patternda `SubscriptionReminderService` qo'shish mumkin).
- **Guruh/sinf darajasida chegirma yoki tarif rejalar** yo'q — hammaga bir xil erkin summa.

## 4. Kod sifati va infratuzilma

- **Avtomatik testlar deyarli yo'q** (unit/integration test topilmadi). Professional
  loyihada har bir servis (ayniqsa `SubscriptionService`, `QuestionService`,
  `UserServiceImpl`) uchun kamida asosiy stsenariylar test qilinishi kerak.
- **CI/CD yo'q** — GitHub Actions/GitLab CI orqali har commit'da avtomatik build+test
  ishga tushirish yo'q.
- **Backup strategiyasi yo'q** — MySQL ma'lumotlar bazasi va `uploads/` papkasi uchun
  avtomatik zaxira nusxa olish (masalan, kunlik dump + bulutga yuklash) sozlanmagan.
- **Loglash markazlashtirilmagan** — hozir faqat konsolga/faylga yoziladi, production'da
  xatolarni kuzatish uchun Sentry kabi xizmat ulash tavsiya etiladi.

## 5. Huquqiy / monetizatsiya

- **Foydalanish shartlari va maxfiylik siyosati** (Terms of Service, Privacy Policy)
  sahifalari yo'q — pullik xizmat taqdim etilar ekan, bu huquqiy jihatdan zarur.
- **To'lov qaytarish (refund) siyosati** aniqlanmagan — agar ADMIN huquqi noto'g'ri
  berilgan/bekor qilinishi kerak bo'lsa, qanday tartibda pul qaytarilishi hujjatlashtirilmagan.

## Ustuvorlik bo'yicha tavsiya

~~1. Parolni tiklash~~, ~~2. Login urinishlarini cheklash~~, ~~3. Rol audit log~~,
~~4. Bildirishnoma markazi~~ va ~~5. HTTPS/SSL~~ — bajarildi.

Qolgan, nisbatan tez qo'shsa bo'ladigan narsalar:
1. To'lov tarixi/hisobot sahifasi OWNER uchun (mavjud `Subscription` ma'lumotidan).
2. Obuna tugashi haqida avtomatik Telegram eslatmasi (`DeadlineReminderService`
   patternida).

Payme/Click integratsiyasi, avtomatik testlar va CI/CD — kattaroq va alohida
rejalashtirish talab qiladigan ishlar.
