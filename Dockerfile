FROM openjdk:17-oracle

VOLUME /tmp

EXPOSE 8080

ARG JAR_FILE=target/scala-3.1.3/homework3-assembly-0.1.0-SNAPSHOT.jar

ADD ${JAR_FILE} Cloudsim.jar

ENTRYPOINT ["java","-jar","/Cloudsim.jar"]