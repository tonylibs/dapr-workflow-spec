# syntax=docker/dockerfile:1

# ---- build stage: JDK 25 + Maven wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Prime the dependency cache first so source-only changes reuse cached layers.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests clean package

# ---- runtime stage: JRE 25 only ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/target/dws-orchestrator.jar /app/app.jar
USER app

ENV DWS_DEFINITIONS_PATH=/etc/dws/definitions
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
