# Используем легкий OpenJDK образ
FROM eclipse-temurin:21-jdk-jammy

# libheif-examples — "heif-convert" buyrug'ini beradi, iPhone'ning HEIC/HEIF
# rasm formatini JPEG'ga o'girish uchun (FileStorageService.convertHeicToJpeg).
# Java'da HEIC dekoderi yo'q (HEVC kodek litsenziyasi sababli).
#
# libreoffice-impress — "soffice" buyrug'ini beradi, PPT/PPTX taqdimotni
# PDF'ga o'girish uchun (FileStorageService.convertPptToPdf) — kurs
# bo'limi matniga "🎞 PPT qo'shish" (slaydlar sifatida) funksiyasi uchun.
# poppler-utils — "pdftoppm" buyrug'ini beradi, shu PDF'ning har bir
# sahifasini alohida PNG rasmga (slaydga) ajratish uchun
# (FileStorageService.splitPdfIntoSlideImages).
# DIQQAT: bular ancha katta paketlar — image hajmini sezilarli oshiradi
# (~500MB-1GB), lekin PPTX slaydlarini HAQIQIY ko'rinishi bilan (shrift,
# chizma, joylashuv) faqat shunday "chizib" olish mumkin — client-side
# (brauzer) buning uchun ishonchli kutubxona yo'q (.docx'dagi mammoth.js
# kabi emas).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libheif-examples libreoffice-impress poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем собранный fat jar
COPY target/TestProject-0.0.1-SNAPSHOT.jar app.jar

# Открываем порт приложения
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java","-jar","/app/app.jar"]