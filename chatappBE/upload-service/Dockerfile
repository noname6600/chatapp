FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :upload-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/upload-service/build/libs/*.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
