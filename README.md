# Phoenix Continuum

The galaxy map, rockets, missions, outposts and personal dimensions of the Phoenix suite. 
Split out of PhoenixCore; 
it
reads and writes Conflux research through PhoenixCore's public API (`net.phoenix.core.api`) 
and its events.

- Mod id `phoenix_continuum`, package `net.phoenix.continuum`
- Requires PhoenixCore (`publishToMavenLocal` there first), GTCEu, and optionally EMI
- Research node ids (`phoenixcore:space_*`) stay in PhoenixCore's namespace so existing saves keep their unlocks
- Data: `data/phoenix_continuum/continuum/{systems,bodies,disciplines}`, the research tree is
  `data/phoenix_continuum/conflux/research/continuum.json`