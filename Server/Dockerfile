FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY src/ src/
COPY lib/ lib/

RUN mkdir -p out && \
    find src -name "*.java" > sources.txt && \
    javac -cp "lib/*" -d out @sources.txt

EXPOSE 3050
ENV PORT=3050

CMD ["java", "-cp", "out:lib/*", "server.AppServer"]