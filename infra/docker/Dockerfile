# syntax=docker/dockerfile:1
# App image (spec 7.1): Gradle build stage, then a JRE runtime with the layered Spring Boot jar.
# Build context: the repository root (see docker-compose.yml).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :app:bootJar --no-daemon --console=plain
RUN java -Djarmode=tools -jar app/build/libs/ultimate-analytics.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
# Fixed ids: the Helm chart runs the container as this user (runAsUser / fsGroup 999).
RUN groupadd --system --gid 999 app \
    && useradd --system --uid 999 --gid app --no-create-home app \
    && mkdir -p /data/raw \
    && chown app:app /data/raw
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "ultimate-analytics.jar"]
