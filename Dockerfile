FROM eclipse-temurin:21-jdk-alpine@sha256:b78a1d385da875588e21207f07dddf9a1418849971ae857fb24c8f3a7d441a72 AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :argent-gateway:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/argent-gateway/build/install/argent-gateway /app
ENV ARGENT_BIND_HOST=0.0.0.0
ENV HOME=/tmp
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/app/bin/argent-gateway"]
