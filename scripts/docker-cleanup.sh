#!/bin/bash
# Docker BUILD CACHE vaqt o'tishi bilan juda katta hajmga yetib, server
# diskini band qilib qo'yishi mumkin — har bir "docker compose ... --build"
# deploy'da (docs/DEPLOYMENT.md) yangi qatlamlar qo'shiladi, eskilari esa
# (yangi app.jar COPY qilingani uchun) darhol "ishlatilmaydigan" bo'lib
# qoladi, lekin o'zi o'chmaydi. Haqiqiy production hodisa (2026-09-02):
# disk 83% ga yetib, tekshirilganda buning sababi backup fayllari (121MB,
# arzimas) emas, balki 23GB'dan ortiq to'plangan build cache ekani
# aniqlandi.
#
# Bu skript FAQAT ishlatilmayotgan (unused/dangling) build cache'ni
# tozalaydi — joriy image, ishlab turgan konteyner yoki hajmlarga
# (volumes — shu jumladan MySQL ma'lumotlari, uploads) HECH QANDAY
# TA'SIR QILMAYDI ("docker builder prune" faqat build-vaqtidagi oraliq
# qatlamlarni o'chiradi).
#
# Ishga tushirish: root crontab orqali, har kuni (masalan 04:00'da,
# kunlik zaxira nusxadan — backup.sh, odatda 03:00 — keyin). O'rnatish:
#   crontab -e
#   0 4 * * * /opt/studygrow/scripts/docker-cleanup.sh >> /var/log/docker-cleanup.log 2>&1
#
# docker-compose.prod.yml'dagi "backup" xizmatidan farqli, bu alohida
# konteyner sifatida EMAS — chunki Docker'ning o'zini boshqarish uchun
# host mashinaning Docker socket'iga (yoki uni konteynerga xavfli
# ravishda mount qilishga) ehtiyoj bo'lardi. Host'ning o'zida oddiy cron
# orqali ishlatish ancha sodda va xavfsiz.
set -euo pipefail

log() {
    echo "[$(date '+%F %T')] $1"
}

log "Docker build cache tozalash boshlandi..."

before=$(docker system df --format '{{.Type}}: {{.Size}} (reclaimable {{.Reclaimable}})' 2>/dev/null | grep -i "build cache" || true)
log "Oldin: ${before:-noma'lum}"

docker builder prune -f

after=$(docker system df --format '{{.Type}}: {{.Size}} (reclaimable {{.Reclaimable}})' 2>/dev/null | grep -i "build cache" || true)
log "Keyin: ${after:-noma'lum}"

log "Tozalash yakunlandi."
