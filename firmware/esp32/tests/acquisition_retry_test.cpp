#include "../UvirSensor/UvirAcquisitionRetry.h"

#include <assert.h>
#include <stdio.h>

int main() {
  uint8_t attempts = 0;
  assert(uvirRegisterFailedReadAttempt(attempts, 3));
  assert(attempts == 1);
  assert(uvirRegisterFailedReadAttempt(attempts, 3));
  assert(attempts == 2);
  assert(!uvirRegisterFailedReadAttempt(attempts, 3));
  assert(attempts == 3);

  uvirResetReadAttempts(attempts);
  assert(attempts == 0);
  assert(!uvirRegisterFailedReadAttempt(attempts, 1));
  assert(attempts == 1);

  printf("Acquisition read retry policy passed: bounded attempts and reset.\n");
  return 0;
}
