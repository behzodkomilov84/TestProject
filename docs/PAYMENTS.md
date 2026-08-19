# Click integratsiyasi — ishga tushirish qo'llanmasi

Bu hujjat `ROLE_ADMIN` obunasini foydalanuvchi o'zi (OWNER ishtirokisiz) onlayn
sotib olishi uchun Click to'lov tizimini qanday ulashni tushuntiradi.

> **Eslatma**: loyihada avval Payme integratsiyasi ham bor edi, lekin olib
> tashlandi (2026) — Payme "o'zini o'zi band qilgan" (SZ — samozanyatiy)
> maqomdagilar bilan shartnoma tuzmasligi aniqlandi, eng kami **yakka
> tartibdagi tadbirkor (YaTT)** maqomi talab qilinadi.

## Qanday ishlaydi (arxitektura)

1. Foydalanuvchi `/profile` sahifasida muddatni tanlab, "Click orqali to'lash"
   tugmasini bosadi.
2. Backend `PaymentOrder` (bizning ichki "buyurtma") yaratadi va foydalanuvchini
   Click'ning to'lov sahifasiga (checkout link) yo'naltiradi.
3. Foydalanuvchi u yerda to'laydi.
4. Click'ning **o'z serverlari** bizning webhook manzilimizga (quyida)
   so'rov yuboradi — to'lovni tasdiqlash/bekor qilish shu yerda sodir
   bo'ladi, foydalanuvchi brauzeri orqali emas.
5. Webhook muvaffaqiyatli to'lovni tasdiqlasa — `ROLE_ADMIN` avtomatik
   beriladi (xuddi OWNER qo'lda tasdiqlagandek, faqat `source=ONLINE`).

Kodning joylashuvi:
- `entity/PaymentOrder.java`, `entity/PaymentTransaction.java` — ma'lumotlar bazasi.
- `service/payment/ClickService.java` — Prepare/Complete protokol logikasi.
- `service/payment/PaymentOrderService.java` — bizning ichki biznes-logika (Subscription bilan bog'lash).
- `controller/api/payment/*` — HTTP endpoint'lar.

## ⚠️ Muhim ogohlantirish

Xato kodlari va chegara holatlarini rasmiy hujjat bilan solishtiring:
https://docs.click.uz/

## 1-qadam: Click merchant ro'yxatdan o'tish

⚠️ https://merchant.click.uz (`mc.click.uz`) to'g'ridan-to'g'ri **kirish**
(login) sahifasi, ro'yxatdan o'tish shu yerda emas. Ro'yxatdan o'tish
https://business.click.uz orqali, va u ham hujjatlar talab qiladi
(2026-yil holatiga tekshirib ko'rilgan):

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
   Faollashtirish uchun quyidagi 1.1-qadamga qarang.

**Muhim**: yuqoridagi hujjatlar (passport nusxasi, STIR, bank rekvizitlari)
— shaxsiy/moliyaviy ma'lumot. Bularni faqat OWNER'ning o'zi, to'g'ridan-
to'g'ri Click'ning rasmiy sahifasida kiritishi kerak — boshqa hech kimga
(jumladan AI-yordamchiga) berilmasligi kerak.

### 1.1-qadam: Click xizmatini faollashtirish (Click'ning o'zidan olingan ko'rsatma)

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
   domeningiz, IP-manzilingiz va portingizni (odatda 443, HTTPS) yuborib,
   firewall whitelist'ga qo'shishlarini so'rang. IP **statik** bo'lishi
   shart — o'zgartirishdan oldin ham ular oldindan xabardor qilinishi kerak.
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
   - `ClickException` kabi holatlarda ham `PaymentTransaction` jadvalida
     yozuv qolishini tekshiring — muvaffaqiyatsiz urinish ham
     kuzatilishi kerak.

## 2-qadam: `.env` faylini to'ldirish

```env
PAYMENT_PRICE_PER_MONTH_SOM=50000

CLICK_SERVICE_ID=...
CLICK_MERCHANT_ID=...
CLICK_SECRET_KEY=...
```

To'ldirilmagan bo'lsa — `/profile` sahifasidagi "Onlayn to'lov" bloki
avtomatik ko'rinmaydi (funksiya "o'chirilgan" holatda qoladi, ilova
normal ishlashda davom etadi).

## 3-qadam: Ilovani qayta ishga tushirish va sinash

1. `.env`ni to'ldirgach, ilovani qayta ishga tushiring.
2. `/profile` sahifasiga USER sifatida kiring — "Onlayn to'lov" bloki
   ko'rinishi kerak.
3. Kichik summa bilan haqiqiy to'lovni sinab ko'ring (1-qadamdagi
   "Sinov to'lovi" bo'limiga qarang — Click'da sandbox muhiti yo'q).
4. To'lovdan keyin `subscriptions` va `payment_orders`/`payment_transactions`
   jadvallarini tekshiring — yangi `CONFIRMED` obuna va `PAID` order paydo
   bo'lishi kerak, foydalanuvchiga `ROLE_ADMIN` berilgan bo'lishi kerak.
