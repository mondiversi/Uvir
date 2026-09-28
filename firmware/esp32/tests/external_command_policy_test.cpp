#include "../UvirSensor/UvirExternalCommandPolicy.h"

#include <assert.h>
#include <stdio.h>

int main() {
  // Idle also represents an app-managed manual session on the sensor.
  assert(uvirExternalAcquisitionAllowed(false, false, false));
  assert(uvirExternalAcquisitionAllowed(true, true, false));
  assert(!uvirExternalAcquisitionAllowed(true, false, false));
  assert(!uvirExternalAcquisitionAllowed(false, false, true));
  assert(!uvirExternalAcquisitionAllowed(true, true, true));

  printf("External command session policy passed.\n");
  return 0;
}
