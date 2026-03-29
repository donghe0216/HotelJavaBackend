# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies separately so Docker layer cache is reused when only
# source files change (pom.xml unchanged → this layer is cached).
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security best practice
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/HotelBooking-0.0.1-SNAPSHOT.jar app.jar

# NOTE: ECS task definition currently maps containerPort=3000.
#       Spring Boot is configured to server.port=9090.
#       Until ecs.tf is updated to containerPort=9090 (and ALB target group
#       port=9090), you must pass --server.port=3000 at runtime OR fix the TF.
EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
