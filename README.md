# AllInStats

![](https://img.cdn1.vip/i/6ab933d35b0ab_1790522323.webp)

A Fabric mod that records what actually happens in a world and shows it as charts.

The statistics of the game are not detailed enough: they cannot answer "which items did I pick up
yesterday", "how much of it came from fishing", "what did the farm kill", or "how many pickaxes did I
wear out this week". AllInStats records every counter per world and per player, keeps a minute
resolution history of it and draws it in an ImGui interface with plots, zooming and export.

## Features

Recorded counters (per world and per player):

| Category | Counters |
|---|---|
| Items | acquired (with the source: picked up, crafted, taken out of a container, fished), dropped |
| Blocks | broken, placed |
| Entities | killed by a player, player killed by an entity |
| Tools | durability used, tools broken |

The interface (the statistics button of the pause menu opens it, and it can open the vanilla statistics screen again):

* big categories (Items, Blocks, Entities, Tools, Fishing) and the concrete objects of each of them
  (stone, dirt, pig, ...) with their total, searchable and sortable
* several objects at once (up to 8): the counters of every selected object are drawn as their own
  series in the same chart, the chart type of a single object is kept (candles need a single object)
* chart types: line, area, bar, scatter and candlestick
* granularity: minute, hour, day, week, month, year
* time range: free start and end (`yyyy-MM-dd HH:mm`, the field shows the format and marks what it
  cannot read), buttons that move the range by an hour or a day, plus the presets last hour/day/
  7 days/30 days/year/all
* series per counter type and, for a single item counter, per source
* zooming with the wheel, panning by dragging, fitting with a double click and a reset button; the
  time axis stays labelled with dates at every zoom level and the cursor readout shows the time
* the counters are read again every 2 seconds while the interface is open (can be switched off) and
  opening the interface writes and reads them right away, so what was just recorded shows up
  without reopening the screen
* export of the visible range as CSV (one column per series) or PNG
* the names of the objects are shown in the language of the game, the interface itself is available
  in Chinese and English; it uses a font of the system that can render the language, and that font is
  only used for the windows of this mod, the windows of other mods keep the font of the game

## Installation

The mod is required on the server side (it records the world). In single player the integrated server
is the recording side, so nothing else is needed.

It needs:

* Fabric Loader
* [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
* [fabric-gui-imgui](https://modrinth.com/mod/fabric-gui-imgui) (the ImGui and ImPlot interface)
* [fabric-orm-jimmer](https://modrinth.com/mod/fabric-orm-jimmer) (Jimmer, the ORM)
* [fabric-database-h2](https://modrinth.com/mod/fabric-database-h2) (H2, the database)

In multiplayer the database of the running server is the one that is recorded; the interface of a
client can only show the database of its own game directory.

## Data

* Database: `<game directory>/allinstats/stats.mv.db` (H2, one file per installation, every save and
  every player is a row of its own)
* Export: `<game directory>/allinstats/exports`
* Counters are collected in memory and written every 2 seconds, the game never waits for the
  database. Opening the interface writes what is still in memory first, so it is never older than
  that. Killing the game can lose the counters of the last few seconds.

```
stat_world   (id, world_id, name, created_time, modified_time)
stat_player  (id, world_id, uuid, name, created_time, modified_time)
stat_counter (id, world_id, player_uuid, type, stat_key, source, bucket, amount)
```

`bucket` is the epoch millisecond of the minute the counters belong to, `type` is one of the counter
types, `stat_key` is the registry id of the object (`minecraft:stone`) and `source` is where an item
came from. Every granularity of the interface is aggregated from these minutes.

## Known limitations

* Everything is recorded by the server side, so the counters exist on the server (in single player the
  integrated server). A client in a multiplayer world can only read the database of its own game
  directory, nothing is transferred over the network.
* The image export draws the chart with the toolkit of the JVM itself (without a window system, which
  is forced on macOS because the game owns the main thread there). It is a rendering of the same data
  and series, not a screenshot of the ImGui window.
* A candle chart is built from the minute counters of the range, so it reads at most the last 120 days
  of the selected range.
* On 1.12.2 and older the fishing loot is taken from the loot roll of the bobber instead of a
  criterion trigger, and the item is counted once more when it is picked up (as in every version).

## Supported versions

26.3, 26.2, 26.1, 1.21.11, 1.20.6, 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.15.2, 1.14.4, 1.12.2, 1.11.2,
1.10.2, 1.9.4, 1.8.9

## License

MIT
