# VDO Dashboard Android

Android-app die een klassiek VDO-instrumentenpaneel nabootst, met live data van GPS en een ESP32.

## Wat erin zit

- **Snelheidsmeter** (Ø125mm, 0–200 km/h) — niet-lineaire schaal zoals het origineel (schaal begint zichtbaar bij 20, geen "0"), gevoed door de **GPS-snelheid** van het toestel zelf
- **Mechanische kilometerteller**, ingebouwd in de snelheidsmeter — telt écht mee op basis van de afgelegde GPS-afstand, begint bij 0 en onthoudt de stand tussen herstarts (`SharedPreferences`)
- **Analoge klok** (Ø75mm) — loopt live mee op de systeemtijd van het toestel, met 12/3/6/9 als cijfers, VDO- en Kienzle-merknamen
- **Combinatiemeter** (Ø125mm) — gebaseerd op een echt origineel exemplaar:
  - Toerenteller ingebouwd in het middenveld met de chrome knop
  - TANK- en TEMP-meter als gebogen schaalstrips met kleurverloop
  - 5 vierkante controlelampjes: FERNLICHT, TANK-leeg, BLINKER, OEL, LADUNG
- Chrome-bezels en donkere, VDO-geïnspireerde wijzerplaten op alle meters
- Systeembalken verborgen (immersive mode), tijdelijk zichtbaar bij aanraken

## Databronnen

| Gegeven | Bron |
|---|---|
| Snelheid | GPS (`LocationManager`, rechtstreeks op het toestel) |
| Kilometerstand | Berekend uit GPS-afstand, lokaal opgeslagen |
| Toerental, brandstof, temperatuur, 4 controlelampjes | ESP32 via Bluetooth (Classic SPP) |

Zie `firmware/ESP32_installatiegids.md` voor het aansluiten en programmeren van de ESP32, en `firmware/esp32_vdo_dashboard.ino` voor de firmware zelf. Het communicatieprotocol tussen ESP32 en app:

```
kph,rpm,fuel,temp,blinker,oel,ladung,fernlicht
```

(`kph` wordt door de app genegeerd — die komt uit GPS.)

## Projectstructuur

```
Vdoverdie2/
├── app/
│   └── src/main/
│       ├── java/com/example/vdodashboard/
│       │   ├── MainActivity.kt       ← permissies, GPS, Bluetooth, odometer
│       │   └── VDODashboard.kt       ← alle tekenlogica van de meters
│       ├── res/values/styles.xml
│       └── AndroidManifest.xml
├── firmware/
│   ├── esp32_vdo_dashboard.ino
│   └── ESP32_installatiegids.md
├── build.gradle.kts
└── settings.gradle.kts
```

## Benodigde permissies

- **Locatie** (`ACCESS_FINE_LOCATION`) — voor de snelheidsmeter en kilometerteller
- **Bluetooth** (`BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` op Android 12+) — voor de verbinding met de ESP32

Beide worden bij het opstarten van de app aangevraagd.

## Bouwen

Standaard Gradle-build: `./gradlew assembleDebug`, of via de GitHub Actions-workflow in dit repo.

## Status / bekende beperkingen

- De kilometerteller telt alleen mee zolang de app open staat (geen achtergrondservice)
- De ESP32-firmware bevat plaatshouders voor de tank- en temperatuurkalibratie (`readFuelLevel()`, `readTemperature()`) — die moeten afgestemd worden op de daadwerkelijk gebruikte sensoren
- De pin-toewijzing voor de 4 controlelampjes (blinker/oel/ladung/fernlicht) gaat uit van schakelaars die naar massa sluiten (`INPUT_PULLUP`, actief-laag) — controleer dit voor je eigen bedrading
