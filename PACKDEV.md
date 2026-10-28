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

## Status update (system screen, creator, GregTech versions)

- **Tuner:** the body preview is a live 3D planet (drag to turn, scroll to zoom), drawn with the map's renderer.
- **System screen** (`ContinuumSystemScreen`, from the tuner): tune one system at a time with a 3D view of its star, or start
  a new one. It edits a working copy; Apply sends each changed top-level key as a server patch
  (`config/phoenix_continuum/tuning_systems/<ns>/<name>.json`), Create writes a new system file into the pack folder.
- **New body** keeps its own small screen; it inherits the template you pick.
- **1.20.1 branch:** compiled against GregTech 7.5.3. The GregTech 8 classes live in `src/gtm8/java` (same jar), and
  `GTCEuCompat` picks the matching `GtmBootstrap` at startup. Dev runs: `runServer` (7.5.3), `runServerGtm8`.
- **No PhoenixCore dependency on 1.20.1.** `ContinuumHost` holds the teams, research and discipline-travel hooks; a companion
  installs its own. Ambience sounds are looked up by id (`phoenixcore:ambient/...`) and are silent when missing. Launch
  complexes appear on the Solaris map when Solaris is installed (`TeamSiteData` lives in Solaris).

- **Research is Continuum's own on 1.20.1.** `BuiltinResearch` keeps each team's unlocked ids, flags and discipline in the world,
  and `progressionMode` now defaults to `RESEARCH_GATED`. Grant research with `/continuumadmin research grant|revoke|list|flag|discipline`,
  `ContinuumAPI.grantResearch(...)` or the `Continuum` KubeJS binding (`Continuum.grantResearch(player, "mypack:node")`).
  A body or system gated on `detected_research` / `surveyed_research` appears when its id is unlocked; one without gates uses
  its `initial_stage`. Another mod can still replace all of this with `ContinuumHost.setResearch`.

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


### On 1.20.1 (Forge, KubeJS 6)

The personal dimension API is the same on 1.20.1 and 1.21.1: the same `PdimWorldgen` builder, the same scripts and files, the
same events. What differs underneath:

- **No restart (Infiniverse):** install Infiniverse 1.0.0.5 for 1.20.1 (Modrinth, Forge). Same switch (`runtimePersonalDimensions`).
- **Weather:** 1.20.1 has no mixin in Continuum, so a dimension's own weather is put over the game's after every tick instead of
  replacing its weather step. It behaves the same, with one more packet per tick to the players in the dimension while the
  overworld's weather is different from the dimension's.
- **GregTech ores (`.gtOres`):** written for GregTech 7.5.3 (static registries). With GregTech 8 on 1.20.1 it is skipped and the
  log says so.
- **KubeJS 6:** the scripts are the same. Use `Item.of('minecraft:torch', 16)` for stacks (not `'minecraft:torch 16'`), and item
  tags in a data pack go in `tags/items/` (1.21.1: `tags/item/`). Everything in the Rhino JavaScript of KubeJS 6 that the example scripts use
  (arrow functions, template strings, `const`) is supported.
