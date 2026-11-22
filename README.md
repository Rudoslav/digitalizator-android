# digitalizator-android

Android appka pre [digitalizator](https://github.com/Rudoslav/digitalizator): telefón na stojane slúži ako kamera.

MVP:
- náhľad z kamery ako MJPEG cez HTTP (`/preview`)
- odfotenie v plnom rozlíšení na požiadavku (`/capture` → JPEG)
- spojenie s PC cez USB (`adb forward`), bez Wi-Fi

Desktopová appka (náhľad, medzerník, ukladanie, OCR) je v repe `digitalizator`.
