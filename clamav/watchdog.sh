#!/bin/sh
# ClamAV konteyneri uchun "o'zini tuzatuvchi" ishga tushirish skripti.
#
# MUAMMO (2026-10-03): virus bazasi har kuni yangilanganda clamd qayta
# yuklanadi va 1,9 GB gacha xotira oladi; 3,8 GB li serverda tizim uni OOM
# bilan o'ldirdi. Konteyner ishlashda qolaverdi ("unhealthy"), lekin clamd
# yo'q edi — fayl yuklash "Virus skaneri hozircha mavjud emas" xatosini berdi
# va faqat qo'lda `docker restart clamav` yordam berdi.
#
# YECHIM: standart /init'ni ishga tushiramiz, so'ng clamd javob bermay
# qolsa, shu konteyner ICHIDA uni o'zimiz qayta ko'taramiz — Docker
# boshqaruviga hech qanday ruxsat kerak emas.
# (Bazani yangilashda xotira ikki baravar oshmasligi uchun docker-compose.prod.yml'da
# CLAMD_CONF_ConcurrentDatabaseReload=no ham o'rnatilgan.)

/bin/sh /init &
INIT_PID=$!

# Birinchi ishga tushishda baza yuklanishi uchun kutamiz.
sleep 180

FAILS=0
while kill -0 "$INIT_PID" 2>/dev/null; do
    sleep 30
    if clamdcheck.sh >/dev/null 2>&1; then
        FAILS=0
    else
        FAILS=$((FAILS + 1))
    fi

    # 6 ta ketma-ket muvaffaqiyatsiz tekshiruv (~3 daqiqa) — baza yangilanishi
    # paytidagi qisqa to'xtashlarni "o'lgan" deb adashmaslik uchun.
    if [ "$FAILS" -ge 6 ]; then
        echo "[watchdog] clamd javob bermayapti — qayta ishga tushirilmoqda"
        pkill -9 -x clamd 2>/dev/null || true
        rm -f /tmp/clamd.sock /run/clamav/clamd.sock
        clamd --foreground &
        # Yangi clamd bazani yuklashi uchun qo'shimcha kutish (~4 daqiqa).
        FAILS=-8
    fi
done

exit 1
