# ── Stage 1: Build ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN mvn package -B -DskipTests

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy fat JAR
COPY --from=build /app/target/cyber-algo-arena-1.0.0.jar app.jar

# Copy static assets and contest data
COPY public/ public/
COPY contest_data/ contest_data/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--web"]
