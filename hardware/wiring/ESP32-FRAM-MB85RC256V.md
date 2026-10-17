# ESP32 + MB85RC256V FRAM

The current breakout has 32 KiB of nonvolatile I2C FRAM. It is not SRAM and
does not need a backup cell. See [complete wiring](README.md).

| Breakout pin | Connection / required state |
|---|---|
| VCC | ESP32 3V3 |
| GND | ESP32 GND |
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| A0, A1, A2 | Low for address `0x50`; retain verified default pull-down/jumper arrangement |
| WP | Low to allow writes; retain verified default pull-down, or tie to GND if needed |

Do not drive WP high: the bus may acknowledge while writes fail. The chip's
address and WP inputs have internal pull-downs, but check any breakout jumpers
or external resistors before changing the wiring. Explicit GND ties must not
short an existing high strap. No ESP32 GPIO is assigned to WP or address inputs.

The [manufacturer's data sheet](https://www.ramxeed.com/assets/images/products/datasheet/FeRAM/i3/MB85RC256V-DS11v3-E.pdf)
specifies a 2.7–5.5 V chip supply range; this build uses **3.3 V** to keep the
shared bus compatible. Do not infer that the ESP32 bus can tolerate 5 V because
the FRAM chip can.

## What is stored here

The firmware reserves space for redundant activity checkpoints, a queue of up
to **92 final pending records** (acquisitions, alerts or errors). A further 4 KiB
region is reserved for diagnostics/future use, not a second error-log queue. When the
queue cannot accept another final record, new records go to microSD; later
records can use FRAM again when slots become available. It is not a one-way
switch that permanently abandons FRAM once full.

Synchronization covers FRAM **and** the filesystem. ACK only after durable host
storage; unacknowledged records must remain recoverable. The microSD is still
required for the full storage design; internal flash is emergency fallback.

Check `fram_available`, `fram_queue_available`, `fram_record_capacity` and
`fram_records_used` in `HELLO`. A diagnostic also checks `fram_read_write_ok`
without destroying user queue contents. Do not run generic memory-fill examples
on a FRAM containing UVIR activity or pending data.
