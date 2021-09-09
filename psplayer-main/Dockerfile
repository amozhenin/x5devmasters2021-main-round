FROM openjdk:11

ENV rs.endpoint=http://localhost:9080

RUN mkdir /app
WORKDIR /app

COPY target/psplayer.jar /app/psplayer.jar
EXPOSE 9081

ENTRYPOINT ["java", "-jar", "/app/psplayer.jar"]
