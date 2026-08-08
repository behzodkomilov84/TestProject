# Zaxira nusxalash (backup) va tiklash

Ushbu hujjat production serverdagi MySQL bazasi va `uploads/` papkasi
(foydalanuvchilar yuklagan rasm/video) uchun avtomatik zaxira nusxalash
strategiyasini tushuntiradi.

## Qanday ishlaydi

`docker-compose.prod.yml`dagi **`backup`** servisi — xuddi asosiy `mysql`
servisi bilan bir xil rasmiy `mysql:8.0` image'ini ishlatadi (shunda
`mysqldump` versiyasi serverning o'zi bilan 100% mos keladi, custom image
yasash shart emas). Fonda ishlab, har kuni **03:00**'da
([scripts/backup.sh](../scripts/backup.sh)):

1. `mysqldump --single-transaction` orqali bazaning to'liq, izchil
   (InnoDB jadvallarni bloklamaydigan) dump'ini oladi va gzip qiladi.
2. `uploads/` papkasini `.tar.gz` qilib arxivlaydi.
3. Ikkalasini serverning **host mashinasidagi `./backups` papkasiga**
   saqlaydi (named Docker volume emas — bind mount, shunda pastda
   tushuntirilgan offsite-sinxronizatsiya host darajasida oson bo'ladi),
   fayl nomiga vaqt tamg'asi qo'shib
   (`test_project-20260115_030000.sql.gz`).
4. `BACKUP_RETENTION_DAYS` (standart 14 kun) dan eski nusxalarni o'chiradi.
5. Konteyner birinchi marta ishga tushganda ham (03:00'ni kutmasdan)
   darhol bitta zaxira oladi.

Loglarni ko'rish:

```bash
docker compose -f docker-compose.prod.yml logs -f backup
```

## ⚠️ MUHIM: bu yolg'iz o'zi YETARLI EMAS

`./backups` papkasi **shu serverning o'zida** turadi. Agar server (disk,
VPS, datacenter) butunlay ishdan chiqsa yoki tasodifan o'chirilsa —
asosiy ma'lumot bilan BIRGA zaxira nusxa ham yo'qoladi. Bu faqat
"tasodifan noto'g'ri `DELETE`/migratsiya ishga tushirib qo'yish" kabi
holatlardan himoya qiladi, haqiqiy server halokatidan emas.

**Haqiqiy zaxira strategiyasi uchun nusxalarni serverdan TASHQARIGA
ko'chirish shart.** Buning uchun eng ishonchli yo'l — quyidagi kabi
HOST mashinaning o'zida (Docker konteyner ichida emas) alohida cron
job sozlash, chunki shunda serveringiz o'zi tanlagan Linux distributivi
(Ubuntu/Debian va h.k.) uchun mo'ljallangan istalgan vositani (rclone,
`aws s3`, `rsync`, oddiy `scp`) erkin o'rnatishingiz mumkin.

## Zaxirani tashqi joyga (bulut) ko'chirish — host cron + rclone

[rclone](https://rclone.org) — 40+ bulut provayderni (Backblaze B2, AWS
S3, Google Drive, Dropbox va h.k.) qo'llab-quvvatlaydigan bepul vosita.

**Sozlash qadamlari** (masalan Backblaze B2 — eng arzon variantlardan
biri, lekin istalgan rclone qo'llab-quvvatlaydigan provayder ishlaydi),
**serverning o'zida** (Docker'dan tashqarida):

1. rclone'ni serverga o'rnating (rasmiy o'rnatish skripti — deyarli
   har qanday Linux distributivida ishlaydi):

   ```bash
   curl https://rclone.org/install.sh | sudo bash
   ```

2. Sozlang (interaktiv so'rovlarga javob berib, yangi "remote" yarating —
   masalan nomi `b2`, turi tanlagan provayderingiz):

   ```bash
   rclone config
   ```

3. Cron job qo'shing (`crontab -e`) — masalan har kuni 04:00'da (backup
   konteyneri 03:00'da tugatgach) `./backups` papkasini bulutga
   sinxronlash:

   ```cron
   0 4 * * * rclone sync /to'liq/yo'l/loyihaga/backups b2:mening-backup-paketim >> /var/log/backup-sync.log 2>&1
   ```

   (`/to'liq/yo'l/loyihaga/backups` — loyiha joylashgan papkadagi
   `backups/` ning to'liq yo'li, masalan `/home/user/TestProject/backups`.)

Shundan keyin har kunlik zaxira avtomatik ravishda ham serverda, ham
bulutda saqlanadi.

**Sinash uchun** (server buzilishini kutmasdan):

```bash
rclone sync /to'liq/yo'l/loyihaga/backups b2:mening-backup-paketim
rclone ls b2:mening-backup-paketim
```

### Muqobil: oddiy `rsync`/`scp` (ikkinchi serveringiz bo'lsa)

Bulut kerak bo'lmasa, `./backups`ni boshqa o'zingizning serveringizga
ko'chirish ham yetarli:

```cron
0 4 * * * rsync -az --delete /to'liq/yo'l/loyihaga/backups/ user@boshqa-server:/backups/smart-test/ >> /var/log/backup-sync.log 2>&1
```

## Qo'lda zaxira olish (kutmasdan, darhol)

```bash
docker compose -f docker-compose.prod.yml restart backup
```

## Zaxiralarni ko'rish

```bash
ls -lh ./backups
```

(Named volume emasligi sababli — to'g'ridan-to'g'ri hostdagi oddiy papka,
`docker cp` kerak emas.)

## Tiklash (restore)

⚠️ **Bu amal joriy bazadagi barcha ma'lumotlarni zaxiradagi holat bilan
ALMASHTIRADI — qaytarib bo'lmaydi.** Avval joriy holatning ham zaxirasi
borligiga ishonch hosil qiling.

```bash
docker compose -f docker-compose.prod.yml exec backup bash /scripts/restore-db.sh \
    /backups/test_project-20260115_030000.sql.gz
```

Skript tasdiqlash so'raydi (`ha` deb yozib Enter bosish kerak). Tiklangach,
ilovani qayta ishga tushiring:

```bash
docker compose -f docker-compose.prod.yml restart app
```

`uploads/` papkasini tiklash uchun (agar kerak bo'lsa):

```bash
docker compose -f docker-compose.prod.yml exec backup \
    tar -xzf /backups/uploads-20260115_030000.tar.gz -C /
```

## Sozlash

| O'zgaruvchi | Standart | Tavsif |
|---|---|---|
| `BACKUP_RETENTION_DAYS` | `14` | Necha kunlik zaxira `./backups`da saqlansin |

Zaxira vaqtini o'zgartirish uchun (standart 03:00) `docker-compose.prod.yml`
dagi `backup` servisiga `BACKUP_HOUR`/`BACKUP_MINUTE` environment
o'zgaruvchilarini qo'shing.

## Bilib qo'yish kerak bo'lgan cheklovlar

- Zaxira `root` MySQL foydalanuvchisi orqali olinadi (ilovaning o'zi ham
  shu foydalanuvchi bilan ulanadi — mavjud loyiha konfiguratsiyasiga mos).
  Production'da faqat `SELECT`/`LOCK TABLES`/`SHOW VIEW` huquqiga ega
  alohida "backup" MySQL foydalanuvchisi yaratish qo'shimcha xavfsizlik
  bo'lardi (hozircha qilinmagan).
- `./backups` papkasi maxfiy ma'lumot (foydalanuvchilar jadvali, parollar
  hash'i va h.k.) saqlaydi — bu papka `.gitignore`da (git'ga tushmaydi),
  lekin serverning o'zida ham fayl huquqlarini cheklang (masalan
  `chmod 700 ./backups`) va bulut bucket'ingizni ham xususiy (private)
  qilib qo'ying.
