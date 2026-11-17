# RotP Spin (`rotp_spin`): source and license

## Public release

- Module: `rotp-spin`, mod id `rotp_spin`, version `1.0-port.1`.
- Original 1.16.5 add-on: `RotP-Spin-1.0.jar` (RotP Spin Addon, mod id `rotp_spin`), by Loischsiy; declared license: GNU GPL v3.0; SHA-256 `2F6139AD48B5890D2E81EC5C1A2D162CB3D073F163CFE2579EE3948ED6323E35`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-11-06 to 2026-11-11); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Spin source and license

The 1.16.5 Forge donor is `RotP-Spin-1.0.jar` (365,822 bytes, SHA-256
`2F6139AD48B5890D2E81EC5C1A2D162CB3D073F163CFE2579EE3948ED6323E35`),
mod ID `rotp_spin`, display name **RotP Spin Addon**. The donor metadata
credits **Loischsiy** as author and **StandoByte** for the add-on template.
The donor's `META-INF/mods.toml` declares `GNU GPL v3.0`; no separate
license file is present in that JAR. This module uses `GPL-3.0-only`.

Donor source was recovered with Vineflower from the verified local JAR,
without executing donor code. Compatible art is transferred from the donor,
not redesigned. Java, data and rendering routes that require migration are
reviewed against the donor; see the migration inventory and handback for the
scope of the candidate and its verification.

The build and native test cores are the pinned 1.21.1 outputs of
`c7f733919b91aa1f125f66bbc5b882c2537050a4`. The tested core ABI and
namespaced ability resources are checked at mod initialization.
