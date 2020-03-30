# Atomos Android Example

This is a very simple Android example project that can be loaded by Android Studio and/or built with `gradlew` from command line:

`gradlew build`

Before building the example a JAR file containing an Atomos index and set of bundles must be copied to the `app/libs` folder. For this example it is assumed you copy the executable JAR produced by the [Atomos Index example](../atomos.examples.index/README.md) into the `app/libs` folder.

The build will produce an APK andoid application at `app/build/outputs/apk/debug/app-debug.apk`.  This application can then be installed on an emulator or an android device. The resulting application is very simple. It has a single `Launch Atomos` button. When clicked it will launch Atomos which will automatically install and start all the bundles included in the Atomos index JAR that got copied to the `app/libs` folder. The display should indicate when Atomos has launched and give a status of all the bundles that got started with the application. If using the [Atomos Index example](../atomos.examples.index/README.md) then you can bring up the browser on Android and access the web console with the URL [http://localhost:8080/system/console/bundles](http://localhost:8080/system/console/bundles) with the id/password of admin/admin.

