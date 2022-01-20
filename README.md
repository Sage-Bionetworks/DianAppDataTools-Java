# DianUserMigration-Java
This repository contains code for mapping user data from periodic Happy Medium (HM) exports to Synapse into a Sage Bridge project.

# Project setup

Before you can run the DataMigration class code, your environment must have the following environmental variables set first:

## Required
**BR_EMAIL** - Your email to sign into the Arc Validation or DIAN bridge project

**BR_PW** - Your password to sign into the Arc Validation or DIAN bridge project

**BR_ID** - The bridge project ID for either the Arc Validation or DIAN bridge project

**SYN_PAT** - Your Synapse access token to your account that has read/download access for the dian uat or dian prod Synapse projects.

**SYN_PROJ_ID** - The Synapse Project ID for dian uat or dian prod.

# Building and running the code

This project was created and maintained using Android Studio.  I imagine it could be imported into Eclipse as well, but I have not tested that setup.

To load the project, select "import existing gradle project" in Android Studio.

To run the migration code, right click `DataMigration.java`, and click Run.

You can also build the JAR using gradle with the following commands:

`./gradlew jar`
This will build DianUserMigration-Java/DataMigration/build/libs/DataMigration.jar

# Docker image
To create a docker image based on the migration code in this repository, you need to push to a branch within the branch folder **release** with a unique branch name.  The branch name will be used as the docker image name tag and should be distinguishable from previous packages.

For instance, if you create the branch **release/test1**, it will show up under Sage's packages as:
```
ghcr.io/sage-bionetworks/diandatamigration:release-test1
```

The github action **docker_release.yml** handles creating and deploying the docker image to Sage's github packages only on a push to a release/* branch.

To run the docker image in the example above, you need to run the following commands:

```
docker login ghcr.io -u $your_git_username -p $your_git_personal_access_token
docker pull ghcr.io/sage-bionetworks/diandatamigration:release-test1
docker run -e BR_ID=$BR_ID -e BR_EMAIL=$BR_EMAIL -e BR_PW=$BR_PW -e SYN_PROJ_ID=$SYN_PROJ_ID -e SYN_PAT=$SYN_PAT ghcr.io/sage-bionetworks/diandatamigration:release-test1
```

# Building and running the Tools
There are some tools in this project that were created to assist project managers in setting up the studies, making changes to existing schedules, and monitoring participant adherence.  These are tasks needed before we migrate to Bridge 2.0 Researcher UI dashboard.

To run a tool, right click the class in Android Studio and click "Run".

To create a JAR of a particular tool to send out, change the app's build.graadle line 
from:
```
attributes("Main-Class": "org.sagebionetworks.dian.datamigration.DataMigration")
```
to in the case of the add participant tool:
```
attributes("Main-Class": "org.sagebionetworks.dian.datamigration.tools.newparticipants.AddParticipantTool")
```
and then run:
`./gradlew jar`

Tools:
1) AdherenceTool - This tool accepts an Arc ID as input, and outputs the number of tests completed per test cycle for that participant.  It also prints out the raw JSON of the CompletedTests report clientData.
2) AddParticipantTool - This tool can add new participants to a study in the format needed to run on a DIAN ARC app.
3) ManuallyMigrationTool - This tool manually migrates a participant, in the case of them deleting their app before they migrated using their HappyMedium DeviceID credential.

# Troubleshooting Tool Errors
While running the JARS below, if you receive these error codes, this is most likely what they mean...

401 - There is most likely a problem with the bridge email and password you provided.  Make sure it is not a Synapse account, it needs to be a bridge account only, by creating a new user THROUGH the bridge API with email and password and NO synapse account linked.

404 - The Arc ID or Device ID you provided is not correct, as this error designates the External ID could not be found in the Bridge project

# AddParticipantTool

This JAR can only be run with the proper arguments.  You must provide a researcher level bridge email, password, the bridge project ID, and the number of new ARC IDs to generate.

From the command line, the commands will generate 5 new ARC IDs:
cd path_to_jar
java -jar NewArcIDs.jar a@b.com password dian_validation 5 random

This will output something like this:
845399    E&mjbn1vJ
506358    iL1VU.YVS
349888    Rd.7c2hVN
643738    mSGz4Z!Hc
415060    &LJB4cvB4

At this point, the program will ask you if you "Would you like to automatically create these on Bridge? (y/n)"
Type "y" and hit enter, if you want the program to do it for you.

At this point, the program will ask you to "enter the study-id to add them to:" and you must provide a valid study-id from the bridge project for the program to complete. 

When successful, the output will look something like this:
Successfully created 845399
Successfully created 506358
Successfully created 349888
Successfully created 643738
Successfully created 415060

It is a good idea to verify on Bridge Study Manager that the ARC IDs are created and the VERIFICATION_CODE is whats expected.

If you want to create participants that are test_users, you can provide an additional parameter to the request like this:
java -jar NewArcIDs.jar a@b.com password dian_validation 5 random test_user

# Manual Account Migration

This JAR can only be run with the proper arguments.  You must provide a researcher level bridge email, password, the bridge project ID, and the Device ID of the user.

First, you must go to Bridge and search for the ARC_ID user profile attribute for the ARC ID you want to manually migrate.  That will return a external ID account that is the user's Device ID.  If there are multiple results that look like Device ID, use the one that was created most recently.

From the command line, this will manually migrate:
cd path_to_jar
java -jar NewArcIDs.jar a@b.com password dian_validation device_id

Successful output should look something like this:
Downloading availability report...
Downloading test schedule report...
Downloading completing test report...
Creating participant account on bridge 321657
Writing availability report
Writing test schedule report
Writing completed tests report
Setting Device ID account IS_MIGRATED set to true...
**User successfully migrated**

# AdherenceTool

This JAR can only be run with the proper arguments.  You must provide a researcher level bridge email, password, the bridge project ID, and the Device ID of the user.

From the command line, this will manually migrate:
cd path_to_jar
java -jar AdherenceTool.jar a@b.com password dian_validation

The program will ask you which Arc ID you want to check on.  Provide the ARC ID, and you will see the info for that participant.  

# User Migration Background Info
To fully understand the migration process, there are a few terms that need described.

**Arc ID:** This is a 6 digit unique identifier used to keep track of a participant.  In Sage Bridge terms, this is a user’s External ID.

**Rater ID:** A rater is an individual who is responsible and accountable for setting up participants with the app.  A rater is important to the audit trail process so it is known who is responsible for each user.  When HM creates a rater on their dashboard, the server would generate a 6 digit rater id and then email that as plain text to the rater’s email.  This Rater ID is used as the authentication password for both Map Arc (HASD) and DIAN OBS (EXR).

**Device ID:** This is a unique, randomly generated UUID of the format
XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX, where each "X" is a randomly generated alpha numeric character.  When an HM user would sign up, their phone creates and saves a new Device ID to the app's cache.  The Device ID is sent up to HM's server when the user signs up and is stored with their account. Because only each app installation and HM's server's know about their Device ID, this can be treated as a secure token in the data migration process.

**Site Location:** This is a physical location, usually an office or a university, where the app is being administered from.  Each site location must have at least one rater, but some locations have multiple raters.  Unfortunately, the email and contact for the site locations are invalid on HM’s server, as all of them list someone at WashU as their contact.

# HM’s user creation process

User creation on HM’s dashboard is a multi-step process. First, a study coordinator, usually someone at WashU, will use an R script to generate the next, say 10, unique Arc IDs that are ready to be consumed.  They would then have HM mark those 10 Arc IDs as reserved, and assign them to a particular site location. An email is sent to that site location that they can now use those 10 ARC IDs.
At this point, these accounts are not yet created, but more like “reserved”.  The site location chooses which rater will be in charge of setting up participants with these Arc IDs.  It is not until the participant receives their Arc ID and rater ID from the rater, and then enters that into the mobile app, does an account officially get created on HM’s server.

 # Security Issues
There are three security issues we found with the HM user creation process.
1. When a rater is created on HM’s server, their rater ID, is sent to as an email in plain text.  The Rater ID is used as a password to authenticate users, and should not be communicated in this way.  The current security standard for sending a secret like this is using TLS, and the secret should not exist indefinitely, like through email, but only for a short amount of time for it to be consumed by the rater and written down or stored in a secure credential store.
2. passwords are shared among app users, as many users would be entering in the same Rater ID as their password.
3. A 6 digit password only has 999,999 different password combinations, and is vulnerable to a brute force attack.

# Data Validation Issues
Account security is critical to being able to prove that the data generated by a user is their own, and at no point in time did another user, or ill-intentioned party,  submit test data on their behalf.  In the context of GCP, we must fix the three security issues outlined above if we are to validate the submitted user data.

# Security Issue Resolution
The solution to the security and data validation issues outlined above are reflected in the code written in this repository, supported by unit test coverage, and manual validation.  Each security issue outlined above will be fixed as such:
1. Passwords will no longer be sent through email. Each site location will be given their own sub-study on Bridge that only they have permission to view and where only their participants will be viewable.  A site participant's Arc ID will be their External ID, and their password will be stored as a user attribute.  User attributes are ecrypted at rest and during transit.
2. Each user will now have their own unique password. Passwords are not allowed to be shared among participants.
3. Instead of a 6 digit password, each passsword now must be a minimum of 9 characters, and contain at least one lowercase letter, one uppercase letter, one number, and one symbol.

# How will site location onboard users?
As outlined earlier, HM communicated Arc ID and Rater ID passwords through email.  We are no longer supporting that.  Each site location manager will need to create a Synapse account.  After they create a Synapse account, they will be given access to the DIAN bridge project, and only given permission to view their sub-study and its participants.  WashU will not be able to view any of the sub-studies, besides their own.

# Account Migration to Sage's Bridge Server
Before the migration can complete successfully, a sub-study must be created for each site location on Bridge.  Each user we create must belong to a sub-study.

Depending on the state of the user, there are three migration scenarios that can occur.
### Migrating existing users
A user is existing if they have an Arc ID, a site location, and a Device ID.
These users will not have their account created yet with their Arc ID as their External ID.  Instead, the migration code will create an External ID that is their Device ID. The password for this account will also be their Device ID, plus the String "Arc#" that enables it to meet Bridge's password requirements.

The migration code marks this user with the **test_user** data group, has their user attribute **IS_MIGRATED** set to **false**, and populates the account with that user's data.

When a user updates from HM's app to the version of the app that points to Bridge, they will go through an automated migration process.

This process includes authenticating on Bridge to the External ID that matches their Device ID and downloading their data.  At this point they mark the user attribute **IS_MIGRATED** to **true**.  Then, they create a new Bridge user with External ID set to their Arc ID, and create a unique secure password for their account.

When the migration code runs, it always checks the user attribute **IS_MIGRATED**, and if it has become marked as **true**, then it deletes all the data on the account.

To track the status of migration, one can search on Bridge for all existing user account types, you can search for all accounts with **test_user** data group, to see all existing user that needed to be migrated.  And to see which ones have already mgirated, search with that data group, as well as the user attribute **IS_MIGRATED** equal to **true**.

### Migrating Arc IDs without Device IDs
There are many Arc IDs that have been marked as belonging to a site location, but do not have a Device ID yet. This means that they have not been signed into by a real participant yet.

There is no data to migrate for this account, so it is safe to create a new, unused External ID for them at this time.

Their External ID will be their Arc ID, and the migration code will generate a unique secure password for their account, that it will store in the attribute **VERIFICATION_CODE**, so that site location study admins can assign a participant to that Arc ID at some future point.

These users, once authenticated in the app, will begin the app as if they are a new user who just signed up.

### Migrating Arc IDs without a site location
If we encounter a user without a valid site location, we have no where to place this user on Bridge server.

However, for tracking purposes, a sub-study has been created on Bridge with the identifier **Happy-Medium_Errors** where all users without a site location will be placed.

Ideally, this would never happen on the production server, but if it does, we will at least be able to discuss and verify what happened to these users with HM and WashU after the migration code has run successfully for the first time.

These users will be created the same way as the section above, if the user has no Device ID.

# Account parsing from HM's data export
HM exports all the participant data needed to do this process.  The staging QA data is available on Synapse.  The two relevant files are:
```
hasd_staging_participant_json.zip
exr_stating_participant_json.zip
```
These contain 7-8 files which relate Arc IDs, phone numbers, site locations, notes, device-id's and raters.  The one optional file is about phone numbers, all the others are required for the algorithm to complete successfully.  Here is an example of the file list, as reflected in the unit tests:

```
sage_qa-participant-9-21-21.json
sage_qa-participant_phone-9-21-21.json
sage_qa-participant_site_location-9-21-21.json
sage_qa-rater-9-21-21.json
sage_qa-site_location-9-21-21.json
sage_qa_staging-participant_device-10-11-21.json
sage_qa_staging-participant_note-10-11-21.json
sage_qa_staging_participant_rater-9-21-21.json
```

The migration code will download and unzip these files from Synapse, parse the JSON, and build the data models representing each individual user that exists on HM's servers.

# HM Data Model
### Wake Sleep Schedule
The availability schedule defines when a user is available to do their testing. It is a JSON model, and while there can be multiple per user, we only need to read the most recent.

### Test Schedule
The test session schedule defines when a user must do their testing. It is a JSON model, and while there can be multiple per user, we only need to read the most recent.

### Completed Test Sessions
On HM’s server, earnings was calculated using a rest API call by checking which test sessions were completed by the user for each testing cycle.
We do not have that service on Bridge, and so that calculation needs to happen locally in the app.  For the calculation to work, we need a list of which test session a user has completed with the week, day, and session indexes.
By parsing all test sessions for a user, a list of completed tests can be calculated, with the completed test data model looking like this

```
{
    “week”: Int,  // the week in the study, cycle 1 is week 0, and cycle 2, is week 25
    “day”: Int,  // the day of the week 0-7
    “session”: Int, // the session index in the day, 0-3
    “completedOn”: Double // time interval since 1970 in seconds
}
```

# Data parsing from HM's data export:
Daily, early in the morning, HM will export the day’s changes to availability schedules, testing schedules, and completed test sessions.

The folders will be named based on the date format "YYYY-MM-DD", for example, a folder will be named like this:

```
2021-07-08
```

Each of these folders will have the following folder children:

```
test_session
test_session_schedule
wake_sleep_schedule
```

Within each of these folders will be a ZIP file, containing the exported data of that type.  For example, the folder test_session, would have this ZIP in it:

```
test_sessions_2021-07-08.zip
```

The migration code downloads and unzips all of these folders on Synapse, parses the JSON, and builds the data models representing each individual user's data that exists on HM's servers.


