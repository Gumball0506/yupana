# ---------- 1) Construcción: Maven + JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Primero solo el pom: las dependencias quedan en caché mientras no cambie
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# ---------- 2) Ejecución: JRE 21 mínima, usuario sin privilegios ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S yupana && adduser -S yupana -G yupana
WORKDIR /app
COPY --from=build /build/target/yupana-h6-*.jar app.jar
USER yupana
ENV SPRING_PROFILES_ACTIVE=produccion \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8770
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8770/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
