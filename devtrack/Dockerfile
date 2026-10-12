# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline        # cache deps in own layer -> faster rebuilds
COPY src ./src
RUN mvn -q -B clean package -DskipTests    # produces target/devtrack.jar

# ---------- Stage 2: tiny runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home appuser # do not run as root
USER appuser
COPY --from=build /app/target/devtrack.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
