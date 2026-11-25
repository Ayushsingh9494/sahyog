# The server image for Cloud Run. The jar is built first with `./gradlew bootJar`
# (deploy/deploy.sh does it), so the image only needs a Java runtime.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/sahyog-server.jar app.jar
# Never run as root inside the container.
RUN useradd --system --uid 10001 sahyog
USER sahyog
ENV SPRING_PROFILES_ACTIVE=prod
# Use the container's memory limit sensibly; start fast on Cloud Run's scale-from-zero.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
