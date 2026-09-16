# Mage Additions

Mage Manhunt gameplay additions for Minecraft 1.21.1 NeoForge.

Current target:
- Minecraft 1.21.1
- NeoForge 21.1.219
- Java 21
- Iron's Spells 'n Spellbooks `1.21.1-3.14.8`

## Current feature: per-spell cast-time overrides

Mage Additions intercepts Iron's final effective cast duration at the common cast-start path. Rules use full spell IDs, so Iron's addon spells can be configured without hard-coding their Java classes.

Supported modes:
- `absolute` — replace the final effective duration with a fixed number of ticks.
- `multiplier` — multiply Iron's already-calculated effective duration.

`20 ticks = 1 second`.

### Config

The first run creates:

`config/mage_additions.json`

Example:

```json
{
  "settings": {
    "allow_instant_spell_delays": false,
    "max_cast_time_ticks": 72000
  },
  "cast_time_overrides": {
    "irons_spellbooks:fireball": {
      "enabled": true,
      "mode": "absolute",
      "value": 20
    },
    "some_addon:meteor": {
      "enabled": true,
      "mode": "multiplier",
      "value": 0.5
    }
  }
}
```

Reload while the server is running with:

`/mageadditions reload`

The command requires permission level 2.

## Animation safety

`allow_instant_spell_delays` is `false` by default. Iron's instant spells use instant cast behavior/animations, so giving them a non-zero cast duration can cause visual or behavioral mismatches. LONG and CONTINUOUS spells are the safe first test targets.

A later Mage Additions feature can add deliberate delayed-INSTANT/animation handling rather than relying on accidental behavior.

## Building

Put this exact jar in `libs/`:

`irons_spellbooks-1.21.1-3.14.8.jar`

Then run:

- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The built mod jar will be under `build/libs/`.

## Project structure

```text
src/main/java/net/fireboy/mageadditions/
├── MageAdditions.java
├── command/
│   └── ModCommands.java
├── config/
│   ├── CastTimeConfig.java
│   └── CastTimeOverrides.java
└── mixin/
    └── AbstractSpellMixin.java
```

Future spell-specific reworks, such as the Counterspell forward-area/cone behavior, should live under a separate behavior package instead of being mixed into the generic cast-time config system.
