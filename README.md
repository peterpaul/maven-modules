# Maven Dependencies

## Building a debian package

Before being able to build a debian image, you need to run:

    sudo apt install debhelper javahelper

Then

    ./gradlew deb
