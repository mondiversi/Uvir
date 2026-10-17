# ESP32 + generic SPI microSD adapter

This guide applies to the tested six-pin **MicroSD Card Adapter** with a
**1117C33 regulator and SN74HC125**. A generic name alone does not guarantee
the same circuit. See [complete wiring](README.md).

| Adapter label | ESP32 | Direction relative to ESP32 |
|---|---|---|
| VCC | 5V / VIN rail | Supply to this regulated adapter only |
| GND | GND | Shared ground |
| CS | GPIO 13 | Output: select card |
| SCK | GPIO 18 | Output: SPI clock |
| MOSI | GPIO 23 | Output: data to card (COPI) |
| MISO | GPIO 19 | Input: data from card (CIPO) |

The firmware uses SPI at **4 MHz**. This module is **not I2C** and does not
share SDA/SCL. Do not swap MISO/MOSI as if wiring two UARTs. Do not use GPIO 5
for CS simply because a generic ESP32 example does: UVIR uses GPIO 13.

The adapter's 5 V supply feeds its onboard 3.3 V regulator; ESP32-facing signals
must remain 3.3 V compatible. A bare card or a different 3.3 V-only breakout must
not receive 5 V. Check its circuit rather than assuming every six-pin board
matches. See [powering the complete build](../ASSEMBLY.md).

## Card and files

Use a **FAT32** card; 4 GB works for the intended build. Do not infer capacity
support solely from the adapter's size or generic label. Larger/exFAT cards
require checking filesystem compatibility and are not a reason to format an
existing card containing data. Back up before any deliberate format.

The firmware uses the card for final offline-record overflow, error logs and
checkpoint fallback when FRAM is unavailable. It mounts and owns its storage
format; inserting a card does not authorize wiping unrelated files. A
`sd_foreign` result requires investigation, not an automatic format.

Check `sd_available`, `storage_backend`, `sd_total_bytes`, `sd_used_bytes`,
`sd_free_bytes`, `sd_record_capacity_total` and `sd_record_capacity_free`.
Capacity estimates reserve space rather than promising every byte for records.
The diagnostic uses a bounded temporary file read/write/delete test.

The card controller manages its own flash translation/wear behavior. There is
no UVIR switch that enables microSD wear leveling, and a generic card label
cannot prove endurance. FRAM buffering reduces card traffic; keep good backups
and do not remove the card or cut power during writes. Shut down while idle
before extracting the card. Never ACK queued data merely to free space.
