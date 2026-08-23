#include "BluetoothSerial.h"

BluetoothSerial SerialBT;
int rpm = 0;
int kph = 0;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("VDO_Dashboard_ESP32");
  Serial.println("ESP32 VDO Bluetooth zender gestart!");
}

void loop() {
  // Simulatie: toeren lopen op en snelheid schaalt mee
  rpm += 120;
  if (rpm > 8000) rpm = 0;
  kph = (rpm / 8000.0) * 200;

  // Stuur data als: snelheid,toeren
  SerialBT.print(kph);
  SerialBT.print(",");
  SerialBT.println(rpm);

  delay(40); // ~25 frames per seconde voor vloeiende meters
}

