# Continuum for pack developers: plan

Decisions made (2026-10-20):

1. Content inherits from other content (`inherits`), it is not patched by field overrides.
2. Everything is datapack-style, so KubeJS (`kubejs/data/...`) works unchanged, and there is also a plain
   `config/phoenix_continuum/pack/` folder that behaves like a datapack.
3. The JSON format is versioned and carries a compatibility promise.
4. Visibility (what shows on the map) is controlled by a powerful API. The default is that everything shows right away.

## What exists today

Bodies, systems, disciplines and affinities are JSON under `data/<namespace>/continuum/...` and reload with datapacks.
There are ~97 config entries. Failures are only logged. There is no schema, no docs, no inheritance.

## 1. Inheritance

- New key `"inherits": "namespace:other_body"` on bodies and systems. (`parent` already means "the planet this moon
  orbits", so it is not reused.)
- Resolution happens on the raw JSON before parsing: load everything, resolve each `inherits` chain, deep-merge the
  child over the parent (objects merge key by key, arrays and scalars are replaced), then parse.
- `"abstract": true` marks a template: it can be inherited from but is not registered as a body.
- Cycles and missing parents are validation errors that name the whole chain.
- Load order is fixed and documented: built-in data, then mod and datapack namespaces (normal datapack order), then the
  pack folder. A later file with the same id replaces an earlier one (standard datapack behaviour), and `inherits` can
  point at anything loaded, so a pack can say "my body is Aurum, but with rings".
- `"disabled": true` removes a body or system a lower layer defined.

## 2. Datapack-style everywhere

- Nothing new is needed for datapacks or `kubejs/data/`: the same paths already load. The work is making that
  guaranteed and documented, and making errors visible.
- `config/phoenix_continuum/pack/` is registered as an extra data pack at the top of the stack (a folder pack with a
  `pack.mcmeta` generated on first run), so packs without KubeJS have one obvious place to drop files.
- `/continuum reload` reloads only Continuum data and re-syncs it to clients, without a full `/reload`.
- Optional, later: a small KubeJS binding for events and visibility rules (see 4), never required for content.

## 3. Format promise

- Every file may carry `"format": <integer>`. Missing means 1.
- Within one format number the promise is additive only: new optional keys with defaults, never a changed meaning or a
  removed key. Anything breaking bumps the number and ships a migration note plus a validator message that tells the
  author exactly what to change.
- A key deprecated in format N keeps working with a logged warning through format N+1.
- Unknown keys are validation warnings (typos), not silent.
- Ids are a promise too: built-in body, system and discipline ids never change once released. Removing one is a
  breaking change, and the validator warns when an id that saved outposts or missions use stops existing.

## 4. Visibility API (everything shows by default)

Three separate questions, kept separate so packs can control each one:

| Question | Meaning | Default |
|---|---|---|
| Visible | Does it appear on the map and in lists at all | yes |
| Discovery stage | Detected, then surveyed (the existing stages) | detected: survey is still a mission |
| Accessible | Can missions be launched to it | yes, still subject to rocket tier |

Mechanisms, all combinable, evaluated per team:

- **JSON rules** on a body or system: `"visibility": { "visible_if": <condition> }`, same for `accessible_if`. A
  condition is `{ "type": "...", ... }` or `{ "all_of": [...] }`, `{ "any_of": [...] }`, `{ "not": {...} }`.
  Built-in condition types: `research` (a Conflux node), `flag` (a team flag), `stage` (another body's stage), `body`
  (another body is visible), `advancement`, `item_in_pad`, `always`, `never`.
- **Condition registry**: `ContinuumConditions.register(id, codec, test)` so other mods add their own types (game
  stages, quests, whatever the pack uses).
- **Event** `ContinuumVisibilityEvent` (per team, per body, with the computed result) so Java code or KubeJS has the
  final say, in both directions.
- **Commands**: `/continuum show|hide <team|player> <body|system|all>` for per-team overrides that persist, and
  `/continuum progression` to see which rule hid a thing (explain mode).
- **Progression mode** config: `SHOW_ALL` (default) or `RESEARCH_GATED`. In `RESEARCH_GATED` the built-in research
  gates apply (the Phoenix progression, which the Phoenix modpack sets). In `SHOW_ALL` built-in gates are ignored and
  only rules a pack wrote itself apply.

To confirm before building: should built-in content also show everything on a fresh world under the default, with the
research gates only in `RESEARCH_GATED`? That is how this plan reads "default of all showing right away".

## 5. Feedback and tooling (supports all of the above)

- `/continuum validate`: every bad reference, unknown key, inheritance problem, format issue, with file and path. Runs
  automatically after a reload; ops see a summary in chat, the full list goes to a log file. Item and research ids can
  only be checked once registries are loaded, so the first full check is at server start.
- `/continuum dump <body>`: the fully resolved body (after inheritance and defaults).
- `/continuum reveal`: testing shortcut for the team's stages.
- Docs: one page per file type (every key, type, default, example), plus a small example pack (one system, a ringed
  planet, a quasar, an inheriting body) and a "add a world in ten minutes" tutorial in the Phoenix Wiki.
- Later: operator-only live-tuning panel for visual values, with an "export JSON" button.

## Build order

1. Validator and `/continuum reload` (everything else reports through it).
2. Inheritance resolution (`inherits`, `abstract`, `disabled`) plus the pack folder.
3. Format field and the compatibility checks.
4. Visibility conditions, event, commands and progression mode.
5. Docs and example pack.
6. Live-tuning panel, KubeJS binding (optional).

## Risks

- Deep merge hides where a value came from. `/continuum dump` and per-key provenance in validator output are not
  optional.
- Visibility rules are evaluated often (map draw, lists). Results are cached per team and invalidated on research,
  flag, command or reload.
- Changing the default from gated to show-all changes existing worlds; `RESEARCH_GATED` must be easy to set and the
  Phoenix pack should set it explicitly.

## Status (2026-10-21)

Built: validator and `/continuumadmin validate|reload|dump`, inheritance (`inherits`, `abstract`, `disabled`), format
check, the pack folder, the visibility layer (condition registry, `visibility.visible_if` / `accessible_if`, per-team
`/continuumadmin show|hide|unset|explain`, `ContinuumVisibilityEvent`, `progressionMode`), and
`/continuumadmin pdimfiles` (writes the personal-dimension files a new body needs; a restart enables them).

Content: Continuum now ships one example system (`phoenix_continuum:example`: a rocky planet, a moon that inherits it,
and a ringed gas giant). Everything PFT-specific (the other systems and bodies, disciplines, personal dimension files,
research and gated recipes, nebulas) ships from PhoenixCore under the same ids, so existing saves are unaffected.

The PFT modpack should set `progressionMode = RESEARCH_GATED` so its research gates keep deciding what is found.

Still to do: docs pages and the example pack walkthrough, nested-key validation, removing bodies that have reference
errors from the registry, KubeJS bindings, the live-tuning panel.

## Status update (KubeJS and tuner)

Built: a KubeJS plugin (compile-only dependency; loaded only when KubeJS is installed) with the `Continuum` global
(`ContinuumAPI`), script-defined condition types, and the `ContinuumEvents` group (`visibility`, `discovery`,
`mission`); see KUBEJS.md. The same `ContinuumAPI` and the game-bus events (`ContinuumEvents.Discovery`,
`ContinuumEvents.MissionChanged`, `ContinuumVisibilityEvent`) are usable from Java without KubeJS.

Built: the live tuner, `/continuum tune [body]` (client command; operators or the single-player owner). It edits a
body's visual parameters on this client only, shows a surface preview, and exports the merged definition to
`config/phoenix_continuum/tuner_export/` and the clipboard, ready to drop into the pack folder.

The tuner now also edits server-side fields for operators (name, description, trip minutes, starting stage, rocket tier
needed, orbit, personal dimension cost, and the yield list), plus a "push visual edits" button that sends the local
visual edits to the server. Server edits are saved as small patch files in `config/phoenix_continuum/tuning/<ns>/`
(only the changed keys), merged over the resolved definition on every load and re-synced to everyone at once. Deleting a
patch, or "Reset server edits", puts the body back; the original definition files are never modified.

## The default game (nothing to configure)

Continuum ships a small progression as ordinary data, so a player with no commands and no operator rights has somewhere to go:

1. Build a launch complex, make a rocket and probes, open the map from the controller.
2. **Example Rock** is on the map from the start. Surveying it earns the research `lunar_survey`, which reveals **Example Moon**
   (`gate.detected_research`). Surveying the moon earns `deep_space`, which reveals **Example Giant**.
3. The team is told in chat when it earns research, and `/continuum goals` lists what to survey next (the ones that open
   something new come first). No operator is involved at any point.

Everything here is data you can replace: edit or disable the example definitions, write your own `grants_research` and
`gate` entries, set `announceResearch` off in the config, or set `progressionMode` to `SHOW_ALL` to put everything on the map
from the start. A mod that provides its own research (PhoenixCore) takes over the research side and these grants do nothing.

## Visuals, personal dimension skies and profiling

- **Dyson swarm:** set a system's `star_kind` to `dyson_swarm` (or `megastructure`) for a star wrapped in nested, tilted shells
  of reflective panels. It has real depth: far panels show their inner faces lit by the star, seen through gaps in the near
  ones, which are dark against the glare and hide the star and its shafts of light. The first shell is a band in the orbital
  plane, the others are tipped away from it. It also draws in a personal dimension's sky.
- **Anomalies:** a body can list things worth a closer look in `"anomalies"`: each has an `id`, `name`, `description`, a
  `reveal` stage (`"detected"` or `"surveyed"`, default surveyed), an optional `cost` (`{"item": "...", "count": n}`, taken
  from the player) and a `reward` (`items` with `item`, `count` or `min`/`max`, and `chance`; an optional `research` node
  to grant; and a `message`). Once a team has reached the reveal stage the body panel on the map lists it with an
  Investigate button; each can be investigated once per team. Scripts hear about it with
  `ContinuumEvents.anomaly(event => ...)` (`event.player`, `event.team`, `event.body`, `event.anomaly`).
- **Observatory:** a block (`phoenix_continuum:continuum_observatory`, with a plain crafting recipe you can override) that opens
  a live 3D view of every body and star the team has found: a list to pick from, drag to turn, scroll to zoom, and the
  orbit, launch window, anomalies and lore for each. Detected-only bodies show as the faint scan return they are.
- **Comets:** passing comets (transient bodies) now trail a coma, an ion tail and a dust tail on the map, and cross the sky of
  a personal dimension in that system while they pass.
- **Named expeditions:** the mission planner has a "Name and crew" button: a flight can be given a name and up to three online
  teammates as crew. It appears under that name in the fleet and the log ("Expedition "Dawn Runner" (crew: ...)"), and each
  crew member lowers the flight's chance of failing by 3%.
- **Launch windows:** where a world is on its orbit changes how long a flight there takes and how hard it is on the hull: up
  to 30% quicker when the world is on the side of its star facing home, 30% slower on the far side (`launchWindowStrength`
  in the config; 0 turns it off). One orbital day is `launchWindowDayMinutes` real minutes (default 6), so inner worlds cycle
  in hours and outer ones in days. The map shows the window, how it changes the flight, and when it next opens or closes.
- **Sky events in a personal dimension** (nothing to configure; they follow from the world): the moons of the world you stand
  on cross the star in eclipse seasons (the day dims, stars come out, a corona and a sunset glow ring the horizon; a moon
  smaller than the star leaves a ring of it showing); a world with `"style": "aurora"` (or ice and some air) shows
  curtains of light over the northern horizon at night; a deep, cloudy atmosphere (cloud cover over 0.5) flashes with
  lightning; and around a black hole the sky bends into an Einstein ring.
- **Dyson sphere:** `star_kind` of `dyson_sphere` (or `dyson_shell`) shuts the star inside a complete shell: dark plates with
  glowing seams, great rifts where the star shows through, a lit belt of docks in the orbital plane, and the glare ringing
  its edge. It is opaque, so it hides the star.
- **Rogue planet:** a transient body with `"style": "rogue"` is a planet that belongs to no star, drifting through the system for
  the body's `transient` window. It is drawn as an ordinary planet (give it a dark surface), without a comet's tails; in a
  personal dimension it swells as it comes close and shrinks as it leaves. Extraction runs can catch it, like a comet.
- **Supernova remnant:** a transient body with `"style": "remnant"` is drawn as the expanding shell of an exploded star (a ragged
  red shock front, a green-teal layer behind it, a blue-white glow inside and a pulsing neutron star at the centre) that swells over
  the visit and fades; its surface `mid` colour tints it. In a personal dimension it is a huge, faint shell on the sky.
- **Tidal disruption event:** a transient body with `"style": "tidal_disruption"` is drawn as a star being torn apart by the
  system's black hole instead of as a planet: the star stretched along its path, a stream of gas spiralling in (red to
  blue-white as it falls), and the ring round the hole flaring. It appears for the body's `transient.duration_minutes`
  and fades. It uses the ordinary transient mechanism, so duration, cooldown, chance and the infall range are the usual
  `transient` keys. Observation only, like a comet.
- **Personal dimensions** now show the sky as if you stand on that world: the rest of the system with phases (other planets
  with their rings, and the world's own moons), placed from their orbits and the game clock, and only the ones the team has
  found. The old view of the planet itself in the sky is still there as a choice ("Sky" button when you create a
  dimension, changeable any time). A moon still shows its host planet.
- **Profiling:** `/continuum profile start` shows the GPU and CPU time of each draw pass on screen (planet, atmosphere, ring,
  each star effect, backdrop, pdim sky...). `report` prints it, `stop` ends it and logs it.
- **Shader tools:** `./gradlew checkShaders` compiles every shader with your graphics driver; `./gradlew benchShaders` draws
  each full-quad shader offscreen, writes a picture of each to `build/shader-bench`, and times it covering a whole
  1280x720 target (the worst case). Add `-PbenchW=1920 -PbenchH=1080` for another size.
- **Rings overhead:** standing on a planet that has `rings`, the ring is drawn as an arc across the sky, in the planet's equatorial
  plane tipped by the ring's own `tilt`, with the planet's shadow falling across it away from the sun. It turns with the planet,
  so it stays put in your sky. The world under your feet hides what has gone below the horizon (a ring, another planet).

## Personal dimension worldgen

A personal dimension (pdim) is void by default, with an obsidian pad to stand on. You can give any body real terrain, a
vanilla noise world, or any chunk generator at all (another mod's too).

**KubeJS** (a startup script; the dimension files are written while the game builds its list of data packs, so a change
needs a restart, and chunks already generated in a saved world keep their old terrain):

```js
ContinuumEvents.pdimWorldgen(event => {
  // layers of blocks from the bottom up ("block" or "block*height") over one biome
  event.pdim('phoenix_continuum:luna').flat('minecraft:plains', 'minecraft:bedrock', 'minecraft:stone*60', 'minecraft:grass_block')

  // a vanilla style world, one biome or the overworld's own
  event.pdim('phoenix_continuum:mars').noise('minecraft:overworld', 'minecraft:desert')
  event.pdim('phoenix_continuum:earth').overworldLike()

  // anything else: a chunk generator as an object, and its dimension type if it needs its own
  event.pdim('mypack:hive').generator({
    type: 'phoenixcore:discipline', discipline_id: 'phoenix', seed: 19274618,
    biome_source: { type: 'minecraft:fixed', biome: 'phoenixcore:phoenix' }
  }).dimensionType('phoenixcore:discipline')

  // where players arrive: .arriveOnSurface() (the default for terrain), .arriveOnPlatform(), .arriveAt(100)
})
```

**Java**: `PdimWorldgen.of(bodyId).flat(...)` (or `.noise`, `.overworldLike`, `.generator`, `.dimensionType`,
`.arriveAt`), from your mod constructor or by listening for `PdimWorldgen.Collect` on the game bus. `of` creates the
spec if the body has none and returns it, so there is nothing to register afterwards.

Arrival: a void world builds the pad at the player's plot, as before. With terrain the player is put on the ground at
the plot (plots are 1024 blocks apart), and the pad is only built if the column is empty. A spec can force the pad or a
fixed height. A spec registered in code or script wins over a hand-written `dimension/pdim/<body>.json`.

### More than terrain

**No restart (Infiniverse).** With the Infiniverse mod installed (and `runtimePersonalDimensions` left on in the config) a
personal dimension is made the first time somebody needs it, from its spec, while the game runs. A new body, or a spec
you add or change, needs no restart and no `/continuumadmin pdimfiles`. Without Infiniverse nothing changes: dimensions
are files read when the world loads. A changed spec only affects chunks not made yet; `/continuumadmin pdimworldgen reset
<body>` drops the loaded dimension so the next visit makes it again from the current spec (what was built stays).

**Rules instead of one body at a time** (they are worked out when a dimension is made, so they need Infiniverse; a spec
made for a named body always wins over a rule):

```js
ContinuumEvents.pdimWorldgen(event => {
  event.type('moon', spec => spec.flat('minecraft:stony_peaks', 'minecraft:bedrock', 'minecraft:stone*60'))
  event.system('mypack:home_system', spec => spec.auto())
  event.where(body => body.radius() > 3, spec => spec.overworldLike())
  event.all(spec => spec.arriveOnSurface())
})
```

**`.auto()`** works the world out from the body: stars, black holes and gas giants stay void; rocky worlds get terrain in
a biome that follows their distance from the star, their ice and their oceans (an earth-like ocean world gets the
overworld's own biomes, an airless moon bare rock).

**A base at the plot:** `.plotStructure('mypack:landing_pad')` places that structure template when a player first makes
the dimension (its floor on the arrival point, its middle on the plot).

**Events** (server scripts): `ContinuumEvents.pdimEnter(e => ...)` with `e.player`, `e.body`, `e.created` (true the first
time), and `ContinuumEvents.pdimLeave(e => ...)`. From Java, `ContinuumEvents.PdimEntered` and `PdimLeft` on the game bus.

**Files, for packs without KubeJS:** `config/phoenix_continuum/pdim_worldgen/*.json`, one spec per file:

```json
{ "body": "phoenix_continuum:luna", "type": "flat", "biome": "minecraft:stony_peaks",
  "layers": ["minecraft:bedrock", "minecraft:stone*60", "minecraft:gravel"] }

{ "match": { "type": "moon" }, "type": "auto", "plot_structure": "mypack:landing_pad" }
```

`type` is `void`, `flat`, `noise` (`settings` and a `biome`), `overworld_like`, `auto` or `generator` (a `generator`
object). Also optional: `dimension_type`, `arrival` (`surface`, `platform` or `fixed` with `y`) and `plot_structure`.
`match` takes `type`, `system` and `ids`.

**`/continuumadmin pdimworldgen`** lists what each body will get (generator, arrival, structure) and whether it is loaded.

### World rules, plots and ores

Each spec can also say how the world behaves:

```js
event.pdim('mypack:vacuum_moon')
  .flat('minecraft:stony_peaks', 'minecraft:bedrock', 'minecraft:stone*60')
  .mobs(false)                               // nothing spawns on its own (natural, spawners, patrols)
  .fallDamage(true)                          // a fall hurts (default false)
  .requireOxygen('#mypack:space_helmets')    // no air: damage each second without this helmet (an item id or a #tag)
  .plotRadius(300)                           // players stay within 300 blocks of their own plot (ops exempt)
  .protectPlots(true)                        // other people's plots can't be built on or used (a team shares its plots)
  .gtOres('minecraft:overworld')             // GregTech's ore veins and bedrock fluids of that dimension generate here too
  .weather('clear')                          // 'normal' (the overworld's, default), 'clear', 'rain' or 'storm'
```

- `plotRadius` and `protectPlots` are for dimensions where every player has a plot in the same world. Protection covers the
  area within `plotRadius` of the owner's plot (256 blocks if none is set). Creative ops can build anywhere.
- `gtOres` adds the dimension to the filter of every GregTech ore vein, bedrock fluid and bedrock ore that lists the one you
  name, when the dimension is made (or when the world starts, for dimensions read from files).
- The same keys work in a spec file: `mobs`, `fall_damage`, `oxygen`, `plot_radius`, `protect_plots`, `gt_ores`.

### Weather

A dimension other than the overworld takes the overworld's weather, so a personal dimension can't normally differ. A spec can
give it weather of its own (Continuum runs its own weather step for that dimension, in place of the game's):

```js
event.pdim('mypack:desert_world').weather('clear')      // never rains
event.pdim('mypack:swamp_world').weather('rain')        // always raining (snow in cold biomes, as usual)
event.pdim('mypack:storm_world').weather('storm')       // always a thunderstorm, with lightning
// clear spells of 10 to 60 minutes, rain for 5 to 10 minutes, one rainy spell in four a thunderstorm
event.pdim('mypack:island').weatherCycle(600, 3600, 300, 600, 0.25)
```

In a file: `"weather": "storm"` or `"weather": { "clear_min": 600, "clear_max": 3600, "rain_min": 300, "rain_max": 600,
"storm_chance": 0.25 }` (seconds). Weather turns on and off gradually, as the game's does. A cycle starts again from clear
after a restart. `/weather` and the overworld's own cycle do not touch a dimension that has weather of its own.

### Ordinary dimensions: turn the plots off

By default every player gets a plot of their own in a personal dimension, all in the one world. If you would rather have a
normal shared dimension, turn plots off:

```js
event.pdim('mypack:moon_colony').flat(...).plots(false)             // one body: everybody arrives at the world's origin
event.pdim('mypack:moon_colony').plots(false).spawnAt(120, 70, -40)  // or at a spot of your choosing
```

or in the config (`personalPlots = false`) for every personal dimension at once, or `"plots": false` and `"spawn": [x, y, z]` in
a spec file. With plots off there is no plot allocation, `plotRadius` and `protectPlots` do nothing, and a `plotStructure` is put
down once for the whole dimension (the first time anybody arrives), not once per player. Everything else about a personal
dimension (gravity, day speed, sky, costs, the worldgen, weather and the other rules) still applies. A void world still gets its
obsidian pad at the arrival point; terrain puts the player on the ground there.

