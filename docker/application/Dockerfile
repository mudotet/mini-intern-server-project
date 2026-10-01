FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
COPY build/install/mini-game-server/ /app/
USER 10001:10001
ENV JAVA_OPTS="-Xms64m -Xmx256m"
EXPOSE 8080
ENTRYPOINT ["/app/bin/mini-game-server"]
