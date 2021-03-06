# Gravity Gun

A Spigot plugin to bring the Gravity Gun to Minecraft.
Enables the player to pick up and repel blocks and mobs.

## Commands
Command | Description | Permission
--- | --- | ---
`/gravitygun` or `/gg` | Gives the player a gravity gun | `gravitygun.cmd.get`
`/gg reload` | Reloads the config | `gravitygun.cmd.reload`

## Permissions
Permission | Description
--- | ---
`gravitygun.pickup.block` | Pick up a block with the Gravity Gun
`gravitygun.pickup.entity` | Pick up a mob with the Gravity Gun
`gravitygun.repelheld` | Repel the held block / mob
`gravitygun.repelarea` | Repel all mobs in an area
`gravitygun.cmd.get` | Use `/gg` or `/gg get`
`gravitygun.cmd.reload` | Use `/gg reload`

## Config
```yaml
# "true" if players should be able to pick up blocks with the gravity gun
allow-blocks: true
# "true" if players should be able to pick up tile entity blocks (chests, signs, and similar blocks) with the gravity gun
allow-tile-blocks: false
# blocks that a player should not be able to pick up
banned-blocks:
  - bedrock
  - barrier

# "true" if players should be able to pick up entities with the gravity gun
allow-entities: true
# entities that a player should not be able to pick up
banned-entities:
  - wither
  - ender_dragon

# "true" if players should be able to repel all entities in an area
allow-area-repel: true
# the peak velocity at which to repel entities in an area
area-repel-power: 7.5

# "true" if players should be able to repel the block / entity they have held
allow-held-repel: true
# the velocity at which to repel the held block / entity
held-repel-power: 2.0

# The range to look for blocks / entities to pick up
pickup-range: 20

# "true" if held entities shouldn't suffocate in blocks
prevent-suffocation-damage: true

pickup-sound:
  name: ENTITY_ILLUSIONER_PREPARE_BLINDNESS
  pitch: 1.5
  volume: 1.0
  enabled: true
drop-sound:
  name: ENTITY_ILLUSIONER_MIRROR_MOVE
  pitch: 0.6
  volume: 1.0
  enabled: true
repel-sound:
  name: ENTITY_DRAGON_FIREBALL_EXPLODE
  pitch: 1.2
  volume: 1.0
  enabled: true```