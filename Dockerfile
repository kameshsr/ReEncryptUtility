FROM openjdk:11
LABEL maintainer="kamesh"
ADD target/ReEncryptUtility-0.0.1-SNAPSHOT.jar re-encrypt-utility-docker.jar
ENTRYPOINT ["java", "-jar", "re-encrypt-utility-docker.jar"]