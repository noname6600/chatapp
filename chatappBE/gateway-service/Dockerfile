FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY gateway-service ./gateway-service
RUN chmod +x ./gradlew
RUN ./gradlew -p gateway-service bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/gateway-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
