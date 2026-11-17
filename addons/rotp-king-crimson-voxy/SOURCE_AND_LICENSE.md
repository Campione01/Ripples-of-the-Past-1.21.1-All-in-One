# RotP King Crimson Voxy (`rotp_kingcrimson_voxy`): source and license

## Public release

- Module: `rotp-king-crimson-voxy`, mod id `rotp_kingcrimson_voxy`, version `0.1.0-port.1`.
- Origin: this project's own add-on, written by Campione01 for NeoForge 1.21.1 (work dated 2026-11-06 to 2026-11-15); it is not a port of a 1.16.5 add-on.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The record below is the one kept with the module; local file-system paths in it were reduced to file names, and links into the private workspace to their titles.

## RotP King Crimson Voxy source and license

Own code for this port, written for the 1.21.1 King Crimson add-on (`rotp_kingcrimson`). This module has no donor in
1.16.5 and contains no code copied from Voxy, Sodium or Iris. It patches Voxy's shader text at load time, at anchors it
checks first, and it does nothing unless Voxy, Sodium, Iris and King Crimson are all present.

It uses `GPL-3.0-only`, the licence of the King Crimson add-on it extends. The `src/stubs` source set holds empty
compile-time stand-ins for two Sodium types and is not packaged.
