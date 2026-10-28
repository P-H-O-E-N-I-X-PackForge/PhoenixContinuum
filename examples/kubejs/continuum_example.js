// Continuum example script. Copy to kubejs/server_scripts/ in your pack (or instance).
// Everything here is optional; delete what you do not need. See KUBEJS.md for the full list.

// 1. Earn research by playing. Bodies and systems can also do this in their JSON with "grants_research";
//    this is the same thing done from a script: surveying the first planet opens the next research.
ContinuumEvents.discovery(event => {
  if (event.subject == 'phoenix_continuum:example_rock' && event.stage == 'surveyed') {
    Continuum.grantResearch(event.team, 'mypack:deep_space')
    console.info(`Team ${event.team} unlocked deep_space`)
  }
})

// 2. Log what missions do.
ContinuumEvents.mission(event => {
  console.info(`[continuum] ${event.kind} ${event.missionType} -> ${event.destination} (${event.probes} probes)`)
})

// 3. The last word on what is visible or reachable: keep the giant out of reach until the pack says so.
ContinuumEvents.visibility(event => {
  if (event.subject == 'phoenix_continuum:example_giant' && event.kind == 'accessible' &&
      !Continuum.hasResearch(event.team, 'mypack:deep_space')) {
    event.deny()
  }
})

// 4. A condition type your definitions can use: { "type": "mypack:night", ... }
Continuum.registerCondition('mypack:night', (params, server, team) => {
  return server.overworld().isNight()
})

// 5. Personal dimension events: the first time a player makes the world of a body, welcome them and give a starter kit.
ContinuumEvents.pdimEnter(event => {
  if (event.created) {
    event.player.tell(`Welcome to ${event.body}. Your own plot is ready.`)
    event.player.give('minecraft:torch 16')
  }
})

ContinuumEvents.pdimLeave(event => {
  console.info(`${event.player.name.string} left their world of ${event.body}`)
})
