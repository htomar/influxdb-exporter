FROM gradle:6.7-jdk11 AS build
RUN mkdir /application
COPY . /application
WORKDIR /application
RUN gradle build --no-daemon

FROM openjdk:11-jre-slim
COPY --from=build /application/build/libs/*-fat-*.jar /app/exporter.jar
ENTRYPOINT ["java", "-jar", "/app/exporter.jar"]
