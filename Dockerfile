FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/swcnoops-1.0-SNAPSHOT.jar ./swcnoops.jar
COPY --from=build /app/target/lib ./lib
COPY swc.properties.example ./swc.properties
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "swcnoops.jar", "swc.properties"]
