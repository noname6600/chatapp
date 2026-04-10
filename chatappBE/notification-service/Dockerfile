FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :notification-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/notification-service/build/libs/*.jar app.jar

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
