# digitalizator-android

Android appka pre [digitalizator](https://github.com/Rudoslav/digitalizator): telefón na stojane slúži ako kamera pre počítač.

Appka beží ako malý HTTP server (port 8765) a počítač sa k nej pripája cez USB (`adb forward`):
- `GET /status` → `ok`
- `GET /preview` → živý náhľad (MJPEG, nízke rozlíšenie)
- `GET /capture` → jedna fotka v plnom rozlíšení (JPEG, CameraX, najvyššia kvalita)

## Inštalácia
1. Stiahni `digitalizator-camera-*.apk` z [Releases](https://github.com/Rudoslav/digitalizator-android/releases) a nainštaluj ho
   (na Xiaomi: Možnosti vývojára → zapni „Inštalácia cez USB", alebo APK otvor v telefóne a povoľ neznáme zdroje).
2. Zapni USB ladenie a pripoj telefón k počítaču USB káblom.
3. Pri prvom spustení povoľ kameru. Desktopová appka ju potom spúšťa sama.

Pozor: ak máš nainštalovaný starší ladiaci (debug) build, najprv ho odinštaluj, release APK je podpísaný iným kľúčom.

## Build
Java 21 a Android SDK (platforma 36, build-tools 36.1.0):
```
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug     # ladiaci build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleRelease   # podpísaný, vyžaduje kľúč
```
Release sa podpisuje kľúčom z `~/.config/digitalizator/android-keystore.properties` (mimo repa; položky `storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
Bez neho vznikne nepodpísaný APK.

## Licencia
MIT (c) 2026 Rudoslav. Pri použití alebo úprave treba zachovať toto autorstvo a text licencie. Používa AndroidX CameraX (Apache-2.0).
