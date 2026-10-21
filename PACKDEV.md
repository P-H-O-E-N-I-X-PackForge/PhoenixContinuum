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
