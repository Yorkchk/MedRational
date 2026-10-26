# MedRational API image. One image for every environment: the profile, secrets and Firebase key
# come in at run time, so the same image is tested in uat and promoted to prod.
#
#   docker build -t medrational .
#   docker run --rm -p 8080:8080 --env-file .env.uat -e SPRING_PROFILES_ACTIVE=uat \
#     -v ./firebase-service-account.json:/run/secrets/firebase.json:ro \
#     -e FIREBASE_CREDENTIALS=/run/secrets/firebase.json medrational
# In PowerShell, mount the key with a full path: -v "${PWD}/firebase-service-account.json:...".
# A ./ path there mounts an empty directory instead of the file.

# ---- Build: compile the jar. Tests run in CI, which has the Postgres they need ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build

# Dependencies first, so this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/MedRational-*.jar app.jar

# ---- Runtime: JRE + LibreOffice for Office -> PDF previews (Preview/OfficeConverter) ----
FROM eclipse-temurin:21-jre-noble

# procps: JODConverter uses ps to manage the LibreOffice process.
# Carlito/Caladea match Calibri/Cambria metrics, so previews keep the documents' layout.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice-writer-nogui \
        libreoffice-calc-nogui \
        libreoffice-impress-nogui \
        fonts-dejavu-core \
        fonts-liberation2 \
        fonts-crosextra-carlito \
        fonts-crosextra-caladea \
        procps \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# Workshop dates are zone-less LocalDateTime compared with the server clock, so the container
# must run in the zone admins enter dates in: Morocco. Morocco has been on permanent GMT since
# 2026-09-20, but Java 21.0.12 still bundles tzdata 2026b, which puts Africa/Casablanca at +01.
# UTC is the same time and doesn't depend on Java's zone rules. Override with -e TZ=... if needed.
# MaxRAMPercentage leaves room for LibreOffice next to the JVM.
ENV LIBREOFFICE_HOME=/usr/lib/libreoffice \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"

# Non-root, with a home directory for LibreOffice's user profile
RUN useradd --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
