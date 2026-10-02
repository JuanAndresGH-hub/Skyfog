# SkyFog

SkyFog is a client-side Fabric mod for Minecraft 26.2 that applies configurable,
uniform fog to the Overworld. It can match the sky to the fog color and hide
the sun, moon, stars, and clouds.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25

## Installation

Place the built `skyfog-*.jar` in the `mods` directory of a Fabric 26.2
instance. Open the configuration with **K** or `/skyfog` (the `/sf` alias is
also available). Commands include `/sf toggle`, `/sf start <blocks>`,
`/sf end <blocks>`, and `/sf color <red> <green> <blue>`.

## Compatibility and limitations

SkyFog only changes client rendering. Shaders and alternate renderers such as
Sodium or Iris may replace parts of vanilla rendering, so visual differences
are possible. Optional sky and cloud injections use `require = 0` so a changed
renderer signature does not prevent the client from starting; fog itself
remains strict. The MoulConfig 4.7.2 annotation GUI does not support
translation keys, so its option labels are English. Chat messages support the
independent SkyFog language setting.

This project is not affiliated with Mojang, Microsoft, Hypixel, or their
affiliates. It does not include code or resources from SkyHanni. It was
developed with assistance from AI.

## License

SkyFog is distributed under the MIT License. See `THIRD_PARTY_NOTICES` for
bundled dependency notices.
