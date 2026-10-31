# Stage 1: build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# Stage 2: run the jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/shelfline-backend.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
