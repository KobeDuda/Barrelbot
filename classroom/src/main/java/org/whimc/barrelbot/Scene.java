package org.whimc.barrelbot;

import java.util.*;
import net.minecraft.nbt.*;
import org.bukkit.*;
import org.bukkit.block.structure.*;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Objective;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

/** A setup snapshot of the blocks, puzzle actors, mount relationships and scoreboard values. */
record Scene(Area area, Structure blocks, List<Actor> actors) {
  record Actor(
      UUID id,
      UUID vehicle,
      Location location,
      EntitySnapshotData snapshot,
      Map<String, Integer> scores) {}

  record EntitySnapshotData(String snbt) {}

  static boolean actor(Entity e) {
    return e.getScoreboardTags().stream()
            .anyMatch(
                t ->
                    t.startsWith("whimc.barrelbot")
                        || t.equals("whimc.instructor")
                        || t.startsWith("whimc.function_display")
                        || t.equals("whimc.dispenser_bot"))
        && !(e instanceof org.bukkit.entity.Player);
  }

  static List<Entity> actors(World w, Area area) {
    Set<Entity> found = new LinkedHashSet<>();
    for (Entity e : area.entities(w))
      if (actor(e)) {
        found.add(e);
        found.addAll(e.getPassengers().stream().filter(Scene::actor).toList());
        if (e.getScoreboardTags().contains("whimc.barrelbot.function"))
          for (Entity child : w.getNearbyEntities(e.getLocation().clone().add(0, 1, 0), .4, 1, .4))
            if (child.getScoreboardTags().stream()
                .anyMatch(
                    t ->
                        t.equals("whimc.function_display")
                            || t.equals("whimc.barrelbot.function_text")
                            || t.equals("whimc.barrelbot.function_interaction"))) found.add(child);
      }
    return List.copyOf(found);
  }

  static Scene capture(World w, Area area) {
    var size = area.hi().minus(area.lo());
    Structure blocks = Bukkit.getStructureManager().createStructure();
    blocks.fill(area.lo().at(w), new BlockVector(size.x() + 1, size.y() + 1, size.z() + 1), false);
    List<Actor> actors = new ArrayList<>();
    for (Entity e : actors(w, area)) {
      var snap = Objects.requireNonNull(e.createSnapshot(), "Cannot snapshot puzzle entity");
      CompoundTag tag;
      try {
        tag = TagParser.parseCompoundFully(snap.getAsString());
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot read entity snapshot", ex);
      }
      tag.remove("Passengers");
      tag.remove("UUID");
      Map<String, Integer> scores = new HashMap<>();
      for (Objective o : Bukkit.getScoreboardManager().getMainScoreboard().getObjectives()) {
        var s = o.getScoreFor(e);
        if (s.isScoreSet()) scores.put(o.getName(), s.getScore());
      }
      actors.add(
          new Actor(
              e.getUniqueId(),
              e.getVehicle() == null ? null : e.getVehicle().getUniqueId(),
              e.getLocation(),
              new EntitySnapshotData(tag.toString()),
              scores));
    }
    return new Scene(area, blocks, actors);
  }

  void place(World w, Pos offset, boolean copy, int newId, int type) {
    Area target = area.shift(offset);
    for (Entity e : actors(w, target)) e.remove();
    blocks.place(
        target.lo().at(w), false, StructureRotation.NONE, Mirror.NONE, 0, 1, new Random(0));
    Map<UUID, Entity> placed = new HashMap<>();
    Map<Integer, Integer> botIds = new HashMap<>();
    var board = Bukkit.getScoreboardManager().getMainScoreboard();
    for (Actor a : actors) {
      Location l = a.location.clone().add(offset.x(), offset.y(), offset.z());
      Entity e = Bukkit.getEntityFactory().createEntitySnapshot(a.snapshot.snbt()).createEntity(l);
      placed.put(a.id, e);
      a.scores.forEach(
          (key, value) -> {
            var o = board.getObjective(key);
            if (o != null)
              o.getScoreFor(e)
                  .setScore(
                      copy && key.equals("whimc.barrelbot.bot_id")
                          ? botIds.computeIfAbsent(value, v -> Puzzle.allocate("bot_id"))
                          : value);
          });
      if (copy) {
        Puzzle.objective("puzzle_id").getScoreFor(e).setScore(newId);
        Puzzle.objective("puzzle_type_id").getScoreFor(e).setScore(type);
        if (e.getScoreboardTags().contains(Puzzle.BOX) && Puzzle.score(e, "bot_id") <= 0)
          Puzzle.objective("bot_id").getScoreFor(e).setScore(Puzzle.allocate("bot_id"));
        CompoundTag data = Puzzle.data(e);
        var bounds = data.getCompound("bounding_box");
        bounds.ifPresent(
            b -> {
              for (String side : List.of("min_", "max_")) {
                b.putInt(side + "x", b.getIntOr(side + "x", 0) + offset.x());
                b.putInt(side + "y", b.getIntOr(side + "y", 0) + offset.y());
                b.putInt(side + "z", b.getIntOr(side + "z", 0) + offset.z());
              }
            });
        data.getList("SpawnPos")
            .ifPresent(
                v -> {
                  if (v.size() == 3)
                    for (int i = 0; i < 3; i++)
                      v.set(
                          i,
                          DoubleTag.valueOf(
                              v.getDoubleOr(i, 0)
                                  + (i == 0 ? offset.x() : i == 1 ? offset.y() : offset.z())));
                });
        Puzzle.data(e, data);
      }
    }
    for (Actor a : actors)
      if (a.vehicle != null && placed.containsKey(a.vehicle))
        if (!placed.get(a.vehicle).addPassenger(placed.get(a.id)))
          throw new IllegalStateException("Could not restore puzzle passenger");
  }
}
