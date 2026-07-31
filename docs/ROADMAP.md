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

- **Parolni tiklash (forgot password) yo'q** — foydalanuvchi parolni unutsa, hech qanday
  yo'l bilan tiklay olmaydi. Email yoki Telegram orqali tasdiqlash kodi yuborish kerak.
- **Login urinishlarini cheklash yo'q** — brute-force hujumga qarshi himoya (masalan,
  5 marta noto'g'ri urinishdan keyin vaqtincha bloklash) qo'shilmagan.
- **Rol o'zgarishlari uchun audit log yo'q** — kim, qachon, kimga qanday rol berganini
  saqlaydigan tarix yo'q (hozir faqat `Subscription` orqali berilgan ADMIN'lar uchun
  qisman tarix bor, lekin checkbox orqali qo'lda berilgan/olib tashlangan rollar
  butunlay izsiz).
- **HTTPS/SSL** — production muhitda albatta reverse proxy (nginx) orqali SSL sertifikat
  bo'lishi shart, hozir loyihada bunga oid konfiguratsiya yo'q.
- **Fayl yuklashda antivirus/tekshiruv yo'q** — foydalanuvchi yuklagan rasm/videolar
  zararli kodga ega bo'lishi mumkin (kamdan-kam, lekin professional tizimda tekshiriladi).

## 3. Funksional kamchiliklar / yetishmayotgan logika

- **Bildirishnoma tizimi** — hozir faqat Telegram bot orqali eslatma bor (topshiriq
  muddati). Saytning o'zida bildirishnoma markazi (masalan, "ADMIN so'rovingiz
  tasdiqlandi", "yangi topshiriq berildi") yo'q.
- **Email integratsiyasi umuman yo'q** — ro'yxatdan o'tishda email tasdiqlash,
  parol tiklash, hisobotlarni email orqali yuborish kabi funksiyalar qo'shilishi mumkin.
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

Eng katta amaliy foyda beradigan va nisbatan tez qo'shsa bo'ladigan narsalar:
1. Parolni tiklash (email yoki Telegram orqali).
2. To'lov tarixi/hisobot sahifasi OWNER uchun (mavjud `Subscription` ma'lumotidan).
3. Login urinishlarini cheklash (Spring Security'da tayyor yechimlar bor).
4. Obuna tugashi haqida avtomatik Telegram eslatmasi.

Payme/Click integratsiyasi va to'liq audit log tizimi — kattaroq va alohida
rejalashtirish talab qiladigan ishlar.
