package org.whimc.barrelbot;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class Regions {
  static Map<String, Area> read(World world) {
    var manager =
        WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    Map<String, Area> regions = new TreeMap<>();
    if (manager != null)
      manager
          .getRegions()
          .forEach(
              (name, r) -> {
                if (name.matches("(?:[0-9]+-|puzzle[0-9]+-).+")) {
                  var a = r.getMinimumPoint();
                  var b = r.getMaximumPoint();
                  if (!r.getType().getName().equalsIgnoreCase("cuboid")) return;
                  regions.put(
                      name, new Area(new Pos(a.x(), a.y(), a.z()), new Pos(b.x(), b.y(), b.z())));
                }
              });
    return regions;
  }

  static boolean laneMember(Player p, Puzzle q, boolean adjacent, boolean unique) {
    var manager =
        WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .get(BukkitAdapter.adapt(q.world()));
    if (manager == null) return false;
    var lanes =
        manager.getRegions().values().stream().filter(r -> r.getId().matches("[0-9]+-.+")).toList();
    var current =
        lanes.stream()
            .filter(
                r ->
                    r.getMinimumPoint().x() <= q.anchor().x()
                        && r.getMaximumPoint().x() >= q.anchor().x()
                        && r.getMinimumPoint().z() <= q.anchor().z()
                        && r.getMaximumPoint().z() >= q.anchor().z())
            .toList();
    if (current.size() != 1) return false;
    int own = Integer.parseInt(current.getFirst().getId().split("-")[0]);
    int lane = adjacent ? (own % 2 == 0 ? own - 1 : own + 1) : own;
    return lanes.stream()
        .filter(r -> r.getId().startsWith(lane + "-"))
        .anyMatch(
            r ->
                r.getMembers().contains(p.getUniqueId())
                    && (!unique
                        || r.getMembers().getUniqueIds().size() == 1
                            && r.getMembers().getGroups().isEmpty()));
  }

  static boolean canBuild(Player p, Location l) {
    var q = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    return !q.getApplicableRegions(BukkitAdapter.adapt(l)).getRegions().isEmpty()
        && q.testBuild(BukkitAdapter.adapt(l), WorldGuardPlugin.inst().wrapPlayer(p), Flags.BUILD);
  }
}
