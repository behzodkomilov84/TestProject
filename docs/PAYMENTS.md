# Payme / Click integratsiyasi — ishga tushirish qo'llanmasi

Bu hujjat `ROLE_ADMIN` obunasini foydalanuvchi o'zi (OWNER ishtirokisiz) onlayn
sotib olishi uchun Payme va Click to'lov tizimlarini qanday ulashni tushuntiradi.

## Qanday ishlaydi (arxitektura)

1. Foydalanuvchi `/profile` sahifasida muddatni tanlab, "Payme orqali to'lash"
   yoki "Click orqali to'lash" tugmasini bosadi.
2. Backend `PaymentOrder` (bizning ichki "buyurtma") yaratadi va foydalanuvchini
   Payme/Click'ning to'lov sahifasiga (checkout link) yo'naltiradi.
3. Foydalanuvchi u yerda to'laydi.
4. Payme/Click'ning **o'z serverlari** bizning webhook manzilimizga
   (quyida) so'rov yuboradi — to'lovni tasdiqlash/bekor qilish shu yerda
   sodir bo'ladi, foydalanuvchi brauzeri orqali emas.
5. Webhook muvaffaqiyatli to'lovni tasdiqlasa — `ROLE_ADMIN` avtomatik
   beriladi (xuddi OWNER qo'lda tasdiqlagandek, faqat `source=ONLINE`).

Kodning joylashuvi:
- `entity/PaymentOrder.java`, `entity/PaymentTransaction.java` — ma'lumotlar bazasi.
- `service/payment/PaymeService.java`, `service/payment/ClickService.java` — protokol logikasi.
- `service/payment/PaymentOrderService.java` — bizning ichki biznes-logika (Subscription bilan bog'lash).
- `controller/api/payment/*` — HTTP endpoint'lar.

## ⚠️ Muhim ogohlantirish

Ushbu implementatsiya Payme/Click'ning rasmiy hujjatlariga asoslanib
yozilgan, lekin **hech qachon haqiqiy pul bilan sinalmagan** (buning uchun
haqiqiy merchant akkaunt kerak, bu loyihada yo'q edi). Production'ga
chiqarishdan oldin albatta:

1. Har ikkala tizimning **test (sandbox) muhitida** to'liq sinovdan o'tkazing.
2. Payme/Click support jamoasi bilan **sertifikatsiya** jarayonidan o'ting
   (ular odatda test to'lovlarini o'tkazib, webhook javoblaringizni tekshiradi).
3. Xato kodlari va chegara holatlarini (masalan Payme tranzaksiyaning
   ~12 soatlik "muddati o'tishi") rasmiy hujjat bilan solishtiring:
   - Payme: https://developer.help.paycom.uz/
   - Click: https://docs.click.uz/

## 1-qadam: Payme merchant ro'yxatdan o'tish

⚠️ **Bu oddiy "ro'yxatdan o'tish" emas** — Payme Business'da hozircha o'zi
uchun (developer sifatida) darhol sandbox kalit oladigan tayyor forma yo'q.
Jarayon menejer orqali boradi (2026-yil holatiga real tekshirib ko'rilgan):

1. https://business.payme.uz — sahifada faqat telefon raqamingizni qoldirasiz
   ("Подключить"/"Sinab ko'rish" tugmasi). Kompaniya/IP ma'lumotlarini
   kiritadigan alohida forma yo'q — bularni menejer keyingi bosqichda so'raydi.
2. Payme menejeri sizga qo'ng'iroq qiladi (mijozlar sharhiga ko'ra, odatda
   1 kun ichida) — kompaniya/YaTT ma'lumotlari, shartnoma shartlari
   muhokama qilinadi.
3. Shartnoma (odatda elektron, ERI orqali) imzolangach, sizga **shaxsiy
   kabinet** ochiladi — shundagina "Kassalar" bo'limida kassa yaratib,
   **Merchant ID** va **Kalit (Key)**ni olasiz.
4. Callback (webhook) URL sifatida ko'rsating:
   ```
   https://<sizning-domeningiz>/api/payments/payme/webhook
   ```
5. Test bosqichida checkout bazasi `https://test.paycom.uz` bo'lishi mumkin —
   shu holda `.env`da `PAYME_CHECKOUT_BASE_URL=https://test.paycom.uz` qo'shing
   (aniq sandbox tartibini shaxsiy kabinet ochilgach menejerdan so'rang).

## 2-qadam: Click merchant ro'yxatdan o'tish

⚠️ Xuddi Payme kabi — https://merchant.click.uz (`mc.click.uz`) to'g'ridan-
to'g'ri **kirish** (login) sahifasi, ro'yxatdan o'tish shu yerda emas.
Ro'yxatdan o'tish https://business.click.uz orqali, va u ham hujjatlar talab
qiladi (2026-yil holatiga tekshirib ko'rilgan):

1. https://business.click.uz — "Tezkor ulanish" formasi orqali ariza
   qoldirasiz (STIR/soliq raqami va aloqa ma'lumotlari bilan).
2. **Kerakli hujjatlar** (rasmiy FAQ'ga ko'ra): rahbarning passporti nusxasi,
   kompaniya ro'yxatdan o'tganligi guvohnomasi, O'zbekistondagi bank hisob
   raqami (va MFO), MXIK/QQS kodlari (fiskalizatsiya uchun).
   - **Yangilanish (real tekshirilgan)**: "**o'zini o'zi band qilgan**"
     (SZ — samozanyatiy) maqomi ham **Shop API** (aynan bizning
     `ClickService.java` implementatsiya qilgan Prepare/Complete sxemasi)
     integratsiyasi uchun yetarli ekan — kompaniya (MCHJ) bo'lish shart
     emas. Ariza `business.click.uz` orqali yuboriladi, Click'ning
     ulanish/sotuv/integratsiya bo'limlari bilan aloqa o'rnatiladi, va
     natijada **Service ID**, **Merchant ID**, **Secret key** beriladi.
3. Shartnoma **Didox** (O'zbekistonning rasmiy elektron hujjat almashish
   tizimi, ERI/elektron raqamli imzo talab qiladi) orqali imzolanadi.
4. Kalitlarni olgach — **xizmat standart holatda O'CHIRILGAN** turadi!
   Faollashtirish uchun quyidagi 2.1-qadamga qarang.

**Muhim**: yuqoridagi hujjatlar (passport nusxasi, STIR, bank rekvizitlari)
— shaxsiy/moliyaviy ma'lumot. Bularni faqat OWNER'ning o'zi, to'g'ridan-
to'g'ri Payme/Click'ning rasmiy sahifasida kiritishi kerak — boshqa hech
kimga (jumladan AI-yordamchiga) berilmasligi kerak.

### 2.1-qadam: Click xizmatini faollashtirish (Click'ning o'zidan olingan ko'rsatma)

Kalitlar (Service ID/Merchant ID/Secret key) berilgandan keyin ham xizmat
ishlamaydi — quyidagi qadamlar bajarilmaguncha Click tomonidan qo'lda
faollashtirilishi kerak:

1. **Webhook manzilini kiritish**: `merchant.click.uz` kabinetiga kiring →
   "Сервисы" (Xizmatlar) bo'limi → jadvalning "Действие" (Amal) ustunidagi
   qalam belgisini bosing → tekshirish (Prepare) va natija (Complete)
   manzillarini kiriting. Bizning implementatsiyada ikkalasi ham **bitta**
   manzil (`action` parametri bilan farqlanadi):
   ```
   https://<sizning-domeningiz>/api/payments/click/webhook
   ```
2. **Static IP / TAS-IX**: agar serveringiz O'zbekiston TAS-IX tarmog'ida
   BO'LMASA (masalan, xorijiy VPS) — birinchi haqiqiy to'lovdan OLDIN Click
   integratsiya bo'limiga (ariza bilan birga berilgan mas'ul xodimga)
   domeningiz, IP-manzilingiz va portingizni yuborib, firewall whitelist'ga
   qo'shishlarini so'rang. IP **statik** bo'lishi shart — o'zgartirishdan
   oldin ham ular oldindan xabardor qilinishi kerak.
3. Webhook manzili kiritilgach, Click'ning mas'ul xodimiga (ariza javobida
   ko'rsatilgan aloqa) "tayyormiz" deb yozing — shundan keyin ular xizmatni
   faollashtiradi.
4. **Sinov to'lovi** (Click'ning o'z tavsiyasi — bu SANDBOX EMAS, kichik
   summa bilan HAQIQIY tranzaksiya):
   - Telefonga **Click Up** ilovasini o'rnating.
   - Quyidagi havolani oching (o'zingizning Service ID/Merchant ID bilan):
     ```
     https://my.click.uz/services/pay/?service_id=<SERVICE_ID>&merchant_id=<MERCHANT_ID>&amount=1000&transaction_param=test
     ```
   - Chiqqan formada telefon raqami yoki karta ma'lumotlarini kiriting —
     hisob (invoys) chiqariladi.
   - Click Up ilovasida shu hisobni to'lang. Xatolik bo'lsa —
     Prepare/Complete so'rov-javoblarining logini Click'ning integratsiya
     guruhiga yuboring (shuning uchun webhook endpoint'da so'rov/javoblarni
     to'liq loglash tavsiya etiladi).
   - `PaymeException`/`ClickException` kabi holatlarda ham `PaymentTransaction`
     jadvalida yozuv qolishini tekshiring — muvaffaqiyatsiz urinish ham
     kuzatilishi kerak.

## 3-qadam: `.env` faylini to'ldirish

```env
PAYMENT_PRICE_PER_MONTH_SOM=50000

PAYME_MERCHANT_ID=...
PAYME_KEY=...

CLICK_SERVICE_ID=...
CLICK_MERCHANT_ID=...
CLICK_SECRET_KEY=...
```

Ikkalasi ham to'ldirilmagan bo'lsa — `/profile` sahifasidagi "Onlayn to'lov"
bloki avtomatik ko'rinmaydi (funksiya "o'chirilgan" holatda qoladi, ilova
normal ishlashda davom etadi).

## 4-qadam: Ilovani qayta ishga tushirish va sinash

1. `.env`ni to'ldirgach, ilovani qayta ishga tushiring.
2. `/profile` sahifasiga USER sifatida kiring — "Onlayn to'lov" bloki
   ko'rinishi kerak.
3. Test (sandbox) kartasi bilan to'lovni sinab ko'ring.
4. To'lovdan keyin `subscriptions` va `payment_orders`/`payment_transactions`
   jadvallarini tekshiring — yangi `CONFIRMED` obuna va `PAID` order paydo
   bo'lishi kerak, foydalanuvchiga `ROLE_ADMIN` berilgan bo'lishi kerak.
