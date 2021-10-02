# DianUserMigration-Java
Code for mapping user data from periodic Happy Medium export to Synapse, into the DIAN bridge project.

# Project setup:

Before you can run the code, your environment must have the following environmental variables set first:

## Required
**BR_EMAIL** - Your email to sign into the Arc Validation or DIAN bridge project
**BR_PW** - Your password to sign into the Arc Validation or DIAN bridge project
**BR_ID** - The bridge project ID for either the Arc Validation or DIAN bridge project

**SYN_PAT** - Your Synapse access token to your account that has read/download access for the dian uat or dian prod Synapse projects.
**SYN_PROJ_ID** - The Synapse Project ID for dian uat or dian prod.

## Optional
**BR_USER_DATA_GROUP** - If you want to tag all users created in the UserMigration code with a data group, set this to String like "test_user"

# Building the code

This project was created and maintained using Android Studio.  I imagine it could be imported into Ecliipse as well, but I have not tested it.

To load the pojrect, select "import existing gradle project" in Android Studio.

To run, right click `DataMigration.java`, and click Run. 
This will create the configuration in Android Studio, but the program will throw an exception, because you need to provide an argument to the program of either `user` to run the user migration or `data` to run the data migration.

To add program arguments, tap on the "DataMigration" configuration at the top of Android Studio and click "Edit Configurations".  On this screen you can enter `user` or `data` in the text field labeled "program arguments".

You can also build the JAR using gradle with the following commands:

`./gradlew jar` 
This will build DianUserMigration-Java/DataMigration/build/libs/DataMigration.jar

You can then run the user migration from the command line with this command:
`java -jar DianUserMigration-Java/DataMigration/build/libs/DataMigration.jar user`

You can run the data migration from the command line with this command:
`java -jar DianUserMigration-Java/DataMigration/build/libs/DataMigration.jar data`
 
# User Migration Background Info:
To fully understand the user migration process, there are a few terms that need described.

Arc ID: This is a 6 digit unique identifier used to keep track of a participant.  In Sage Bridge terms, this is a user’s External ID.  It’s important to note that even when a user has to do 2FA with their phone number, they still must be assigned an Arc ID.  
Rater ID: A rater is an individual who is responsible and accountable for setting up participants with the app.  A rater is important to the audit trail process so it is known who is responsible for each user.  When HM creates a rater on their dashboard, the server would generate a 6 digit rater id and then email that as plain text to the rater’s email.  This Rater ID is used as the authentication password for both Map Arc (HASD) and DIAN OBS (EXR). 
Site Location: This is a physical location, usually an office or a university, where the app is being administered from.  Each site location must have at least one rater, but some locations have multiple raters.  Unfortunately, the email and contact for the site locations are invalid on HM’s server and all list Sarah S as the contact.

# HM’s user creation process

User creation on HM’s dashboard is a multi-step process. First, a study coordinator, usually Sarah S, will have Sarah A use an R script to generate the next, say 10, unique Arc IDs that are ready to be consumed.  Sarah S would then have HM mark those 10 Arc IDs as reserved, and assign them to a particular site location. An email is sent to that site location that they can now use those 10 ARC IDs.  
At this point, these accounts are not yet created, but more like “reserved”.  The site location chooses which rater will be in charge of setting up participants with these Arc IDs.  It is not until the participant receives their Arc ID and rater ID from the rater, and then enters that into the mobile app, does an account officially get created on HM’s server.  
 
 # Security Issues
There is one security issue we found with the HM user creation process, and that is that when a rater is created on HM’s server, their rater ID, is sent to their email in plain text.  The Rater ID is used as a password to authenticate users, and should not be communicated in this way.  The current security standard for sending a secret like this is using TLS, and the secret should not exist indefinitely, like through email, but only for a short amount of time for it to be consumed by the rater and written down or stored somewhere safe.

# App Authentication:
 
 ### For Map Arc (HASD) a user authenticates using:
 **external_id** = Arc ID (######)
 **password**   = Rater ID (######)
For Map Arc, all users essentially belong to one rater, which is Marisol at Wash U.  Only she knows her Rater ID which she communicates to users when they first sign in with their Arc ID / Rater ID.
 
 ### For DIAN OBS (EXR) a user authenticates using:
 **external_id** = Arc ID (######)
 **password**  = Rater ID (######)
The DIAN observational clinical trial study contains national and international site locations and raters.  There are only about 18 users currently participating in the DIAN OBS trial; however, many more are planned to ramp up soon and the ARC IDs have already been reserved for use by site locations and the raters have already been given their Rater IDs.
 
 ### For DIAN (EXR) a user authenticates using:
Phone number SMS verification code
ARC ID they enter must match their profiles ARC ID
 
# Account migration to Sage Bridge Server
Before we can migrate the account data from HM over to Sage Bridge, we need to create each account so we have a place to store the data.  
HM exports all the participant data needed to do this process.  The staging QA data is available on Synapse Synapse | Sage Bionetworks .  The two relevant files are:
hasd_staging_participant_json.zip
exr_stating_participant_json.zip
These contain 5-6 files which relate Arc IDs, phone numbers, site locations, and raters.  The migration script will download these files from Synapse, parse the JSON, and build these data models for each app:

### Map Arc and DIAN Obs:
`
1{ 
2   "ArcId": 6 digit string
3   "SiteName": String  // the site location name
4   "RaterName": String   // the rater's name
5   "RaterEmail": String  // the rater's email
6   "RaterPhone": String  // the rater's phone number
7}
`

### DIAN:
`
1{ 
2   "ArcId": 6 digit string
3   "PhoneNum": String  // *Note these are international format and always start with “+”
4   "SiteName": String  // the site location name
5   "RaterName": String   // the rater's name
6   "RaterEmail": String  // the rater's email
7   "RaterPhone": String  // the rater's phone number
8}
`
 
Using these details, we can create each user on Bridge, and use Bridge’s user attributes to store all the supplemental information.  
 
When creating each account, each user should only belong to the particular sub-study.  This can be done with Bridge 2.0’s substudy setup, or by using data groups.  For now, we will use data groups to be able to easily separate user’s data in Synapse.
 
Note: The major open issue is how to communicate the new Rater ID password to the Rater in a secure fashion. 
 
# Data Migration Background Info:
After all the HM users have been created on Sage Bridge, we can populate the accounts with the data needed to load their app state.
Daily, early in the morning, HM will export the day’s changes to availability schedules, testing schedules, and completed test sessions.  An example can be seen here on Synapse Synapse | Sage Bionetworks  within folders 2021-07-08 and 2021-09-21.
The migration script must go through all the day’s folders and find the most recent availability and testing schedule JSON files, and also compute a list of completed test sessions for each user.
All three of these should be stored as JSON data on the user’s account as a singleton Bridge user report.  A singleton report is a report on bridge that has a date of January 1st, 1970.

### Availability Migration
The availability schedule defines when a user is available to do their testing. It is a JSON model, and while there can be multiple per user.  We only need to read the most recent.

### Test Schedule Migration
The test session schedule defines when a user is available to do their testing. It is a JSON model, and while there can be multiple per user.  We only need to read the most recent.

### Completed Tests Migration
On HM’s server, earnings was calculated using a rest API call by checking which test sessions were completed by the user for each testing cycle. 
We do not have that service on Bridge, and so that calculation needs to happen locally in the app.  For the calculation to work, we need a list of which test session a user has completed with the week, day, and session indexes.  
By parsing all test sessions for a user, a list of completed tests can be calculated, with the completed test data model looking like this:

`
1{
2  “week”: Int,  // the week in the study, cycle 1 is week 0, and cycle 2, is week 25
3  “day”: Int,  // the day of the week 0-7
4  “session”: Int, // the session index in the day, 0-3
5  “completedOn”: Double // time interval since 1970 in seconds
6}
`



