FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :argent-gateway:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/argent-gateway/build/install/argent-gateway /app
ENV ARGENT_BIND_HOST=0.0.0.0
ENV HOME=/tmp
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/app/bin/argent-gateway"]
