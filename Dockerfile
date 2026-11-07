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
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 app \
    && mkdir -p /app/data \
    && chown -R app:app /app

COPY --from=build /app/app.jar /app/app.jar

USER app
EXPOSE 8080

ENV TZ=Europe/Minsk \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
