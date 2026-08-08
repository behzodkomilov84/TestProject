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

gunzip -c "$DUMP_FILE" | mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD"

echo "✅ Tiklandi: $DUMP_FILE"
echo "ℹ️  Ilovani qayta ishga tushirish tavsiya etiladi: docker compose -f docker-compose.prod.yml restart app"
