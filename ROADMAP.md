# Bosh sahifa (Landing Page) — ROADMAP

**Sana:** 2026-09-08
**So'rov:** "бутун сайтни ўрганиб чиқиб, барча имкониятлари ҳақидаги маълумотни ўзида жамлаган БОШ саҳифа қилишимиз керак... Профессионал, чиройли, одамни ўзига жалб қиладиган, бошқа шу каби сайтлардан қолишмайдиган бўлиши керак."

Hozirgi bosh sahifa (`/index`) juda minimal edi — faqat sarlavha + 3 ta test-rejim kartasi.
Sayt (kurslar, testlar, o'qituvchi vositalari, Telegram bot, forum, statistika,
ko'p tilli qo'llab-quvvatlash) to'liq o'rganib chiqilgach, quyidagi tuzilma
tasdiqlandi va amalga oshirildi.

## Tasdiqlangan tuzilma (yuqoridan pastga)

1. **Hero — karusel (slaydlar)** — Bootstrap Carousel, bir nechta targ'ibot
   slaydi (xush kelibsiz + CTA, kurslar targ'iboti, Telegram bot targ'iboti).
   Shaxsiylashtirilgan salomlashuv (`${#authentication.name}`) birinchi
   slaydda.
2. **"Bu yerda nima bor?"** — imkoniyatlar tarmog'i (grid): Testlar, Kurslar,
   Statistika, O'qituvchilar uchun, Telegram bot, Fikr-mulohaza.
3. **Test rejimlari** (hozirgi 3 ta karta — Amaliyot/Imtihon/Murakkab) —
   **saqlanib qoladi**, Hero'dan keyin (asosiy, darhol sinab ko'riladigan
   funksiya).
4. **Kurslar vitrinasi** — karusel, DB'dan dinamik (chop etilgan, so'nggi 6 ta
   kurs — `UserMvcController#login_success`, `CourseService.listCatalog`).
5. **"Qanday ishlaydi"** — 4 qadamli qo'llanma.
6. **O'qituvchilar uchun** — alohida blok (guruh boshqarish, savollar
   to'plami, topshiriq berish).
7. **Telegram bot promo** — bot havolasi bilan.
8. **Jonli statistika paneli** — foydalanuvchilar/kurslar/yechilgan testlar
   soni (DB'dan hisoblanadi: `UserRepository.count()`,
   `CourseRepository.countByPublishedTrue()`,
   `TestSessionRepository.countByFinishedAtIsNotNull()`).
9. **FAQ** — Bootstrap Accordion (animatsion collapse).
10. **Yakuniy CTA + Footer** — hozirgi footer saqlanadi, kengaytiriladi.

## Qo'shimcha (o'zim taklif qilgan)

- SEO/Open Graph teglar (`<meta description>` hozir bo'sh edi).
- Scroll-fade-in animatsiyasi (IntersectionObserver, index.js) — bo'limlar
  ko'rinish maydoniga kirganda yumshoq paydo bo'ladi.
- Bootstrap animatsion elementlari: Carousel (slayd o'tishlari), Accordion
  (FAQ), kartalarda hover-transition (soyalar/ko'tarilish).

## Texnik eslatmalar

- `/index` autentifikatsiya talab qiladi (SecurityConfig) — shaxsiylashtirish
  va `courseService.listCatalog(user)` xavfsiz ishlatiladi.
- Kurslar vitrinasi — faqat `published=true` kurslar, `CourseDto.authorName`
  (2026-09-07'da qo'shilgan) muallif qatorini ko'rsatish uchun ishlatiladi.
- Statistika — real DB hisob-kitoblari, hardcode qilingan raqamlar EMAS.
