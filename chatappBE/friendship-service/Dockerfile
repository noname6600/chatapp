FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :friendship-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0"

COPY --from=build /workspace/friendship-service/build/libs/*.jar app.jar

EXPOSE 8085
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
