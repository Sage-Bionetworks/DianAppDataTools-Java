FROM openjdk:8-jre-slim
EXPOSE 8080
RUN mkdir /app
COPY DataMigration/build/libs/DataMigration.jar /app/DataMigration.jar
ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/DataMigration.jar"]
