// Personal dimension worldgen example. Copy to kubejs/startup_scripts/ in your pack (or instance), then restart.
// A personal dimension (pdim) is void with an obsidian pad unless you say otherwise. Everything here is optional.
//
// Startup script: the dimension files are written while the game builds its list of data packs, so a change needs a
// restart (unless the Infiniverse mod is installed: then dimensions are made while the game runs), and chunks already
// generated in a saved world keep their old terrain (use a fresh world to see it all).

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

  // 3. The gas giant has no ground: leave it void, the default (you arrive on the obsidian pad). Naming it with
  //    .voidWorld() puts it back to void if something else changed it.
  event.pdim('phoenix_continuum:example_giant')
    .voidWorld()

  // 4. Anything else: any chunk generator as an object, even another mod's, with its own dimension type.
  //    This one is a Conflux discipline world, with that discipline's terrain, structures and ores.
  //    (Needs PhoenixCore. Point it at one of your own bodies.)
  // event.pdim('mypack:hive_world').generator({
  //   type: 'phoenixcore:discipline',
  //   discipline_id: 'phoenix',
  //   seed: 19274618,
  //   biome_source: { type: 'minecraft:fixed', biome: 'phoenixcore:phoenix' }
  // }).dimensionType('phoenixcore:discipline')

  // 5. Rules for many bodies at once (they need the Infiniverse mod; a spec for a named body above always wins):
  //    every other moon is a plain grey flat world, and everything else works itself out from the body's own data
  //    (rocky worlds get a biome from their climate; gas giants, stars and black holes stay void).
  event.type('moon', spec => spec.flat('minecraft:stony_peaks', 'minecraft:bedrock', 'minecraft:stone*60'))
  event.all(spec => spec.auto())

  // 6. A base waiting at the plot the first time a player makes the world (a structure template of your own):
  // event.pdim('phoenix_continuum:example_rock').plotStructure('mypack:landing_pad')

  // 7. World rules. A vacuum moon: no mobs, no air without a helmet, GregTech's overworld ores in the ground, and every
  //    player kept to (and protected on) their own plot.
  // event.pdim('phoenix_continuum:example_moon')
  //   .mobs(false)
  //   .requireOxygen('#mypack:space_helmets')
  //   .gtOres('minecraft:overworld')
  //   .plotRadius(300)
  //   .protectPlots(true)
  //   .weather('clear')           // or 'rain', 'storm', 'normal'; .weatherCycle(600, 3600, 300, 600, 0.25) for its own spells

  // 8. An ordinary shared dimension instead of a plot each: everybody arrives at the same spot (the world's origin, or
  //    wherever you say), and plot limits and protection do nothing. The config option personalPlots=false does it for all.
  // event.pdim('phoenix_continuum:example_rock').plots(false).spawnAt(0, 70, 0)

  // Where players arrive, if you want to force it:
  //   .arriveOnSurface()   the ground at the plot (the default for terrain)
  //   .arriveOnPlatform()  build the obsidian pad whatever the terrain
  //   .arriveAt(100)       a fixed height, no pad (only for terrain: in void you would fall)
})
