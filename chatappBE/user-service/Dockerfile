FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :user-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/user-service/build/libs/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
