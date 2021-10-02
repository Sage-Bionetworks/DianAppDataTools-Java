FROM java:8
WORKDIR /
ADD DataMigration/build/libs/DataMigration.jar DataMigration.jar
EXPOSE 8080
CMD java - jar DataMigration.jar user
