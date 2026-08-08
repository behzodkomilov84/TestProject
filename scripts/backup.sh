#!/bin/bash
# Har kuni belgilangan vaqtda (standart 03:00) MySQL bazasi va uploads/
# papkasining zaxira nusxasini oladi, BACKUP_RETENTION_DAYS kundan eski
# nusxalarni o'chiradi. docker-compose.prod.yml'dagi "backup" servisi
# (docker/backup/Dockerfile — mysql:8.0 ustiga qurilgan, shuning uchun
# mysqldump alohida o'rnatilmasdan tayyor) shu skriptni ishga tushiradi.
#
# DIQQAT: bu papka ($BACKUP_DIR, named volume "backup-data") serverning
# O'ZIDA turadi. Agar server/disk butunlay ishdan chiqsa, shu zaxira ham
# u bilan birga yo'qoladi — bu HAQIQIY zaxira strategiyasi emas, faqat
# "tasodifan noto'g'ri SQL ishga tushirib qo'yish" kabi holatlar uchun
# himoya. Haqiqiy zaxira uchun RCLONE_REMOTE'ni sozlang (pastga qarang,
# docs/BACKUP.md'da batafsil).
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
UPLOADS_DIR="${UPLOADS_DIR:-/app/uploads}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_HOUR="${BACKUP_HOUR:-03}"
BACKUP_MINUTE="${BACKUP_MINUTE:-00}"

mkdir -p "$BACKUP_DIR"

log() {
    echo "[$(date '+%F %T')] $1"
}

run_backup() {
    local ts
    ts=$(date +%Y%m%d_%H%M%S)

    log "Zaxira nusxalash boshlandi..."

    # --- 1. MySQL dump (--single-transaction — InnoDB uchun ilova ishlashda
    # ham izchil "snapshot", jadvallarni bloklamaydi). ---
    local db_dump="$BACKUP_DIR/${MYSQL_DATABASE}-${ts}.sql.gz"
    if mysqldump \
        -h "$MYSQL_HOST" \
        -u "$MYSQL_USER" \
        -p"$MYSQL_PASSWORD" \
        --single-transaction \
        --routines \
        --triggers \
        --databases "$MYSQL_DATABASE" \
        | gzip > "$db_dump"; then
        log "✅ Baza zaxirasi saqlandi: $db_dump ($(du -h "$db_dump" | cut -f1))"
    else
        log "❌ Baza zaxirasi MUVAFFAQIYATSIZ (mysqldump xatolik qaytardi)"
        rm -f "$db_dump"
    fi

    # --- 2. Uploads papkasi (rasm/video) ---
    if [ -d "$UPLOADS_DIR" ] && [ -n "$(ls -A "$UPLOADS_DIR" 2>/dev/null)" ]; then
        local uploads_archive="$BACKUP_DIR/uploads-${ts}.tar.gz"
        if tar -czf "$uploads_archive" -C "$(dirname "$UPLOADS_DIR")" "$(basename "$UPLOADS_DIR")"; then
            log "✅ Uploads zaxirasi saqlandi: $uploads_archive ($(du -h "$uploads_archive" | cut -f1))"
        else
            log "❌ Uploads zaxirasi MUVAFFAQIYATSIZ"
            rm -f "$uploads_archive"
        fi
    else
        log "ℹ️  Uploads papkasi bo'sh/topilmadi — o'tkazib yuborildi."
    fi

    # --- 3. Eski nusxalarni tozalash ---
    find "$BACKUP_DIR" -name "*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
    find "$BACKUP_DIR" -name "*.tar.gz" -mtime "+${RETENTION_DAYS}" -delete
    log "Tozalash: ${RETENTION_DAYS} kundan eski nusxalar o'chirildi."

    # --- 4. (Ixtiyoriy, ilg'or foydalanuvchilar uchun) Bulutga yuklash ---
    # Bu konteyner rasmiy "mysql:8.0" image'ining o'zi (mysqldump versiyasi
    # serverning o'zi bilan mos kelishi uchun qasddan shunday) — rclone unda
    # standart o'rnatilmagan. ASOSIY tavsiya etilgan usul — HOST mashinada
    # ("./backups" papkasi shu yerga bind-mount qilingan) alohida cron orqali
    # rclone/rsync/scp ishlatish, docs/BACKUP.md'da tushuntirilganidek. Agar
    # baribir shu konteynerning o'zida ishlatmoqchi bo'lsangiz — rclone'ni
    # o'zingiz custom image'da qo'shib, RCLONE_REMOTE'ni environment orqali
    # bering, shu quyidagi tekshiruv avtomatik ishlaydi.
    if [ -n "${RCLONE_REMOTE:-}" ]; then
        if command -v rclone >/dev/null 2>&1; then
            log "Tashqi joylashuvga yuklanmoqda: $RCLONE_REMOTE ..."
            if rclone copy "$BACKUP_DIR" "$RCLONE_REMOTE" --include "*${ts}*"; then
                log "✅ Tashqi joylashuvga yuklandi."
            else
                log "❌ Tashqi joylashuvga yuklash MUVAFFAQIYATSIZ (lokal nusxa baribir saqlandi)."
            fi
        else
            log "⚠️  RCLONE_REMOTE o'rnatilgan, lekin rclone topilmadi — Dockerfile'ni tekshiring."
        fi
    fi

    log "Zaxira nusxalash yakunlandi."
}

# Konteyner ishga tushgani zahoti — birinchi zaxirani darhol olamiz
# (keyingi safar belgilangan vaqtgacha kutmasdan).
run_backup

while true; do
    now_epoch=$(date +%s)
    next_epoch=$(date -d "today ${BACKUP_HOUR}:${BACKUP_MINUTE}:00" +%s)
    if [ "$next_epoch" -le "$now_epoch" ]; then
        next_epoch=$(date -d "tomorrow ${BACKUP_HOUR}:${BACKUP_MINUTE}:00" +%s)
    fi
    sleep_seconds=$((next_epoch - now_epoch))

    log "Keyingi zaxira: $(date -d "@$next_epoch" '+%F %T') ($((sleep_seconds / 3600)) soat $(((sleep_seconds % 3600) / 60)) daqiqadan keyin)."
    sleep "$sleep_seconds"
    run_backup
done
