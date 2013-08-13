# jeo-android

Android specific utilities for https://github.com/jdeolive/jeo/

## Preparing for Building

jeo-android requires google-play-services.jar from the [Android SDK](http://developer.android.com/sdk/index.html). To get this file, open the
Android SDK Manager and install "Google Play Services" from the "Extras" category. When done, the file will be available under `sdk/extras/google/google_play_services/libproject/google-play-services_lib/libs/google-play-services.jar` from the root of your Android SDK installation (adt-bundle).

To register this file with Maven, execute the following command:

    mvn install:install-file -DgroupId=com.google -DartifactId=google-play-services -Dversion=2.0.12 -Dpackaging=jar -DgeneratePom=true -Dfile=sdk/extras/google/google_play_services/libproject/google-play-services_lib/libs/google-play-services.jar

The `-Dversion` value may be different, but the correct version will be indicated as part of the error message you get for the missing dependency when trying to build jeo-android.

## Building

To build jeo-android, simply run

    mvn install