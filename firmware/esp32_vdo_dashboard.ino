#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

// Pas deze pinnen aan naar jouw bedrading
const int FUEL_SENSOR_PIN = 34;   // brandstofpeilgever via spanningsdeler (analoog)
const int TEMP_SENSOR_PIN = 35;   // NTC-thermistor via spanningsdeler (analoog)

const int BLINKER_PIN = 25;       // richtingaanwijzer, via PC817-optocoupler
const int OEL_PIN = 26;           // oliedruklampje, via PC817-optocoupler
const int LADUNG_PIN = 27;        // laadcontrolelampje (dynamo D+), via PC817-optocoupler
const int FERNLICHT_PIN = 33;     // grootlichtlampje, via PC817-optocoupler

const int TACHO_PIN = 4;          // toerental-signaal, via spanningsdeler (BMW Motronic "Drehzahl"-uitgang)

// BMW M42 (4-cilinder): de ECU stuurt 2 pulsen per motoromwenteling naar de
// originele toerenteller. Pas dit aan als je een ander motortype aansluit.
const int PULSES_PER_REV = 2;
const unsigned long UPDATE_INTERVAL_MS = 200; // moet gelijk zijn aan de delay() onderin loop()

// ---- Meetdeler-instellingen (zie ESP32_installatiegids.md voor de berekening) ----
const float ADC_MAX = 4095.0;      // 12-bit ADC
const float VREF = 3.3;            // ESP32 ADC-referentie

// Tank: BMW E36-peilgever loopt (Bentley-specificatie) van 10 ohm (leeg) tot
// 250 ohm (vol). Vaste weerstand van 3V3 naar GPIO34, sensor van GPIO34 naar GND.
const float FUEL_FIXED_R = 100.0;
const float FUEL_EMPTY_OHMS = 10.0;
const float FUEL_FULL_OHMS = 250.0;

// Temp: gebruik de sensor die naar de wijzerplaat-meter gaat (zwarte kop,
// M14x1,5, ~5000 ohm bij 25°C) - niet de ECU-sensor. Vaste weerstand van 3V3
// naar GPIO35, sensor van GPIO35 naar GND.
const float TEMP_FIXED_R = 1800.0;
// TODO: vul deze twee punten in na eigen kalibratie (multimeter, sensor los,
// eerst in ijswater dan in kokend water) - de fabriekswaarden zijn niet
// gegarandeerd exact voor jouw specifieke sensor.
const float TEMP_CAL_LOW_C = 0.0;
const float TEMP_CAL_LOW_OHMS = 5700.0;   // indicatief, nog te verifiëren
const float TEMP_CAL_HIGH_C = 100.0;
const float TEMP_CAL_HIGH_OHMS = 190.0;   // indicatief, nog te verifiëren

volatile unsigned long pulseCount = 0;

void IRAM_ATTR onTachoPulse() {
  pulseCount++;
}

void setup() {
  Serial.begin(115200);
  // Naam moet EXACT overeenkomen met wat MainActivity.kt zoekt
  SerialBT.begin("VDO_Dashboard_ESP32");
  Serial.println("Bluetooth SPP gestart, wachten op koppeling...");

  // De 10k pull-up zit nu extern bij elke PC817 (uitgangszijde), dus geen
  // interne INPUT_PULLUP meer nodig op deze pinnen.
  pinMode(BLINKER_PIN, INPUT);
  pinMode(OEL_PIN, INPUT);
  pinMode(LADUNG_PIN, INPUT);
  pinMode(FERNLICHT_PIN, INPUT);

  pinMode(TACHO_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(TACHO_PIN), onTachoPulse, FALLING);
}

void loop() {
  // kph wordt momenteel niet gebruikt door de app (die komt uit GPS),
  // maar mag gewoon meegestuurd worden.
  float kph = 0.0;
  float rpm = readRpmFromSensor(); // al geschaald naar "x1000", zoals de app verwacht
  float fuel = readFuelLevel();    // 0.0 (leeg) .. 1.0 (vol)
  float temp = readTemperature();  // graden Celsius

  int blinker = readLamp(BLINKER_PIN);
  int oel = readLamp(OEL_PIN);
  int ladung = readLamp(LADUNG_PIN);
  int fernlicht = readLamp(FERNLICHT_PIN);

  // Protocol dat MainActivity.kt verwacht:
  // "kph,rpm,fuel,temp,blinker,oel,ladung,fernlicht\n"
  SerialBT.print(kph);
  SerialBT.print(",");
  SerialBT.print(rpm);
  SerialBT.print(",");
  SerialBT.print(fuel);
  SerialBT.print(",");
  SerialBT.print(temp);
  SerialBT.print(",");
  SerialBT.print(blinker);
  SerialBT.print(",");
  SerialBT.print(oel);
  SerialBT.print(",");
  SerialBT.print(ladung);
  SerialBT.print(",");
  SerialBT.println(fernlicht);

  delay(UPDATE_INTERVAL_MS); // ~5x per seconde updaten is ruim voldoende voor de naalden/lampjes
}

// Elke lampjes-ingang loopt via een PC817-optocoupler (zie ESP32_installatiegids.md):
// als het originele waarschuwingslampje "aan" moet, licht de PC817-LED op en trekt
// de fototransistor deze pin LAAG. Pas de vergelijking aan als jouw circuit andersom werkt.
int readLamp(int pin) {
  return digitalRead(pin) == LOW ? 1 : 0;
}

float readRpmFromSensor() {
  // Tel het aantal pulsen dat sinds de vorige meting binnenkwam (via de interrupt
  // hierboven), en reken dat om naar toeren per minuut.
  noInterrupts();
  unsigned long count = pulseCount;
  pulseCount = 0;
  interrupts();

  float freqHz = count / (UPDATE_INTERVAL_MS / 1000.0);
  float rpm = (freqHz * 60.0) / PULSES_PER_REV;
  return rpm / 1000.0; // de app verwacht rpm al gedeeld door 1000 (schaal 0-8)
}

// Rekent een ADC-uitlezing (0-4095) terug naar de weerstand van de sensor,
// ervan uitgaande dat de sensor tussen de ADC-pin en GND zit, met de vaste
// weerstand tussen 3V3 en diezelfde pin.
float adcToResistance(int raw, float fixedR) {
  float v = (raw / ADC_MAX) * VREF;
  if (v <= 0.001) return 0.0; // voorkom delen door (bijna) nul
  return fixedR * v / (VREF - v);
}

float readFuelLevel() {
  int raw = analogRead(FUEL_SENSOR_PIN);
  float sensorR = adcToResistance(raw, FUEL_FIXED_R);
  float fraction = (sensorR - FUEL_EMPTY_OHMS) / (FUEL_FULL_OHMS - FUEL_EMPTY_OHMS);
  return constrain(fraction, 0.0, 1.0);
}

float readTemperature() {
  int raw = analogRead(TEMP_SENSOR_PIN);
  float sensorR = adcToResistance(raw, TEMP_FIXED_R);

  // Eenvoudige lineaire interpolatie tussen de twee kalibratiepunten.
  // Een NTC is in werkelijkheid exponentieel, maar voor een wijzerplaat-meter
  // (geen precisie-instrument) is dit ruim voldoende, zeker rond de normale
  // bedrijfstemperatuur van de motor.
  float t = TEMP_CAL_LOW_C + (sensorR - TEMP_CAL_LOW_OHMS) *
            (TEMP_CAL_HIGH_C - TEMP_CAL_LOW_C) / (TEMP_CAL_HIGH_OHMS - TEMP_CAL_LOW_OHMS);
  return t;
}
