package org.whimc.barrelbot;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public class Smoke extends Classroom {
  World w;
  Player p;
  Puzzle q;
  int stage = 0;
  int loops = 0;
  Pos dest;
  int before;
  List<String> messages = new ArrayList<>();

  @Override
  public void onEnable() {
    if (!Boolean.getBoolean("barrelbot.isolatedTest"))
      throw new IllegalStateException("This test requires an isolated Maryville copy");
    super.onEnable();
    Bukkit.getScheduler()
        .runTaskTimer(
            this,
            () -> {
              try {
                tick();
              } catch (Throwable e) {
                e.printStackTrace();
                getLogger().severe("SMOKE_FAILED " + e);
                stage = -1;
              }
            },
            20,
            5);
  }

  void ok(boolean b, String s) {
    if (!b) throw new AssertionError(s);
  }

  void tick() {
    if (stage < 0) return;
    if (++loops > 1600) throw new AssertionError("Timed out at " + stage);
    if (stage == 0) {
      w = Bukkit.getWorlds().getFirst();
      Location l = new Location(w, -937, 4, -686);

      var user =
          new net.minecraft.server.level.ServerPlayer(
                  ((org.bukkit.craftbukkit.CraftWorld) w).getHandle().getServer(),
                  ((org.bukkit.craftbukkit.CraftWorld) w).getHandle(),
                  new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SetupTester"),
                  net.minecraft.server.level.ClientInformation.createDefault())
              .getBukkitEntity();
      p =
          (Player)
              Proxy.newProxyInstance(
                  getClassLoader(),
                  new Class<?>[] {Player.class},
                  (proxy, m, a) ->
                      switch (m.getName()) {
                        case "getWorld" -> w;
                        case "getLocation" -> l;
                        case "getUniqueId" -> user.getUniqueId();
                        case "getName" -> "SetupTester";
                        case "getInventory" -> user.getInventory();
                        case "hasPermission", "isOnline" -> true;
                        case "spawnParticle" -> null;
                        case "sendMessage" -> {
                          messages.add(Arrays.toString(a));
                          yield null;
                        }
                        case "equals" -> a[0] == proxy;
                        case "hashCode" -> 1;
                        default -> throw new UnsupportedOperationException(m.getName());
                      });
      var row = regions(w).get("puzzle3-loops");
      ok(row != null, "lookup lesson");
      for (int x = -950 >> 4; x <= -923 >> 4; x++)
        for (int z = -700 >> 4; z <= -670 >> 4; z++) w.getChunkAt(x, z).getEntities();
      for (var e : w.getEntities())
        if (e.getScoreboardTags().contains(Puzzle.MANAGER)
            && row.contains(Pos.of(e.getLocation()))
            && e.getLocation().getX() < -923) {
          q = Puzzle.read(e);
          break;
        }
      ok(q != null, "template manager in loops row");
      selections.put(p.getUniqueId(), q.manager().getUniqueId());
      anchors.put(p.getUniqueId(), q.manager().getLocation());
      getLogger().info("SMOKE template " + q + " boxes=" + q.boxes().size());
      var baseline = Supplies.baseline(q.boxes().getFirst());
      ok(
          Arrays.stream(baseline)
              .filter(Objects::nonNull)
              .anyMatch(i -> Puzzle.itemData(i).contains("instruction")),
          "legacy instruction import");
      jobs.command(p, new String[] {"preview", "repair"});
      stage++;
      return;
    }
    if (jobs.current != null && !jobs.current.ready) return;
    if (stage == 1) {
      ok(jobs.current != null, "repair preview: " + messages);
      getLogger().info("SMOKE repair targets " + jobs.current.targets.size());
      ok(jobs.current.targets.size() == 40, "40 lanes");
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 2) {
      ok(
          jobs.current == null
              && jobs.undo.containsKey(p.getUniqueId())
              && jobs.undo.get(p.getUniqueId()).size() == 40,
          "repair completed: " + messages.getLast());
      q = selected(p);
      ok(q.id() > 0, "new ID");
      var b = q.boxes().getFirst();
      var items = Supplies.baseline(b);
      var extra =
          Arrays.stream(items)
              .filter(i -> i != null && Puzzle.itemData(i).contains("instruction"))
              .findFirst()
              .orElseThrow()
              .clone();
      extra.setAmount(2);
      p.getInventory().setItemInMainHand(extra);
      before = count(b);
      jobs.command(p, new String[] {"preview", "add"});
      stage++;
      return;
    }
    if (stage == 3) {
      ok(jobs.current != null, "add preview " + messages);
      jobs.command(p, new String[] {"test"});
      stage++;
      return;
    }
    if (stage == 4) {
      ok(jobs.current.done.size() == 1, "lane2 only");
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 5) {
      q = selected(p);
      ok(count(q.boxes().getFirst()) == before + 2, "persistent addition");
      dest = q.anchor().plus(new Pos(0, 80, 0));
      jobs.command(
          p, new String[] {"preview", "paste", "" + dest.x(), "" + dest.y(), "" + dest.z()});
      stage++;
      return;
    }
    if (stage == 6) {
      ok(jobs.current != null, "custom preview " + messages);
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 7) {
      dest.at(w).getChunk().getEntities();
      var hits =
          w.getNearbyEntities(dest.at(w).add(.5, .5, .5), 1, 1, 1).stream()
              .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
              .toList();
      ok(hits.size() == 1, "clone manager at destination: " + messages.getLast());
      Puzzle copy = Puzzle.read(hits.getFirst());
      ok(copy.type() == q.type() && copy.id() != q.id(), "independent ID");
      ok(copy.boxes().size() == q.boxes().size(), "boxes cloned");
      var box = copy.boxes().getFirst();
      var c = Puzzle.container(box);
      c.getInventory().clear();
      supplies.recall(copy, List.of(box));
      ok(
          Arrays.stream(c.getInventory().getContents())
                  .filter(Objects::nonNull)
                  .mapToInt(ItemStack::getAmount)
                  .sum()
              == count(box),
          "exact recall");
      jobs.command(
          p, new String[] {"preview", "paste", "" + dest.x(), "" + dest.y(), "" + dest.z()});
      stage++;
      return;
    }
    if (stage == 8) {
      ok(jobs.current != null, "overwrite preview");
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 9) {
      dest.at(w).getChunk().getEntities();
      var e =
          w.getNearbyEntities(dest.at(w).add(.5, .5, .5), 1, 1, 1).stream()
              .filter(v -> v.getScoreboardTags().contains(Puzzle.MANAGER))
              .toList();
      ok(e.size() == 1, "one manager after overwrite");
      jobs.command(p, new String[] {"undo"});
      jobs.command(p, new String[] {"confirm-undo"});
      stage++;
      return;
    }
    if (stage == 10) {
      dest.at(w).getChunk().getEntities();
      Puzzle copied =
          Puzzle.read(
              w.getNearbyEntities(dest.at(w).add(.5, .5, .5), 1, 1, 1).stream()
                  .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
                  .findFirst()
                  .orElseThrow());
      var bot =
          copied.area().entities(w).stream()
              .filter(e -> e.getScoreboardTags().contains("whimc.barrelbot"))
              .findFirst()
              .orElseThrow();
      var home = bot.getLocation().clone();
      var uuid = bot.getUniqueId();
      var inv = ((org.bukkit.block.Container) home.getBlock().getState()).getInventory();
      var move =
          Arrays.stream(Supplies.baseline(copied.boxes().getFirst()))
              .filter(
                  i ->
                      i != null
                          && Puzzle.itemData(i)
                              .getStringOr("instruction", "")
                              .equals("move_forward"))
              .findFirst()
              .orElseThrow()
              .clone();
      inv.clear();
      move.setAmount(1);
      inv.setItem(0, move);
      function(bot, "dispenser_bot/convert");
      function(copied.manager(), "manager/start_execution");
      function(copied.manager(), "manager/step");
      ok(
          bot.isValid() && bot.getScoreboardTags().contains("whimc.dispenser_bot"),
          "dispenser survives start");
      ok(bot.getPassengers().size() == 1, "bot retained marker after moving");
      function(copied.manager(), "manager/stop_execution");
      ok(bot.isValid() && Pos.of(bot.getLocation()).equals(Pos.of(home)), "bot returns to spawn");
      ok(bot.getLocation().getBlock().getType() == Material.BARREL, "barrel remains after stop");
      jobs.command(p, new String[] {"preview", "reset"});
      stage++;
      return;
    }
    if (stage == 11) {
      ok(jobs.current != null, "row reset preview");
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 12) {
      roleChecks();
      q = selected(p);
      ok(count(q.boxes().getFirst()) == before + 2, "addition survives reset");
      jobs.command(
          p,
          new String[] {
            "preview",
            "cascade",
            "" + dest.x(),
            "" + (dest.y() + 20),
            "" + dest.z(),
            "2",
            "1",
            "27",
            "0"
          });
      stage++;
      return;
    }
    if (stage == 13) {
      ok(jobs.current != null && jobs.current.targets.size() == 2, "cascade preview");
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 14) {
      ok(jobs.undo.get(p.getUniqueId()).size() == 2, "two cascade snapshots");
      jobs.command(p, new String[] {"undo"});
      jobs.command(p, new String[] {"confirm-undo"});
      // Read all template anchors and report unmapped or ambiguous lesson bands.
      for (int x = -950 >> 4; x <= -923 >> 4; x++)
        for (int z = -951 >> 4; z <= -645 >> 4; z++) w.getChunkAt(x, z).getEntities();
      for (var entry : regions(w).entrySet())
        if (entry.getKey().startsWith("puzzle")) {
          var hits =
              w.getEntities().stream()
                  .filter(
                      e ->
                          e.getScoreboardTags().contains(Puzzle.MANAGER)
                              && e.getLocation().getX() >= -949
                              && e.getLocation().getX() < -922
                              && entry.getValue().contains(Pos.of(e.getLocation())))
                  .count();
          getLogger()
              .info(
                  "BASELINE "
                      + entry.getKey()
                      + " template buttons "
                      + hits
                      + " "
                      + w.getEntities().stream()
                          .filter(
                              e ->
                                  e.getScoreboardTags().contains(Puzzle.MANAGER)
                                      && e.getLocation().getX() >= -949
                                      && e.getLocation().getX() < -922
                                      && entry.getValue().contains(Pos.of(e.getLocation())))
                          .map(Puzzle::read)
                          .toList());
        }
      jobs.command(p, new String[] {"preview", "import"});
      stage++;
      return;
    }
    if (stage == 15) {
      ok(
          jobs.current != null && !jobs.current.targets.isEmpty(),
          "whole-world import preview " + messages.getLast());
      getLogger()
          .info(
              "IMPORT TARGETS "
                  + jobs.current.targets.size()
                  + " CONFLICTS "
                  + jobs.current.conflicts.size());
      jobs.command(p, new String[] {"apply"});
      stage++;
      return;
    }
    if (stage == 16) {
      ok(
          jobs.undo.get(p.getUniqueId()).size() > 40,
          "world import finished: " + messages.getLast());
      var author = Bukkit.getPlayerExact("BuilderTester");
      author.teleport(new Location(w, -937, 7, -706));
      author.setGameMode(GameMode.CREATIVE);
      placeFeature(author, "puzzle_button", -937, 7, -708);
      stage++;
      return;
    }
    if (stage == 17) {
      var author = Bukkit.getPlayerExact("BuilderTester");
      var created = selected(author);
      ok(
          created.anchor().equals(new Pos(-937, 7, -708))
              && role(created, owner).equals(author.getUniqueId().toString()),
          "creative placement assigns builder");
      placeFeature(author, "instructor", -936, 7, -708);
      stage++;
      return;
    }
    if (stage == 18) {
      var author = Bukkit.getPlayerExact("BuilderTester");
      var puzzle = selected(author);
      ok(puzzle.boxes().size() == 1, "creative source box placement");
      var b = puzzle.boxes().getFirst();
      function(author, "instructions/move_forward/give");
      ItemStack sample =
          Arrays.stream(author.getInventory().getContents())
              .filter(i -> i != null && Puzzle.itemData(i).contains("instruction"))
              .findFirst()
              .orElseThrow();
      Puzzle.container(b).getInventory().setItem(0, sample.clone());
      onCommand(author, getCommand("bb"), "bb", new String[] {"lock"});
      ok(!supplies.epoch(b).isEmpty(), "new builder can lock source inventory");
      var loan = Puzzle.container(b).getInventory().getItem(0).clone();
      Puzzle.container(b).getInventory().clear();
      author.getInventory().setItem(0, loan);
      var destination = Bukkit.getWorlds().stream().filter(v -> v != w).findFirst().orElseThrow();
      var travel =
          new org.bukkit.event.player.PlayerTeleportEvent(
              author, author.getLocation(), new Location(destination, 0, 80, 0));
      leaveWorld(travel);
      ok(
          !travel.isCancelled() && author.getInventory().getItem(0) == null,
          "return before changing worlds");
      ok(
          Puzzle.container(b).getInventory().getItem(0) != null,
          "return stayed within original world");
      getLogger().info("SMOKE_PASSED");
      stage = -1;
    }
  }

  Player account(String name) {
    var level = ((org.bukkit.craftbukkit.CraftWorld) w).getHandle();
    var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
    var sp =
        new net.minecraft.server.level.ServerPlayer(
            level.getServer(), level, profile, cookie.clientInformation());
    var conn =
        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(conn);
    level.getServer().getPlayerList().placeNewPlayer(conn, sp, cookie);
    return sp.getBukkitEntity();
  }

  void roleChecks() {
    var box = q.boxes().getFirst();
    var container = Puzzle.container(box).getInventory();
    var sample =
        Arrays.stream(Supplies.baseline(box))
            .filter(i -> i != null && Puzzle.itemData(i).contains("instruction"))
            .findFirst()
            .orElseThrow()
            .clone();
    var peer = account("PairTester");
    var outsider = account("OtherLane");
    var key = org.bukkit.persistence.PersistentDataType.STRING;
    var author = account("BuilderTester");
    var regions =
        com.sk89q.worldguard.WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(w));
    regions.getRegion("1-template").getMembers().addPlayer(author.getUniqueId());
    q.manager().getPersistentDataContainer().set(owner, key, author.getUniqueId().toString());
    author.teleport(q.anchor().at(w).add(0, 1, 0));
    author.setGameMode(GameMode.CREATIVE);
    selections.put(author.getUniqueId(), q.manager().getUniqueId());
    var initial = supplies.epoch(box);
    onCommand(author, getCommand("bb"), "bb", new String[] {"lock"});
    ok(!initial.equals(supplies.epoch(box)), "builder can lock inventory in own WorldGuard lane");
    author.teleport(w.getSpawnLocation());
    q.manager().getPersistentDataContainer().set(partner, key, peer.getUniqueId().toString());
    for (Player student : List.of(peer, outsider))
      selections.put(student.getUniqueId(), q.manager().getUniqueId());
    String g = supplies.epoch(box);
    container.clear();
    peer.getInventory().setItem(0, sample.clone());
    onCommand(outsider, getCommand("bb"), "bb", new String[] {"return"});
    ok(container.isEmpty(), "outsider cannot recall");
    onCommand(peer, getCommand("bb"), "bb", new String[] {"lock"});
    ok(supplies.epoch(box).equals(g), "partner cannot change baseline");
    onCommand(peer, getCommand("bb"), "bb", new String[] {"return"});
    ok(!supplies.epoch(box).equals(g) && !container.isEmpty(), "partner can recall");
    ok(peer.getInventory().getItem(0) == null, "partner copy returned");
    peer.getInventory().setItem(0, sample.clone());
    supplies.check(peer);
    ok(peer.getInventory().getItem(0) == null, "stale copy retired");
    var loan =
        Arrays.stream(container.getContents())
            .filter(i -> i != null && Puzzle.itemData(i).contains("instruction"))
            .findFirst()
            .orElseThrow();
    int old = count(box);
    container.removeItem(loan.clone());
    peer.getInventory().setItem(0, loan.clone());
    supplies.check(peer);
    ok(peer.getInventory().getItem(0) == null, "cross-boundary return");
    ok(
        Arrays.stream(container.getContents())
                .filter(Objects::nonNull)
                .mapToInt(ItemStack::getAmount)
                .sum()
            == old,
        "quantity conserved");
    onCommand(peer, getCommand("bb"), "bb", new String[] {"preview", "overwrite"});
    ok(jobs.current == null, "learners cannot clone");
    function(peer, "instructions/loop/give_infinite");
    ok(
        Arrays.stream(peer.getInventory().getContents())
            .filter(Objects::nonNull)
            .anyMatch(i -> Puzzle.itemData(i).getIntOr("cycles", 0) == -1),
        "instruction catalog");
    dispatch(peer, "loot give @s loot whimc:edit_wand");
    ok(
        Arrays.stream(peer.getInventory().getContents())
            .filter(Objects::nonNull)
            .anyMatch(this::isWand),
        "wand loot components");
  }

  void placeFeature(Player author, String key, int x, int y, int z) {
    var at = w.getBlockAt(x, y, z);
    at.setType(Material.AIR);
    w.getBlockAt(x, y - 1, z).setType(Material.STONE);
    author.getInventory().clear();
    author.getInventory().setHeldItemSlot(0);
    onCommand(author, getCommand("bb"), "bb", new String[] {"give", key});
    var sp = ((org.bukkit.craftbukkit.entity.CraftPlayer) author).getHandle();
    var level = ((org.bukkit.craftbukkit.CraftWorld) w).getHandle();
    var result =
        sp.gameMode.useItemOn(
            sp,
            level,
            sp.getMainHandItem(),
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(x + .5, y, z + .5),
                net.minecraft.core.Direction.UP,
                new net.minecraft.core.BlockPos(x, y - 1, z),
                false));
    ok(
        result.consumesAction(),
        "Minecraft placement interaction "
            + key
            + " at "
            + x
            + " "
            + y
            + " "
            + z
            + " result "
            + result);
  }

  int count(ItemDisplay b) {
    return Arrays.stream(Supplies.baseline(b))
        .filter(Objects::nonNull)
        .mapToInt(ItemStack::getAmount)
        .sum();
  }
}
