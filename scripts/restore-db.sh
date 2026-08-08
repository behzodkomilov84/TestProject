#!/bin/bash
# Zaxira nusxadan (backup.sh yaratgan .sql.gz fayl) bazani tiklaydi.
#
# Ishlatilishi (production serverda, backup konteyneri ichida):
#   docker compose -f docker-compose.prod.yml exec backup bash /scripts/restore-db.sh /backups/test_project-20260101_030000.sql.gz
#
# DIQQAT: bu amal joriy bazadagi BARCHA ma'lumotlarni zaxiradagi holat bilan
# ALMASHTIRADI (qaytarib bo'lmaydi). Ishlatishdan oldin joriy holatning ham
# zaxirasini oling (backup.sh avtomatik oladi, lekin ehtiyot uchun qo'lda
# ham tekshiring: `docker compose -f docker-compose.prod.yml exec backup ls -la /backups`).
set -euo pipefail

DUMP_FILE="${1:?Foydalanish: restore-db.sh <dump-fayli.sql.gz>}"

if [ ! -f "$DUMP_FILE" ]; then
    echo "❌ Fayl topilmadi: $DUMP_FILE" >&2
    exit 1
fi

echo "⚠️  DIQQAT: '$MYSQL_DATABASE' bazasi '$DUMP_FILE' fayli bilan ALMASHTIRILADI."
read -r -p "Davom etasizmi? (ha yozib Enter bosing): " confirm
if [ "$confirm" != "ha" ]; then
    echo "Bekor qilindi."
    exit 0
fi

# DIQQAT: mysqldump fayli faqat dump OLINGAN paytda mavjud bo'lgan
# jadvallar uchun "DROP TABLE IF EXISTS" beradi — agar shu dump olingandan
# KEYIN yangi jadval qo'shilgan bo'lsa (masalan yangi Liquibase migratsiyasi
# orqali), oddiy import qilish uni "unutib qoldiradi" (o'chirmaydi). Buni
# oldini olish uchun avval bazani butunlay o'chirib, dump ichidagi
# "CREATE DATABASE" orqali qaytadan yaratamiz — shunda natija HAQIQATAN HAM
# dump olingan paytdagi holatning aniq nusxasi bo'ladi.
mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`;"

gunzip -c "$DUMP_FILE" | mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD"

echo "✅ Tiklandi: $DUMP_FILE"
echo "ℹ️  Ilovani qayta ishga tushirish tavsiya etiladi: docker compose -f docker-compose.prod.yml restart app"
