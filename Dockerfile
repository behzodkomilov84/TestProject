# Используем легкий OpenJDK образ
FROM eclipse-temurin:21-jdk-jammy

# libheif-examples — "heif-convert" buyrug'ini beradi, iPhone'ning HEIC/HEIF
# rasm formatini JPEG'ga o'girish uchun (FileStorageService.convertHeicToJpeg).
# Java'da HEIC dekoderi yo'q (HEVC kodek litsenziyasi sababli).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libheif-examples \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем собранный fat jar
COPY target/TestProject-0.0.1-SNAPSHOT.jar app.jar

# Открываем порт приложения
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java","-jar","/app/app.jar"]