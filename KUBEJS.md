# Continuum and KubeJS

Continuum registers a KubeJS plugin (`kubejs.plugins.txt`) that is only loaded when KubeJS is installed. Everything
here is **server scripts** (`kubejs/server_scripts`).

Content needs no plugin at all: put bodies and systems in `kubejs/data/<namespace>/continuum/bodies/*.json` and
`.../systems/*.json`, exactly like a data pack. `/continuumadmin reload` loads them, `/continuumadmin validate` lists
problems.

## The `Continuum` global

Teams are given as a player, a UUID or a UUID string. Ids are strings (`"phoenix_continuum:example_rock"`; a bare name
means the `phoenix_continuum` namespace).

```js
Continuum.bodies()                       // all bodies
Continuum.body('example_rock')           // one body, or null
Continuum.stage(player, 'example_rock')  // 'unknown' | 'detected' | 'surveyed'
Continuum.isVisible(player, 'example_rock')
Continuum.isAccessible(player, 'example_rock')

Continuum.raise(player, 'example_rock', 'surveyed')  // never lowers; true if it changed
Continuum.setVisible(player, 'example_giant', false) // per-team override (also for a whole system id)
Continuum.clearOverride(player, 'example_giant')

Continuum.grantResearch(player, 'mypack:node')   // Continuum's own research; true if it was new
Continuum.revokeResearch(player, 'mypack:node')
Continuum.hasResearch(player, 'mypack:node')
Continuum.setResearchFlag(player, 'flag', true)
Continuum.setDiscipline(player, 'name')          // '' clears it

Continuum.reload()                       // reload Continuum data, returns the report
Continuum.report()                       // last load report (issues, errors(), warnings())
```

## Your own condition types

Visibility and accessibility rules in a definition can use any condition type, including ones you define:

```js
// startup or server script
Continuum.registerCondition('mypack:has_gamestage', (params, server, team) => {
  return /* your check, using params.get('stage').getAsString() */ true
})
```

```json
{ "visibility": { "visible_if": { "type": "mypack:has_gamestage", "stage": "orbit" } } }
```

Built-in condition types: `always`, `never`, `research` (`node`), `flag` (`name`), `discipline` (`is`), `stage`
(`body` or `system`, `at_least`), `visible` (`body`), `advancement` (`id`), and the combinators `all_of`, `any_of`, `not`.

## Events

```js
ContinuumEvents.visibility(event => {
  // the last word, after rules and overrides. event.kind is 'visible' or 'accessible'
  if (event.subject == 'phoenix_continuum:example_giant' && event.kind == 'accessible') event.deny()
})

ContinuumEvents.discovery(event => {
  // event.team, event.subject, event.stage ('detected' | 'surveyed')
})

ContinuumEvents.mission(event => {
  // event.kind ('launched' | 'succeeded' | 'failed'), event.team, event.destination, event.missionType, event.probes
})
```

## Earning research without a script

A body or system can give research ids to a team once the team reaches a stage of it:

```json
{ "grants_research": { "detected": ["mypack:sensors"], "surveyed": ["mypack:landing"] } }
```

A plain list (`"grants_research": ["mypack:landing"]`) means "when surveyed". Granting can open other gates, which can grant more.
This is for Continuum's own research; a mod that replaces it (PhoenixCore's Conflux) decides for itself.

## Example script

`examples/kubejs/continuum_example.js` in the Continuum repository shows a discovery handler, a mission log, the visibility
event and a custom condition. Copy it into `kubejs/server_scripts/`.
