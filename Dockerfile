FROM openjdk:8-jre-slim
# We use 'jq' in 'startup.sh'
RUN apt-get update && apt-get install -y jq
EXPOSE 8080
RUN mkdir /app
COPY DataMigration/build/libs/DataMigration.jar /app/DataMigration.jar
ENTRYPOINT ["./startup.sh"]
