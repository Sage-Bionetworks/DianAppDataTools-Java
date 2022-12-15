#!/usr/bin/bash

# unpack SCHEDULED_JOB_SECRETS JSON from AWS secrets manager into invidivual environment variables
export `echo $SCHEDULED_JOB_SECRETS  | jq -r 'to_entries[] | [.key,.value] | join("=")'`

java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Djava.security.egd=file:/dev/./urandom -jar /app/DataMigration.jar
