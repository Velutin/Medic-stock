# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# The cache mount keeps ~/.m2 between builds: the first build downloads everything,
# the following ones only compile.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-jammy

# fontconfig and a font are NOT optional here: Apache POI's autoSizeColumn
# (BillingService and ReportService) measures the text with AWT. Without a font
# installed, every .xlsx export fails on the server with
# "java.lang.NullPointerException: Fontconfig head is null" while working on Windows.
# curl is used by the healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 1001 -m -d /app app
WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar
# Report logo. Replacing branding/logo.png and restarting the container is enough.
COPY branding ./branding

# storage/ holds the surgery consumption sheets; it is a volume in docker-compose.
RUN mkdir -p /app/storage && chown -R app:app /app
USER app

ENV TZ=America/Bahia \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true -Dfile.encoding=UTF-8"

EXPOSE 8085

# The healthcheck used to call /v3/api-docs, which now answers 404 with the documentation off —
# the container would restart in a loop forever. /auth/session answers 401 without a cookie, and a
# 401 is proof enough that Tomcat, Spring Security and the filter chain are up. There is no -f on
# purpose: with it, curl would treat the 401 as a failure.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -s http://localhost:8085/auth/session -o /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
