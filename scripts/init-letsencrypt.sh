#!/bin/bash
#
# Birinchi marta production serverda SSL sozlashda BIR MARTA ishga
# tushiriladigan skript. Muammo: nginx konfiguratsiyasi haqiqiy Let's
# Encrypt sertifikatini talab qiladi, lekin sertifikatni olish uchun avval
# nginx (ACME challenge uchun) ishlab turishi kerak — shu "tuxum-tovuq"
# muammosini hal qilish uchun avval vaqtinchalik (dummy) sertifikat bilan
# nginx'ni ishga tushiramiz, so'ng uni haqiqiy sertifikat bilan almashtiramiz.
#
# Ishlatish:
#   1. .env faylida DOMAIN va LETSENCRYPT_EMAIL to'ldirilgan bo'lishi shart.
#   2. DNS'da DOMAIN shu serverning IP-manziliga yo'naltirilgan bo'lishi shart.
#   3. ./scripts/init-letsencrypt.sh
#
# Qayta ishga tushirish shart emas — bundan keyin certbot konteyneri
# sertifikatni avtomatik yangilab turadi (docker-compose.prod.yml'ga qarang).

set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [ -z "${DOMAIN:-}" ]; then
  echo "❌ .env faylida DOMAIN ko'rsatilmagan (masalan: DOMAIN=smart-test.uz)"
  exit 1
fi

if [ -z "${LETSENCRYPT_EMAIL:-}" ]; then
  echo "❌ .env faylida LETSENCRYPT_EMAIL ko'rsatilmagan (sertifikat muddati haqida ogohlantirish shu emailga keladi)"
  exit 1
fi

COMPOSE="docker compose -f docker-compose.prod.yml"
RSA_KEY_SIZE=4096
CERT_PATH="./certbot/conf/live/$DOMAIN"

echo "### 1/5: Vaqtinchalik (dummy) sertifikat yaratilmoqda — $DOMAIN uchun ..."
mkdir -p "$CERT_PATH" ./certbot/www
openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
  -keyout "$CERT_PATH/privkey.pem" \
  -out "$CERT_PATH/fullchain.pem" \
  -subj "/CN=localhost"

echo "### 2/5: Nginx dummy sertifikat bilan ishga tushirilmoqda ..."
$COMPOSE up -d nginx

echo "### 3/5: Dummy sertifikat o'chirilmoqda, haqiqiy sertifikat so'ralmoqda ..."
rm -rf "./certbot/conf/live/$DOMAIN" "./certbot/conf/archive/$DOMAIN" "./certbot/conf/renewal/$DOMAIN.conf"
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    --email $LETSENCRYPT_EMAIL \
    -d $DOMAIN \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --non-interactive" certbot

echo "### 4/5: Nginx haqiqiy sertifikat bilan qayta yuklanmoqda ..."
$COMPOSE restart nginx

echo "### 5/5: Qolgan servislar (app, mysql) ishga tushirilmoqda ..."
$COMPOSE up -d

echo "✅ Tayyor! https://$DOMAIN ishlashi kerak."
echo "   Sertifikat bundan keyin certbot konteyneri tomonidan avtomatik yangilanadi."
