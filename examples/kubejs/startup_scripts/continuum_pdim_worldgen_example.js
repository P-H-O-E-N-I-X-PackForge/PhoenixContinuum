// Personal dimension worldgen example. Copy to kubejs/startup_scripts/ in your pack (or instance), then restart.
// A personal dimension (pdim) is void with an obsidian pad unless you say otherwise. Everything here is optional.
//
// Startup script: the dimension files are written while the game builds its list of data packs, so a change needs a
// restart, and chunks already generated in a saved world keep their old terrain (use a fresh world to see it all).

ContinuumEvents.pdimWorldgen(event => {

  // 1. The moon: a flat grey world. Layers go from the bottom up, "block" or "block*height".
  //    With terrain you arrive standing on the ground at your plot, no obsidian pad.
  event.pdim('phoenix_continuum:example_moon')
    .flat('minecraft:stony_peaks',
      'minecraft:bedrock',
      'minecraft:deepslate*30',
      'minecraft:stone*28',
      'minecraft:tuff*2',
      'minecraft:gravel')

  // 2. The rock: real vanilla terrain with one biome, a desert world with caves, ores and structures from the noise
  //    settings. (Use .overworldLike() for the overworld's own biomes.)
  event.pdim('phoenix_continuum:example_rock')
    .noise('minecraft:overworld', 'minecraft:desert')

  // 3. The gas giant has no ground: leave it void (the default), but start the player high above the clouds
  //    on the pad instead of at the pad's usual height.
  event.pdim('phoenix_continuum:example_giant')
    .voidWorld()
    .arriveAt(200)

  // 4. Anything else: any chunk generator as an object, even another mod's, with its own dimension type.
  //    This one is a Conflux discipline world, with that discipline's terrain, structures and ores.
  //    (Needs PhoenixCore. Point it at one of your own bodies.)
  // event.pdim('mypack:hive_world').generator({
  //   type: 'phoenixcore:discipline',
  //   discipline_id: 'phoenix',
  //   seed: 19274618,
  //   biome_source: { type: 'minecraft:fixed', biome: 'phoenixcore:phoenix' }
  // }).dimensionType('phoenixcore:discipline')

  // Where players arrive, if you want to force it:
  //   .arriveOnSurface()   the ground at the plot (the default for terrain)
  //   .arriveOnPlatform()  build the obsidian pad whatever the terrain
  //   .arriveAt(100)       a fixed height, no pad
})
