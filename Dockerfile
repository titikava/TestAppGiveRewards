FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/wrapper/ gradle/wrapper/
COPY api-tests/build.gradle.kts api-tests/
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :bootJar

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN mkdir -p /var/log/rewards && chown 10001:10001 /var/log/rewards
COPY --from=build /workspace/build/libs/rewards.jar /app/rewards.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/rewards.jar"]
