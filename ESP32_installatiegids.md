# ESP32 VDO Dashboard — installatiegids 

Deze gids legt uit hoe je de ESP32 aansluit, bedraadt, programmeert en koppelt aan de VDO Dashboard-app.

---

## 1. Wat je nodig hebt

- Een **klassieke ESP32 DevKit-board** (bijv. ESP32-WROOM-32). **Let op:** ESP32-C3, -S2 en -S3 werken niet — die hebben geen klassieke Bluetooth (SPP), alleen BLE.
- USB-kabel (micro-USB of USB-C, afhankelijk van je board)
- Arduino IDE op je computer
- **5x PC817-optocoupler** (tacho + 4 lampjes), met per stuk een 1kΩ-weerstand (LED-kant) en een 10kΩ-weerstand (uitgangskant, pull-up naar 3V3)
- Weerstanden voor twee meetdelers (tank- en temperatuursensor — zie hoofdstuk 3 voor de exacte waarden), plus twee 100nF-condensators als ruisfilter
- Een 12V-naar-5V buck-converter, als je de ESP32 permanent op het boordnet wilt aansluiten
- Breadboard en dupont-kabeltjes om te testen, voordat je alles vast soldeert

---

## 2. Firmware installeren

1. Open Arduino IDE.
2. Ga naar **Bestand → Voorkeuren** en voeg bij "Extra bordbeheerder-URL's" toe:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Ga naar **Extra → Board → Boardbeheerder**, zoek op "esp32" en installeer het pakket van Espressif.
4. Selecteer bij **Extra → Board** je specifieke ESP32-model.
5. Open `esp32_vdo_dashboard.ino`.
6. Sluit de ESP32 aan via USB, kies de juiste **poort** onder **Extra → Poort**.
7. Klik op **Upload**.
8. Open de **Seriële Monitor** (115200 baud) — je zou "Bluetooth SPP gestart, wachten op koppeling..." moeten zien.

---

## 3. Aansluitschema

Zie het bijgevoegde schema (`esp32_wiring_diagram.svg`). Samengevat:

| Functie | ESP32-pin | Type | Aansluiting |
|---|---|---|---|
| Toerental (rpm) | GPIO4 | Digitaal, interrupt | Via PC817-optocoupler vanaf de Motronic "Drehzahl"-uitgang |
| Tank-peilgever | GPIO34 | Analoog (ADC) | Via spanningsdeler naar 3V3 + GND |
| Temperatuursensor | GPIO35 | Analoog (ADC) | Via spanningsdeler naar 3V3 + GND |
| Blinker | GPIO25 | Digitaal, interrupt-vrij | Via PC817-optocoupler vanaf de blinkerdraad |
| Oliedruk | GPIO26 | Digitaal, interrupt-vrij | Via PC817-optocoupler vanaf de oliedrukdraad |
| Laadcontrole (D+) | GPIO27 | Digitaal, interrupt-vrij | Via PC817-optocoupler vanaf de laadcontroledraad |
| Grootlicht | GPIO33 | Digitaal, interrupt-vrij | Via PC817-optocoupler vanaf de grootlichtdraad |

### Toerental van de Bosch Motronic (BMW M42 / 318iS)

De Motronic-ECU stuurt al een kant-en-klaar toerental-signaal naar het originele instrumentenpaneel — dat is de makkelijkste bron, je hoeft niks nieuws te installeren.

- **Signaal:** blokgolf, rust-spanning ongeveer 12V, korte pulsen naar 0V bij elke vonk. **2 pulsen per motoromwenteling** (standaard voor een 4-cilinder, 4-takt motor).
- **Waar aftakken:** deze draad loopt zowel naar de instrumentenpaneel-connector als naar de ronde diagnoseconnector onder de motorkap. Zoek in je Bentley-handboek (of de bedradingsschema's specifiek voor jouw bouwjaar) naar de exacte pinnummers voor jouw type connector — die verschillen namelijk per uitvoering/bouwjaar, dus vertrouw niet blind op een pinnummer van een ander model.
- **Verifiëren voor je iets aansluit:** meet met een multimeter (of nog beter, een oscilloscoop) dat de rustspanning inderdaad rond de 12V ligt en dat je pulsen ziet bij het draaien van de motor. Dit voorkomt dat je de verkeerde draad te pakken hebt.

**Belangrijk — bescherming:** de ESP32's GPIO-pinnen verdragen maximaal 3.3V. Sluit deze 12V-pulsen **nooit rechtstreeks** aan.

De aanbevolen manier is een **PC817-optocoupler**, omdat die volledige galvanische scheiding geeft tussen het (elektrisch rumoerige) boordnet en de ESP32 — spanningspieken, ontstekingsruis en massalussen vanuit de auto bereiken de ESP32 dan helemaal niet. Dat is degelijker dan alleen een spanningsdeler, waarbij auto en ESP32 nog steeds dezelfde massa delen.

Opzet:

1. **Ingangszijde (LED, auto-kant):** een 1kΩ-weerstand van een geschakelde +12V-bron naar de LED-anode van de PC817, en de LED-kathode naar het tachosignaal. Zolang het signaal hoog is (rust, ~12V), staat er nauwelijks spanning over de LED en blijft hij uit. Zodra de ECU het signaal kort naar 0V trekt (elke vonk), ontstaat er ~12V over de weerstand+LED → hij licht kort op. Deze kant deelt **geen** massa met de ESP32.
2. **Uitgangszijde (fototransistor, ESP32-kant):** collector via een 10kΩ pull-up naar 3V3, en naar GPIO4; emitter naar GND (dit is wél dezelfde massa als de ESP32). Als de LED oplicht, gaat de fototransistor geleiden en trekt GPIO4 kort laag — een dalende flank, precies wat de firmware al verwacht (`attachInterrupt(..., FALLING)`). **De code hoeft niet aangepast te worden**, alleen de bedrading.

Heb je geen PC817 op voorraad en wil je snel iets testen? Een simpele spanningsdeler (10kΩ + 3,3kΩ, met eventueel een 3,3V-zenerdiode als extra klem naar GND) werkt ook, en beschermt de ESP32 prima tegen te hoge spanning — je mist dan alleen de galvanische scheiding. Voor een permanente installatie in de auto is de optocoupler de betere keuze.

De firmware telt de pulsen die binnenkomen op GPIO4 met een interrupt, en rekent dat om naar toeren per minuut — gedeeld door 1000, want dat is de schaal die de app verwacht (0-8 op de toerenteller).

### Waarom een spanningsdeler voor tank/temp — en welke waarden?

Sensoren in de auto (tank-vlotter, temperatuur-NTC) zijn **weerstandssensoren** — hun weerstand verandert, geen spanning. De ESP32's ADC-pinnen kunnen alleen een spanning tussen 0V en 3.3V lezen. Door de sensor en een vaste weerstand in serie te zetten (een spanningsdeler), en het middenpunt naar de ADC-pin te leiden, zet je de weerstandsverandering om in een afleesbare spanning. Anders dan bij de tacho (waar het puur om bescherming gaat) is de exacte waarde van de vaste weerstand hier belangrijk voor de nauwkeurigheid.

**Tank** (Bentley-specificatie voor de BMW E36-peilgever: 10Ω leeg, 250Ω vol):
- Vaste weerstand: **100Ω**, van 3V3 naar GPIO34
- Sensor: van GPIO34 naar GND
- Geeft ~0,30V bij leeg tot ~2,36V bij vol — een goede spreiding binnen het bereik

**Temperatuur** (gebruik de sensor die naar de wijzerplaat-meter gaat — zwarte kop, M14x1,5, fabrieksreferentie ~5000Ω bij 25°C; niet de ECU-sensor, die is voor het motormanagement):
- Vaste weerstand: **1,8kΩ**, van 3V3 naar GPIO35
- Sensor: van GPIO35 naar GND

Een NTC-sensor is niet lineair — de weerstand daalt exponentieel met de temperatuur. Voor een preciezere ECU-toepassing gebruik je de Steinhart-Hart-vergelijking, maar voor een wijzerplaat-meter volstaat een simpele lineaire interpolatie tussen twee kalibratiepunten. **Kalibreer zelf**: haal de sensor los, meet de weerstand in ijswater (0°C) en in kokend water (100°C) met een multimeter, en vul die waarden in bij `TEMP_CAL_LOW_OHMS`/`TEMP_CAL_HIGH_OHMS` in de firmware. De waarden die er nu in staan zijn indicatief — gebaseerd op het enige bevestigde referentiepunt (5kΩ bij 25°C) plus een typische NTC-curve, maar niet gegarandeerd exact voor jouw specifieke sensor.

### Waarom PC817-optocouplers voor de lampjes?

Als je de **originele BMW-bedrading** aftakt (de draad die naar het waarschuwingslampje in het cluster loopt), staat die draad niet gewoon op 0V wanneer het lampje uit is — hij hangt dan richting +12V (via het lampje/de LED-schakeling), en wordt pas naar 0V getrokken zodra de schakelaar (oliedruk, laadcontrole) sluit. Een ESP32-GPIO rechtstreeks op zo'n draad aansluiten is dus hetzelfde risico als bij het tachosignaal: die 12V kan de pin beschadigen.

Daarom gebruiken alle 4 de lampjes-ingangen dezelfde opzet als de tacho, elk met een eigen PC817:

1. **Ingangszijde (LED):** een 1kΩ-weerstand van geschakelde +12V naar de LED-anode, LED-kathode naar de bestaande lampjesdraad. Schakelaar dicht (lampje moet branden) → LED licht op. Deze kant deelt geen massa met de ESP32.
2. **Uitgangszijde (fototransistor):** collector via 10kΩ pull-up naar 3V3 + naar de GPIO-pin, emitter naar GND. LED aan → fototransistor trekt de GPIO-pin laag.

De firmware verwacht dus nog steeds "laag = lampje aan" (`readLamp()` blijft ongewijzigd) — alleen komt die lage stand nu via de PC817 tot stand in plaats van via een interne `INPUT_PULLUP` op een rechtstreeks aangesloten schakelaar. De pinnen staan daarom nu op gewone `INPUT`, niet meer op `INPUT_PULLUP` (de 10kΩ bij elke PC817 doet dat werk al).

**Alternatief zonder optocouplers:** als je liever geen 5 PC817's inbouwt, kun je in plaats van de fabrieksbedrading af te takken **eigen, losse schakelcontacten** monteren die uitsluitend naar massa schakelen (nooit naar 12V). Dan is de simpele `INPUT_PULLUP`-opzet weer veilig genoeg, en kun je de optocouplers overslaan voor deze 4 signalen — dat blijft alleen nodig voor de tacho, want die komt altijd rechtstreeks van de ECU.

### Waarom een condensator bij tank/temp?

Anders dan bij de lampjes en de tacho wekt de ESP32 hier zelf de spanning op (via de vaste weerstand) — de sensor is een passieve weerstand naar massa, er komt geen externe 12V aan te pas. Een optocoupler is hier dus niet nodig of geschikt (die is voor aan/uit-signalen, niet voor een continu variërende weerstand). Wat wél helpt tegen ontstekings-/alternatorruis op de lange sensordraden: een kleine **100nF-condensator** tussen elke ADC-pin (GPIO34/GPIO35) en GND.

**Let op bij aftakken:** je BMW heeft twee brandstofgevers (links/rechts, parallel geschakeld). Als je dezelfde sensor gebruikt die ook naar het originele cluster loopt, belasten beide circuits de sensor tegelijk en verstoort dat allebei de metingen. Gebruik bij voorkeur de andere sender exclusief voor de ESP32, zodat het origineel ongemoeid blijft.

**Test dit na montage** — als een lampje in de app juist aan staat wanneer het uit hoort te zijn, staat de logica omgekeerd. Pas dan in de code `readLamp()` aan (verwissel `LOW` naar `HIGH`).

---

## 4. Voeding

Voor permanent gebruik in de auto:

1. Sluit de ESP32 **niet rechtstreeks** op 12V aan — dat is te veel spanning.
2. Gebruik een 12V-naar-5V buck-converter, en sluit de 5V-uitgang aan op de **5V/VIN**-pin van de ESP32.
3. Neem de voeding via een **geschakeld** circuit (aan met het contact), zodat de ESP32 niet blijft draaien en je accu leegtrekt als de auto uitstaat.
4. Verbind alle GND's (ESP32, sensoren, voeding) met elkaar — een gemeenschappelijke massa is essentieel, anders werkt niets betrouwbaar.

---

## 5. Koppelen met de telefoon

1. Zet de ESP32 aan (met de firmware erop).
2. Ga op je telefoon naar **Instellingen → Bluetooth**.
3. Koppel met het apparaat **VDO_Dashboard_ESP32**.
4. Open de VDO Dashboard-app. Zodra de app permissies heeft gekregen (Bluetooth + locatie), verbindt hij automatisch met elk gekoppeld apparaat met die naam — er is geen aparte "verbind"-knop nodig.

---

## 6. Het protocol

De ESP32 stuurt elke 200ms een regel tekst over Bluetooth:

```
kph,rpm,fuel,temp,blinker,oel,ladung,fernlicht
```

| Veld | Betekenis | Bereik |
|---|---|---|
| `kph` | Snelheid | Wordt door de app genegeerd — die komt uit GPS |
| `rpm` | Toerental | 0.0 – 8.0 (dus al gedeeld door 1000; berekend uit de pulsen op GPIO4) |
| `fuel` | Brandstofniveau | 0.0 (leeg) – 1.0 (vol) |
| `temp` | Motortemperatuur | Graden Celsius |
| `blinker` / `oel` / `ladung` / `fernlicht` | Lampjes | `1` = aan, `0` = uit |

Voorbeeldregel: `0,3.2,0.65,88,0,0,1,0` betekent: 3200 rpm, tank 65% vol, motor 88°C, laadcontrolelampje aan, de rest uit.

---

## 7. Testen zonder de auto

Wil je de bedrading en app testen voordat alles in de auto zit? Sluit de ESP32 op een breadboard aan met een paar potentiometers (voor tank/temp) en drukknoppen (voor de lampjes) in plaats van de echte auto-sensoren. Zo kun je alles op je bureau checken voordat je gaat inbouwen.
