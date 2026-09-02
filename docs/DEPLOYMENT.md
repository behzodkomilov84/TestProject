# Production'ga deploy: HTTPS/SSL (nginx + Let's Encrypt)

Ushbu hujjat ilovani production serverda **nginx reverse proxy** va **Let's
Encrypt** (bepul, avtomatik yangilanadigan) SSL sertifikat bilan ishga
tushirish tartibini tushuntiradi.

## Talablar

- Docker va Docker Compose o'rnatilgan server (Linux tavsiya etiladi).
- **Domen nomi** — masalan `smart-test.uz` — va u DNS'da shu serverning
  ochiq IP-manziliga (A-record) yo'naltirilgan bo'lishi shart. Let's
  Encrypt "localhost" yoki IP-manzil uchun sertifikat bermaydi.
- 80 va 443 portlar internetdan ochiq bo'lishi shart (Let's Encrypt shu
  portlar orqali domenga egalik qilinganini tekshiradi).

## 1. `.env` faylini tayyorlash

```bash
cp .env.example .env
```

`.env` faylida quyidagilarni to'ldiring (`docs/DEPLOYMENT.md` emas, o'zi
`.env.example`dagi izohlarga qarang):

- `DB_USERNAME`, `DB_PASSWORD` — kuchli parol qo'ying.
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`
- `SMTP_*`, `MAIL_FROM` — parolni tiklash email kanali uchun (ixtiyoriy).
- **`DOMAIN`** — masalan `smart-test.uz`.
- **`LETSENCRYPT_EMAIL`** — sertifikat muddati haqida ogohlantirish keladi.

## 2. Birinchi marta: sertifikat olish

```bash
./scripts/init-letsencrypt.sh
```

Bu skript:
1. Vaqtinchalik ("dummy") sertifikat yaratadi — nginx ACME challenge uchun
   ishga tushishi kerak, lekin haqiqiy sertifikat hali yo'q ("tuxum-tovuq"
   muammosi).
2. Nginx'ni shu dummy sertifikat bilan ishga tushiradi.
3. Let's Encrypt'dan **haqiqiy** sertifikat so'raydi (HTTP-01 tekshiruvi
   orqali, `http://DOMAIN/.well-known/acme-challenge/` yo'li orqali).
4. Nginx'ni haqiqiy sertifikat bilan qayta yuklaydi.
5. Qolgan servislarni (`app`, `mysql`) ishga tushiradi.

Xatolik chiqsa — eng ko'p uchraydigan sabab: DNS hali to'liq
tarqalmagan (yangi domen bo'lsa, bir necha soat kutish kerak bo'lishi
mumkin) yoki 80-port firewall'da yopiq.

## 3. Keyingi deploy'lar

Sertifikat allaqachon olingan bo'lsa, oddiy:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## 4. Sertifikat yangilanishi

`certbot` konteyneri fonda ishlab, har 12 soatda `certbot renew`ni
chaqiradi — sertifikat muddati (90 kun) tugashiga ~30 kun qolganda
avtomatik yangilanadi. Qo'lda hech narsa qilish shart emas.

## 5. Docker build cache tozalash

Har bir `--build` deploy'da yangi build-cache qatlamlari qo'shiladi,
eskilari o'zidan-o'zi o'chmaydi — vaqt o'tishi bilan bir necha
GIGABAYTgacha yig'ilib, disk joyini band qilib qo'yishi mumkin
(haqiqiy hodisa: 2026-09-02, 23GB'gacha yetgan edi).

`scripts/docker-cleanup.sh` — FAQAT ishlatilmayotgan build cache'ni
tozalaydi (image/konteyner/hajmlarga tegmaydi). Serverda har kuni
avtomatik ishga tushishi uchun root crontab'ga bir marta qo'shiladi:

```bash
crontab -e
# quyidagi qatorni qo'shing (har kuni 04:00'da, kunlik backup'dan keyin):
0 4 * * * /opt/studygrow/scripts/docker-cleanup.sh >> /var/log/docker-cleanup.log 2>&1
```

Qo'lda darhol ishga tushirish: `/opt/studygrow/scripts/docker-cleanup.sh`.

## Muhim: dev vs prod farqi

- **`docker-compose.yml`** (dev) — `app` va `mysql` to'g'ridan-to'g'ri
  portlarda (8080, 3306) ochiq, SSL yo'q. Local ishlash/test uchun.
- **`docker-compose.prod.yml`** (prod) — `app`/`mysql` internetga
  yopiq, faqat `nginx` orqali (80/443) kirish mumkin, SSL bor.

Ikkalasini aralashtirmang — production serverda faqat
`docker-compose.prod.yml` ishlatilishi kerak.

## Xavfsizlik eslatmasi

`certbot/` papkasi (haqiqiy sertifikat + shaxsiy kalitlar) `.gitignore`
orqali git'ga tushmaydi. Uni hech qachon commit qilmang yoki boshqa
joyga ochiq nusxalamang.
