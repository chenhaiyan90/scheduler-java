FROM openjdk:8u212-jre-alpine3.9

COPY target/scheduler-java-1.0-SNAPSHOT.jar ./
WORKDIR ./
ARG port=10600
ENV SERVICE_PORT=${port}
EXPOSE ${port}
#RUN "PWD"
ENTRYPOINT ["java", "-jar", "scheduler-java-1.0-SNAPSHOT.jar"]