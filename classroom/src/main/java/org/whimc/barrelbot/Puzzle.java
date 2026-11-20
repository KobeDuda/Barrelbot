package org.whimc.barrelbot;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;

record Pos(int x, int y, int z) {
  static Pos of(Location l) {
    return new Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
  }

  Location at(World w) {
    return new Location(w, x, y, z);
  }

  Pos plus(Pos d) {
    return new Pos(x + d.x, y + d.y, z + d.z);
  }

  Pos minus(Pos p) {
    return new Pos(x - p.x, y - p.y, z - p.z);
  }

  public String toString() {
    return x + " " + y + " " + z;
  }
}

record Area(Pos lo, Pos hi) {
  boolean contains(Pos p) {
    return horizontal(p) && p.y() >= lo.y() && p.y() <= hi.y();
  }

  boolean horizontal(Pos p) {
    return p.x() >= lo.x() && p.x() <= hi.x() && p.z() >= lo.z() && p.z() <= hi.z();
  }

  Area shift(Pos d) {
    return new Area(lo.plus(d), hi.plus(d));
  }

  boolean overlaps(Area b) {
    return lo.x() <= b.hi.x()
        && hi.x() >= b.lo.x()
        && lo.y() <= b.hi.y()
        && hi.y() >= b.lo.y()
        && lo.z() <= b.hi.z()
        && hi.z() >= b.lo.z();
  }

  long volume() {
    return (1L + hi.x() - lo.x()) * (1L + hi.y() - lo.y()) * (1L + hi.z() - lo.z());
  }

  List<Entity> entities(World w) {
    List<Entity> found = new ArrayList<>();
    for (int x = lo.x() >> 4; x <= hi.x() >> 4; x++)
      for (int z = lo.z() >> 4; z <= hi.z() >> 4; z++)
        for (Entity e : w.getChunkAt(x, z).getEntities())
          if (contains(Pos.of(e.getLocation()))) found.add(e);
    return found;
  }
}

record Puzzle(Entity manager, Pos anchor, Area area, int id, int type) {
  static final String MANAGER = "whimc.barrelbot.puzzle_manager", BOX = "whimc.instructor";

  static Objective objective(String field) {
    Objective o =
        Bukkit.getScoreboardManager().getMainScoreboard().getObjective("whimc.barrelbot." + field);
    if (o == null) throw new IllegalStateException("Load the Barrelbot datapack first.");
    return o;
  }

  static int score(Entity e, String field) {
    return objective(field).getScoreFor(e).getScore();
  }

  static int allocate(String field) {
    Score s = objective(field).getScore("$global");
    int next = Math.addExact(s.getScore(), 1);
    s.setScore(next);
    return next;
  }

  static CompoundTag data(Entity e) {
    CustomData data = ((CraftEntity) e).getHandle().get(DataComponents.CUSTOM_DATA);
    return data == null ? new CompoundTag() : data.copyTag();
  }

  static void data(Entity e, CompoundTag d) {
    ((CraftEntity) e).getHandle().setComponent(DataComponents.CUSTOM_DATA, CustomData.of(d));
  }

  static Puzzle read(Entity e) {
    Entity storage =
        e.getPassengers().stream()
            .filter(p -> p.getScoreboardTags().contains("whimc.barrelbot.puzzle_storage"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing puzzle storage at " + Pos.of(e.getLocation())));
    CompoundTag d = data(storage).getCompoundOrEmpty("bounding_box");
    int[] a = new int[3], b = new int[3];
    int n = 0;
    for (String axis : List.of("x", "y", "z")) {
      int v =
          d.getInt("min_" + axis)
              .orElseThrow(() -> new IllegalStateException("Missing puzzle boundary"));
      int u =
          d.getInt("max_" + axis)
              .orElseThrow(() -> new IllegalStateException("Missing puzzle boundary"));
      a[n] = Math.min(u, v);
      b[n++] = Math.max(u, v);
    }
    Area area = new Area(new Pos(a[0], a[1], a[2]), new Pos(b[0], b[1], b[2]));
    Pos anchor = Pos.of(e.getLocation());
    if (!area.contains(anchor) || area.volume() > 32768)
      throw new IllegalStateException("Invalid puzzle boundary at " + anchor);
    return new Puzzle(e, anchor, area, score(e, "puzzle_id"), score(e, "puzzle_type_id"));
  }

  World world() {
    return manager.getWorld();
  }

  List<ItemDisplay> boxes() {
    return area.entities(world()).stream()
        .filter(e -> e instanceof ItemDisplay && e.getScoreboardTags().contains(BOX))
        .map(e -> (ItemDisplay) e)
        .toList();
  }

  static Container container(Entity box) {
    if (box.getLocation().getBlock().getState() instanceof Container c) return c;
    throw new IllegalStateException("Missing source container at " + Pos.of(box.getLocation()));
  }

  static CompoundTag itemData(ItemStack item) {
    if (item == null || item.isEmpty()) return new CompoundTag();
    CustomData data = CraftItemStack.asNMSCopy(item).get(DataComponents.CUSTOM_DATA);
    return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("barrelbot");
  }

  static ItemStack bind(
      ItemStack item, ItemDisplay box, int puzzle, int bot, String home, String generation) {
    var stack = CraftItemStack.asNMSCopy(item);
    CustomData.update(
        DataComponents.CUSTOM_DATA,
        stack,
        tag -> {
          CompoundTag d = tag.getCompoundOrEmpty("barrelbot");
          d.putInt("puzzle_id", puzzle);
          d.putInt("instructor_id", bot);
          d.putBoolean("puzzle_item", true);
          d.putString("classroom_home", home);
          d.putString("classroom_generation", generation);
          tag.put("barrelbot", d);
        });
    return CraftItemStack.asBukkitCopy(stack);
  }
}
