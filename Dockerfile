FROM eclipse-temurin:25-jdk-alpine@sha256:425f0b733cb23843bd78dbe5552513976d0cf12b9722752c48a5161cf5cd2f32 AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :argent-gateway:installDist --no-daemon

FROM eclipse-temurin:25-jre-alpine@sha256:56f273059de6b13a6b49fe0ded691ed2d0399bbee76156a8f2d385f7524a3d43
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/argent-gateway/build/install/argent-gateway /app
ENV ARGENT_BIND_HOST=0.0.0.0
ENV HOME=/tmp
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/app/bin/argent-gateway"]
