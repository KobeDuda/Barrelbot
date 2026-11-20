package org.whimc.barrelbot;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesLoadEvent;

final class PuzzleIndex implements Listener {
  record Entry(UUID uuid, Pos anchor, int id, int type, String parent) {}

  final Map<UUID, Map<UUID, Entry>> worlds = new HashMap<>();
  final Set<UUID> scanned = new HashSet<>(), scanning = new HashSet<>();
  private final Classroom app;

  PuzzleIndex(Classroom app) {
    this.app = app;
  }

  void chunk(Chunk c) {
    var entries = worlds.computeIfAbsent(c.getWorld().getUID(), ignored -> new HashMap<>());
    entries
        .values()
        .removeIf(e -> (e.anchor.x() >> 4) == c.getX() && (e.anchor.z() >> 4) == c.getZ());
    for (Entity e : c.getEntities())
      if (e instanceof ItemDisplay box && e.getScoreboardTags().contains(Puzzle.BOX))
        app.supplies.indexLegacy(box);
    for (Entity e : c.getEntities())
      if (e.getScoreboardTags().contains(Puzzle.MANAGER))
        entries.put(
            e.getUniqueId(),
            new Entry(
                e.getUniqueId(),
                Pos.of(e.getLocation()),
                Puzzle.score(e, "puzzle_id"),
                Puzzle.score(e, "puzzle_type_id"),
                e.getPersistentDataContainer()
                    .getOrDefault(
                        app.parent, org.bukkit.persistence.PersistentDataType.STRING, "")));
  }

  @EventHandler
  public void loaded(EntitiesLoadEvent ev) {
    if (scanned.contains(ev.getWorld().getUID()) || scanning.contains(ev.getWorld().getUID()))
      Bukkit.getScheduler()
          .runTask(
              app,
              () -> {
                if (ev.getChunk().isLoaded() && ev.getChunk().isEntitiesLoaded())
                  chunk(ev.getChunk());
              });
  }

  void report(Player p, Puzzle q) {
    var entries = worlds.getOrDefault(q.world().getUID(), Map.of()).values();
    var clashes =
        entries.stream()
            .filter(e -> !e.uuid.equals(q.manager().getUniqueId()) && e.id == q.id())
            .map(Entry::anchor)
            .toList();
    long copies =
        entries.stream()
            .filter(e -> e.type == q.type() && !e.uuid.equals(q.manager().getUniqueId()))
            .count();
    String key =
        q.manager()
            .getPersistentDataContainer()
            .getOrDefault(
                app.identity,
                org.bukkit.persistence.PersistentDataType.STRING,
                q.manager().getUniqueId().toString());
    long direct = entries.stream().filter(e -> e.parent.equals(key)).count();
    p.sendMessage(
        q.world().getName()
            + " • #"
            + q.id()
            + " of type #"
            + q.type()
            + " • "
            + copies
            + " other indexed copies • "
            + (q.id() <= 0
                ? "unassigned ID; preview row repair"
                : clashes.isEmpty() ? "no indexed ID collisions" : "ID collision at " + clashes));
  }

  void survey(Player p, Puzzle q, boolean force) {
    World w = q.world();
    UUID id = w.getUID();
    chunk(q.manager().getChunk());
    if (!force && scanned.contains(id)) {
      report(p, q);
      return;
    }
    if (!scanning.add(id)) return;
    Set<LessonJobs.Key> keys = new LinkedHashSet<>();
    for (Area a : app.regions(w).values()) app.jobs.chunks(keys, a);
    for (Chunk c : w.getLoadedChunks())
      if (c.isEntitiesLoaded()) keys.add(new LessonJobs.Key(c.getX(), c.getZ()));
    var queue = new ArrayDeque<>(keys);
    p.sendMessage("Checking registered classroom regions for copies and ID collisions…");
    Bukkit.getScheduler()
        .runTaskTimer(
            app,
            task -> {
              try {
                for (int i = 0; i < 4 && !queue.isEmpty(); i++) {
                  var k = queue.remove();
                  if (w.isChunkGenerated(k.x(), k.z())) chunk(w.getChunkAt(k.x(), k.z()));
                }
                if (queue.isEmpty()) {
                  scanned.add(id);
                  scanning.remove(id);
                  task.cancel();
                  if (p.isOnline() && q.manager().isValid()) report(p, q);
                }
              } catch (RuntimeException ex) {
                scanning.remove(id);
                task.cancel();
                p.sendMessage("Puzzle survey interrupted: " + ex.getMessage());
              }
            },
            1,
            1);
  }

  boolean duplicate(World w, Puzzle q) {
    return worlds.getOrDefault(w.getUID(), Map.of()).values().stream()
        .anyMatch(e -> e.id == q.id() && !e.uuid.equals(q.manager().getUniqueId()));
  }
}
