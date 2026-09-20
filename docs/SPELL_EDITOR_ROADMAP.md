# Mage Additions Spell Editor Roadmap

This document is the implementation contract for the per-spell **Mage Additions** tab.
Iron's native config remains the source of truth for native Iron's values such as mana,
power, cooldown, max level, rarity and crafting.

## Numeric override behaviour

Every numeric Mage Additions property that has a meaningful upstream/base value should
use the shared linked editor:

- **Original**: read-only upstream/base value.
- **Value**: effective absolute value.
- **Multiplier**: value relative to Original.
- Editing **Value** recalculates Multiplier and stores the override as `absolute`.
- Editing **Multiplier** recalculates Value and stores the override as `multiplier`.
- Reset stores no override (`default` / inherited).
- If Original is zero, Multiplier is unavailable because division by zero has no useful
  meaning. Existing zero-base multiplier rules are preserved if loaded.

Example with Original = 10:

- Value 10, Multiplier 1.0x
- Change Multiplier to 2.0x -> Value becomes 20
- Change Value to 15 -> Multiplier becomes 1.5x

The distinction between absolute and multiplier is persisted deliberately. If an addon or
future Iron's update changes the upstream value from 10 to 12:

- a stored **2.0x multiplier** becomes Value 24;
- a stored **absolute 20** remains Value 20 and displays as ~1.6667x.

For level-scaling values, the editor must clearly identify the reference level used by the
Original/Value display. Runtime multiplier hooks should still scale the real value for the
actual cast level rather than flattening a level-scaling spell.

## Selected Mage Additions controls

### Casting

- Cast time override
- Movement while casting
- Allow while airborne

Movement should support at minimum:

- Default / inherited
- Normal
- Slowed
- Rooted

If Slowed is selected, movement speed should use the linked numeric control where a useful
base value exists.

### Targeting / geometry

- Range multiplier / value
- Projectile speed multiplier / value
- Duration multiplier / value
- Radius multiplier / value
- Max targets
- Require line of sight
- Minimum cast distance
- Maximum cast distance

Range, duration, radius and projectile speed are not implemented consistently by every
Iron's/addon spell. These settings therefore need a compatibility/capability adapter rather
than assuming one universal field exists. The editor should show unsupported properties as
unsupported instead of accepting a setting that does nothing.

### Entity interaction

- Friendly fire
- Self damage

These must integrate with the minigame's current generic team/alliance model. Do not add
Hunter/Runner-specific concepts; those roles are obsolete in the current minigame design.

### World interaction

- Block griefing
- Fire/environment griefing

Keep these separate:

- **Block griefing**: direct block destruction/replacement/explosion terrain damage.
- **Environment griefing**: fire, freezing, fluids and similar indirect world changes.

### Scroll generation

- Generate scroll: on/off/default
- Scroll weight
- Minimum scroll level
- Maximum scroll level

Maximum generated scroll level is independent from Iron's spell Max Level. A spell may be
valid at level 10 while random generation is restricted to levels 1-4.

### Cast limits

Use a scope rather than separate contradictory toggles:

- Unlimited
- Per player
- Per team
- Global

Then store a cast count/limit when the scope is not Unlimited. Per-team behaviour must use
the current generic minigame team model. Players without a team should receive a sensible
independent pool rather than relying on legacy Manhunt roles.

## Override state conventions

Boolean/tri-state behaviour overrides should use explicit inheritance:

- Default / inherited
- Allow / enabled
- Block / disabled

Numeric rules should use:

- Default / inherited
- Absolute
- Multiplier

Do not store an explicit `1.0x` or `false` merely to represent default behaviour. `Default`
means Mage Additions should not intervene, which improves compatibility with future Iron's
and addon updates.

## Implementation order

1. **Editor foundation**
   - linked Value <-> Multiplier control
   - remove duplicate Mage mana/cooldown controls
   - preserve server-authoritative save/snapshot flow

2. **Generic casting/targeting hooks**
   - movement while casting
   - airborne casting
   - line of sight
   - min/max cast distance
   - friendly fire / self damage
   - cast-limit scopes

3. **Scroll generation**
   - enable/disable generation
   - weight
   - min/max generated level
   - refresh generated scroll caches live

4. **Capability-backed spell geometry/effects**
   - range
   - projectile speed
   - duration
   - radius
   - max targets

5. **World interaction adapters**
   - block griefing
   - environment griefing

The capability-backed phases should isolate Iron's/addon-version-specific code behind
adapters so updating Minecraft/Iron's does not require rewriting the editor itself.
