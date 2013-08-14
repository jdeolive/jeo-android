# jeo-android

Android specific utilities for [jeo](https://github.com/jdeolive/jeo/).

## Building

To build the android module run maven:

    mvn install

## Android Dependencies

The android module depends the following libraries:

* `com.google.android:android:4.0.1.2` - The core Android APIs
* `com.google.android:google-play-services:r7` - Google Play services 
extensions to the Android core such as the Google Maps API

These libraries are only compile time dependencies. At runtime the actual 
libraries distributed with the platform will be used. 

The version of these libraries may be specified at build time with the 
system properties `android.version` and `googleplay.version` respectively. For
example:

    mvn -Dandroid.version=4.1.1.4 -Dgoogleplay.version=r10 install

The google-play-services library is typically not available in any of the 
standard maven repositories so to use a version other than r7 it must first be
installed manually in the local maven repository. The easiest way to obtain 
the jar is through the Android SDK Manager under the "Google Play Services" 
option from the "Extras" category. Once installed you can install it into the 
local maven repository with the following command:

    mvn install:install-file -DgroupId=com.google.android -DartifactId=google-play-services -Dversion=<VERSION> -Dpackaging=jar -DgeneratePom=true -Dfile=<SDK_DIR>/extras/google/google_play_services/libproject/google-play-services_lib/libs/google-play-services.jar

Where `<VERSION>` is the appropriate version number and `<SDK_DIR>` is the 
location of the Android SDK on the system.
