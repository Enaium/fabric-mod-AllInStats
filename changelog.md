# 1.0.0

* Records items (acquired with the source: pickup, craft, container, fishing, and dropped), blocks
  (broken, placed), entities (killed by a player, player killed by an entity) and tools (durability
  used, broken), per world and per player.
* Several counters can be selected at once and are drawn as their own series in the same chart (a
  candle chart needs a single counter). The counters are read again every 5 seconds while the
  interface is open, and the time axis is labelled with dates at every zoom level.
* ImGui interface with categories, searchable object lists, line/area/bar/scatter/candlestick charts,
  minute to year granularity, a free time range with presets, panning/zooming and export to CSV and
  PNG. The object names are shown in the language of the game, the interface itself is available in
  Chinese and English.
* The statistics button of the pause menu opens the interface of this mod instead of the statistics
  screen of the game; the game screen is still reachable from the interface.
* Counters are aggregated into minutes and stored in H2 through Jimmer; the database is opened per
  game directory and separates every world and every player.
* Supported versions: 26.3, 26.2, 26.1, 1.21.11, 1.20.6, 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.15.2,
  1.14.4, 1.12.2, 1.11.2, 1.10.2, 1.9.4, 1.8.9
* The font of the interface is pushed around the windows of the mod (pushFont/popFont) instead of
  replacing the default font of the ImGui context, so the windows of other mods are not affected.
* Text fields work on every version: on the versions that use SDL for their window (26.x) the
  interface module (fabric-gui-imgui 1.2.1) starts and stops the SDL text input with the focus of a
  text field, before that only keys (deleting for example) arrived there.
* The statistics need Java 11 or newer (the H2 that fabric-database-h2 ships), so the game versions
  that default to Java 8 have to be launched with Java 11 or newer.
