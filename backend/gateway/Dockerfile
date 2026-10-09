# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 1001 -m -d /app app
WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

ENV TZ=America/Bahia \
    JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Dfile.encoding=UTF-8"

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -sf http://localhost:8090/monitoring/services -o /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
