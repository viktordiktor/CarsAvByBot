# syntax=docker/dockerfile:1

# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper and dependency descriptors first, so dependency downloads are cached.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar \
    && find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app/app.jar \;

# ---------- runtime ----------
# The Playwright image ships Chromium, its system dependencies and a JDK,
# so the optional browser fallback works without installing anything at runtime.
FROM mcr.microsoft.com/playwright/java:v1.64.0-noble
WORKDIR /app

COPY --from=build /app/app.jar /app/app.jar
RUN chown -R pwuser:pwuser /app

USER pwuser
EXPOSE 8080

ENV TZ=Europe/Minsk \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    JAVA_TOOL_OPTIONS="-Xms48m -Xmx192m -XX:MaxMetaspaceSize=110m -XX:+UseSerialGC -Xss512k"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
