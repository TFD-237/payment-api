# ─────────────────────────────────────────────────────────────
# Dockerfile — ICT304 Payment API
# Multi-stage build : compile puis image finale légère
# ─────────────────────────────────────────────────────────────

# ── Étape 1 : Build avec Maven (image temporaire) ────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copier le pom.xml en premier → le cache Maven n'est invalidé
# que si les dépendances changent (optimisation CI/CD)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copier le code source et compiler
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Étape 2 : Image finale légère ────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Sécurité : ne pas tourner en root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copier uniquement le JAR compilé
COPY --from=build /app/target/*.jar app.jar

# Port exposé (Railway injecte $PORT automatiquement)
EXPOSE 8080

# Health check pour Railway/Render
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Démarrage avec profil prod + gestion mémoire JVM optimisée pour containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-jar", "app.jar"]
