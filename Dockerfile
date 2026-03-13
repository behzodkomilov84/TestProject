# Используем легкий OpenJDK образ
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Копируем собранный fat jar
COPY target/TestProject-0.0.1-SNAPSHOT.jar app.jar

# Открываем порт приложения
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java","-jar","/app/app.jar"]