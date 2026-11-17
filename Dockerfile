# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so code changes don't re-download the world
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/benefits-portal-aws.jar app.jar
# Folder for uploaded documents when DOCUMENTS_STORAGE=local (docker compose mounts a volume here)
RUN mkdir -p /app/uploads && chown app:app app.jar /app/uploads
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
