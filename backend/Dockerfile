# syntax=docker/dockerfile:1
# Contexto de build: la raíz del repositorio (el build empaqueta rules/schema.json).
#   docker build -f backend/Dockerfile .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
COPY rules/schema.json /src/rules/schema.json
COPY backend/src/main ./src/main
# Las pruebas no corren aquí: necesitan Docker (Testcontainers). Se ejecutan con ./gradlew test.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /src/backend/build/install/vueltax-backend /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["/app/bin/vueltax-backend"]
