package org.whimc.barrelbot;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class Classroom extends JavaPlugin implements Listener {
  final Map<UUID, Location> anchors = new HashMap<>();
  final Map<UUID, UUID> selections = new HashMap<>();
  final Map<String, Area> maryville = new TreeMap<>();
  PuzzleIndex index;
  Supplies supplies;
  LessonJobs jobs;
  NamespacedKey owner, partner, identity, parent;
  static final List<String> TOOLS =
      List.of("puzzle_button", "barrelbot", "dispenser_bot", "instructor", "function");
  static final List<String> INSTRUCTIONS =
      List.of(
          "move_forward",
          "move_down",
          "climb",
          "turn_left",
          "turn_right",
          "end_code_block",
          "counter_loop",
          "infinite_loop",
          "if_block",
          "if_not_block",
          "grab",
          "grab_cake",
          "place",
          "place_block",
          "mine_ice",
          "death_ray");

  @Override
  public void onEnable() {
    identity = new NamespacedKey(this, "instance");
    parent = new NamespacedKey(this, "parent");
    index = new PuzzleIndex(this);
    Bukkit.getPluginManager().registerEvents(index, this);
    supplies = new Supplies(this);
    jobs = new LessonJobs(this);
    owner = new NamespacedKey(this, "builder");
    partner = new NamespacedKey(this, "partner");
    try (var r = new BufferedReader(new InputStreamReader(getResource("maryville.tsv")))) {
      r.lines()
          .forEach(
              line -> {
                String[] v = line.split("\t");
                maryville.put(
                    v[0],
                    new Area(
                        new Pos(
                            Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])),
                        new Pos(
                            Integer.parseInt(v[4]),
                            Integer.parseInt(v[5]),
                            Integer.parseInt(v[6]))));
              });
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    Bukkit.getPluginManager().registerEvents(this, this);
    Bukkit.getScheduler()
        .runTaskTimer(
            this,
            () -> {
              for (Player p : Bukkit.getOnlinePlayers()) {
                supplies.check(p);
                if (staff(p)) p.addScoreboardTag("whimc.classroom_staff");
                else p.removeScoreboardTag("whimc.classroom_staff");
              }
              var job = jobs.current;
              if (job != null && job.ready) {
                var operator = Bukkit.getPlayer(job.operator);
                if (operator != null && operator.getWorld() == job.world) {
                  if (operator.getLocation().distance(job.source.anchor().at(job.world)) < 64)
                    outline(operator, job.source.area(), Color.YELLOW);
                  for (var target : job.targets)
                    if (!job.done.contains(target)
                        && operator.getLocation().distance(target.anchor().at(job.world)) < 48)
                      outline(operator, target.area(), Color.AQUA);
                }
              }
            },
            10,
            10);
  }

  @Override
  public void onDisable() {
    if (jobs != null && jobs.current != null) jobs.release(jobs.current);
    for (Player p : Bukkit.getOnlinePlayers()) p.removeScoreboardTag("whimc.classroom_staff");
  }

  @EventHandler
  public void quit(PlayerQuitEvent ev) {
    UUID id = ev.getPlayer().getUniqueId();
    if (jobs.current != null && jobs.current.operator.equals(id)) jobs.release(jobs.current);
    selections.remove(id);
    anchors.remove(id);
    corners.remove(id);
  }

  boolean wg() {
    return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
  }

  Map<String, Area> regions(World w) {
    if (wg()) {
      var live = Regions.read(w);
      if (!live.isEmpty()) return live;
    }
    return w.getName().toLowerCase(Locale.ROOT).contains("maryville")
            || getConfig().getStringList("maryville-worlds").contains(w.getName())
        ? maryville
        : Map.of();
  }

  boolean staff(Player p) {
    return p.hasPermission("barrelbot.instructor");
  }

  boolean creative(Player p, Location at) {
    return p.getGameMode() == GameMode.CREATIVE && wg() && Regions.canBuild(p, at);
  }

  String role(Puzzle q, NamespacedKey key) {
    return q.manager()
        .getPersistentDataContainer()
        .getOrDefault(key, PersistentDataType.STRING, "");
  }

  boolean builder(Player p, Puzzle q) {
    return staff(p)
        || creative(p, q.anchor().at(q.world()))
            && (p.getUniqueId().toString().equals(role(q, owner))
                || role(q, owner).isEmpty() && Regions.laneMember(p, q, false, true));
  }

  void require(boolean ok, String msg) {
    if (!ok) throw new IllegalStateException(msg);
  }

  void function(Entity e, String fn) {
    dispatch(e, "function whimc:barrelbot/" + fn);
  }

  void dispatch(Entity e, String cmd) {
    Bukkit.dispatchCommand(
        Bukkit.getConsoleSender(),
        "minecraft:execute in "
            + e.getWorld().getKey()
            + " as "
            + e.getUniqueId()
            + " at @s run "
            + cmd);
  }

  Puzzle selected(Player p) {
    UUID id = selections.get(p.getUniqueId());
    Location at = anchors.get(p.getUniqueId());
    if (id != null && at != null && at.getWorld() == p.getWorld()) at.getChunk().getEntities();
    Entity e = id == null ? null : p.getWorld().getEntity(id);
    require(e != null && e.isValid(), "Look at a puzzle start button and use /bb select.");
    return Puzzle.read(e);
  }

  Puzzle select(Player p, Block b) {
    require(b != null, "Look at a start button or source box.");
    Pos pos = Pos.of(b.getLocation());
    List<Entity> exact =
        b.getWorld().getNearbyEntities(b.getLocation().add(.5, .5, .5), 1, 1, 1).stream()
            .filter(
                e ->
                    e.getScoreboardTags().contains(Puzzle.MANAGER)
                        && Pos.of(e.getLocation()).equals(pos))
            .toList();
    if (exact.size() > 1)
      throw new IllegalStateException(
          "Collision at "
              + pos
              + " • puzzle IDs "
              + exact.stream().map(e -> "" + Puzzle.score(e, "puzzle_id")).toList()
              + ". Use a template overwrite during setup.");
    List<Puzzle> picks = exact.stream().map(Puzzle::read).toList();
    if (picks.isEmpty()) {
      picks =
          b.getWorld().getNearbyEntities(b.getLocation(), 64, 64, 64).stream()
              .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
              .map(Puzzle::read)
              .filter(q -> q.area().contains(pos))
              .toList();
    }
    require(picks.size() == 1, "Expected one puzzle at " + pos + "; found " + picks.size() + ".");
    Puzzle q = picks.getFirst();
    selections.put(p.getUniqueId(), q.manager().getUniqueId());
    anchors.put(p.getUniqueId(), q.manager().getLocation());
    Puzzle.objective("puzzle_id").getScoreFor(p).setScore(q.id());
    inspect(p, q);
    return q;
  }

  void inspect(Player p, Puzzle q) {
    String where =
        regions(q.world()).entrySet().stream()
            .filter(e -> e.getValue().horizontal(q.anchor()))
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(", "));
    var loaded =
        q.world().getEntities().stream()
            .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
            .toList();
    long family =
        loaded.stream()
            .filter(e -> Puzzle.score(e, "puzzle_type_id") == q.type() && !e.equals(q.manager()))
            .count();
    long ids = loaded.stream().filter(e -> Puzzle.score(e, "puzzle_id") == q.id()).count();
    p.sendMessage(
        q.world().getName()
            + " • "
            + where
            + " • "
            + q.anchor()
            + " • puzzle #"
            + q.id()
            + " • type #"
            + q.type()
            + " • "
            + family
            + " other loaded copies"
            + (ids > 1 ? " • ID COLLISION (" + ids + ")" : ""));
    outline(p, q.area(), Color.YELLOW);
    if (staff(p)) index.survey(p, q, false);
  }

  void outline(Player p, Area a, Color color) {
    var opt = new Particle.DustOptions(color, 1);
    for (int axis = 0; axis < 3; axis++)
      for (int side = 0; side < 4; side++) {
        double[] from = {a.lo().x(), a.lo().y(), a.lo().z()},
            to = {a.hi().x() + 1., a.hi().y() + 1., a.hi().z() + 1.};
        double[] v = from.clone();
        for (int j = 1; j <= 2; j++) {
          int k = (axis + j) % 3;
          if ((side & 1 << (j - 1)) != 0) v[k] = to[k];
        }
        for (double t = from[axis]; t <= to[axis]; t += .5) {
          v[axis] = t;
          p.spawnParticle(Particle.DUST, v[0], v[1], v[2], 1, 0, 0, 0, 0, opt);
        }
      }
  }

  void link(Player p, String title, String cmd) {
    p.sendMessage(
        Component.text("[" + title + "]", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.runCommand("/bb " + cmd)));
  }

  boolean isWand(ItemStack item) {
    if (item == null || item.getType() != Material.WARPED_FUNGUS_ON_A_STICK) return false;
    var data =
        org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item)
            .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    return data != null && data.copyTag().getBooleanOr("whimc_puzzle_wand", false);
  }

  final Map<UUID, Location> corners = new HashMap<>();

  void corner(Player p, Puzzle q, boolean finish) {
    require(builder(p, q), "Only the builder or instructor can resize.");
    Block b = p.getTargetBlockExact(32);
    require(b != null, "Look at a boundary corner block.");
    if (!finish) {
      corners.put(p.getUniqueId(), b.getLocation());
      p.sendMessage("First corner " + Pos.of(b.getLocation()));
      link(p, "Set opposite corner", "corner2");
      return;
    }
    Location first = corners.get(p.getUniqueId());
    require(first != null && first.getWorld() == q.world(), "Choose the first corner again.");
    Pos a = Pos.of(first), z = Pos.of(b.getLocation());
    Area area =
        new Area(
            new Pos(Math.min(a.x(), z.x()), Math.min(a.y(), z.y()), Math.min(a.z(), z.z())),
            new Pos(Math.max(a.x(), z.x()), Math.max(a.y(), z.y()), Math.max(a.z(), z.z())));
    require(
        area.contains(q.anchor()) && area.volume() <= 32768,
        "Include the start button and stay within 32768 blocks.");
    if (!staff(p)) {
      for (int x = area.lo().x(); x <= area.hi().x(); x++)
        for (int y = area.lo().y(); y <= area.hi().y(); y++)
          for (int zz = area.lo().z(); zz <= area.hi().z(); zz++)
            require(
                creative(p, new Location(q.world(), x, y, zz)),
                "Boundary extends outside your build space.");
    }
    require(
        area.entities(q.world()).stream()
            .noneMatch(
                e -> e.getScoreboardTags().contains(Puzzle.MANAGER) && !e.equals(q.manager())),
        "Boundary contains another start button.");
    var marker =
        q.manager().getPassengers().stream()
            .filter(e -> e.getScoreboardTags().contains("whimc.barrelbot.puzzle_storage"))
            .findFirst()
            .orElseThrow();
    var data = Puzzle.data(marker);
    var bounds = data.getCompoundOrEmpty("bounding_box");
    bounds.putInt("min_x", area.lo().x());
    bounds.putInt("min_y", area.lo().y());
    bounds.putInt("min_z", area.lo().z());
    bounds.putInt("max_x", area.hi().x());
    bounds.putInt("max_y", area.hi().y());
    bounds.putInt("max_z", area.hi().z());
    data.put("bounding_box", bounds);
    Puzzle.data(marker, data);
    Puzzle updated = Puzzle.read(q.manager());
    for (var box : updated.boxes())
      if (!supplies.epoch(box).isEmpty()) supplies.remember(updated, box, supplies.epoch(box));
    saveConfig();
    corners.remove(p.getUniqueId());
    outline(p, area, Color.YELLOW);
    p.sendMessage("Puzzle #" + q.id() + " boundary: " + area.lo() + " to " + area.hi());
  }

  void suggest(Player p, String title, String args) {
    p.sendMessage(
        Component.text("[" + title + "]", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.suggestCommand("/bb " + args)));
  }

  void menu(Player p) {
    link(p, "Select puzzle", "select");
    link(p, "Instruction list", "list");
    link(p, "Return instructions to box", "return");
    link(p, "Lock box contents", "lock");
    link(p, "Blocks and instructions", "tools");
    link(p, "Set first boundary corner", "corner1");
    link(p, "Puzzle features", "features");
    suggest(p, "Assign partner", "partner ");
    if (staff(p)) {
      link(p, "Choose a lesson by name", "lessons");
      link(p, "Add held stack to row", "preview add");
      link(p, "Add hotbar instructions to row", "preview add hotbar");
      link(p, "Preview row reset", "preview reset");
      link(p, "Preview template overwrite", "preview overwrite");
      link(p, "Survey puzzle IDs", "survey");
      link(p, "Preview row ID repair", "preview repair");
      suggest(p, "Assign builder", "builder ");
      Pos target = Pos.of(p.getLocation());
      suggest(p, "Preview paste at coordinates", "preview paste " + target);
      suggest(p, "Preview cascade", "preview cascade " + target + " 2 1 27 0");
      link(p, "Preview setup undo", "undo");
      link(p, "Import unassigned classroom puzzles", "preview import");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void leaveWorld(PlayerTeleportEvent event) {
    if (event.getTo() == null || event.getFrom().getWorld() == event.getTo().getWorld()) return;
    Player p = event.getPlayer();
    supplies.check(p, true);
    if (Arrays.stream(p.getInventory().getContents())
        .anyMatch(
            i ->
                Puzzle.itemData(i)
                    .getStringOr("classroom_home", "")
                    .startsWith(event.getFrom().getWorld().getUID() + "@"))) {
      event.setCancelled(true);
      p.sendMessage(
          "Return your puzzle instructions before leaving this world. Check that the source box has"
              + " room.");
    }
  }

  private String placementKind(BlockPlaceEvent event) {
    var data =
        org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemInHand())
            .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    return data == null
        ? ""
        : data.copyTag().getCompoundOrEmpty("whimc").getStringOr("classroom_block", "");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void validatePlacement(BlockPlaceEvent event) {
    String kind = placementKind(event);
    if (!TOOLS.contains(kind)) return;
    Location location = event.getBlock().getLocation();
    if (!staff(event.getPlayer()) && !creative(event.getPlayer(), location)) {
      event.setCancelled(true);
      event.getPlayer().sendMessage("Build inside your assigned creative space.");
      return;
    }
    boolean occupied =
        location.getWorld().getNearbyEntities(location.clone().add(.5, .5, .5), .6, .6, .6).stream()
            .anyMatch(e -> Scene.actor(e) && Pos.of(e.getLocation()).equals(Pos.of(location)));
    if (occupied) {
      event.setCancelled(true);
      event.getPlayer().sendMessage("A puzzle feature already occupies this block.");
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void place(BlockPlaceEvent event) {
    String kind = placementKind(event);
    if (!TOOLS.contains(kind)) return;
    Player player = event.getPlayer();
    World world = event.getBlock().getWorld();
    Pos at = Pos.of(event.getBlock().getLocation());
    Material placed = event.getBlock().getType();
    float yaw = player.getLocation().getYaw();
    Bukkit.getScheduler()
        .runTask(
            this,
            () -> {
              if (at.at(world).getBlock().getType() != placed) return;
              String who = player.isOnline() ? " as " + player.getUniqueId() : "";
              Bukkit.dispatchCommand(
                  Bukkit.getConsoleSender(),
                  "minecraft:execute"
                      + who
                      + " in "
                      + world.getKey()
                      + " positioned "
                      + at
                      + " rotated "
                      + yaw
                      + " 0 run function whimc:barrelbot/block/"
                      + kind
                      + "/initialize");
              if (kind.equals("puzzle_button")) {
                var managers =
                    world.getNearbyEntities(at.at(world).add(.5, .5, .5), 1, 1, 1).stream()
                        .filter(
                            e ->
                                e.getScoreboardTags().contains(Puzzle.MANAGER)
                                    && Pos.of(e.getLocation()).equals(at))
                        .toList();
                if (managers.size() == 1) {
                  Entity e = managers.getFirst();
                  e.getPersistentDataContainer()
                      .set(owner, PersistentDataType.STRING, player.getUniqueId().toString());
                  selections.put(player.getUniqueId(), e.getUniqueId());
                  anchors.put(player.getUniqueId(), e.getLocation());
                  player.sendMessage(
                      "Created puzzle #"
                          + Puzzle.score(e, "puzzle_id")
                          + " at "
                          + at
                          + ". Use /bb for the boundary and source box.");
                }
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent ev) {
    if (ev.getHand() != EquipmentSlot.HAND
        || ev.getAction() != Action.RIGHT_CLICK_BLOCK
        || ev.getClickedBlock() == null) return;
    Player p = ev.getPlayer();
    Block b = ev.getClickedBlock();
    boolean wand = isWand(ev.getItem());
    if (!(p.isSneaking() || wand)
        || !(b.getType().name().endsWith("_BUTTON") || b.getType() == Material.YELLOW_SHULKER_BOX))
      return;
    if (p.getWorld().getNearbyEntities(b.getLocation().add(.5, .5, .5), 1, 1, 1).stream()
        .noneMatch(
            e ->
                e.getScoreboardTags().contains(Puzzle.MANAGER)
                    || e.getScoreboardTags().contains(Puzzle.BOX))) return;
    ev.setCancelled(true);
    try {
      select(p, b);
      menu(p);
    } catch (RuntimeException ex) {
      p.sendMessage(ex.getMessage());
    }
  }

  boolean canDeposit(Player p, org.bukkit.inventory.Inventory top, ItemStack item) {
    Location loc = top.getLocation();
    if (staff(p)
        || loc == null
        || item == null
        || item.isEmpty()
        || loc.getBlock().getType() != Material.YELLOW_SHULKER_BOX) return true;
    var boxes =
        loc.getWorld().getNearbyEntities(loc.clone().add(.5, .5, .5), 1, 1, 1).stream()
            .filter(
                e ->
                    e instanceof ItemDisplay
                        && e.getScoreboardTags().contains(Puzzle.BOX)
                        && Pos.of(e.getLocation()).equals(Pos.of(loc)))
            .toList();
    if (boxes.isEmpty()) return true;
    if (boxes.size() != 1) return false;
    var box = (ItemDisplay) boxes.getFirst();
    var puzzles =
        loc.getWorld().getNearbyEntities(loc, 64, 64, 64).stream()
            .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
            .map(Puzzle::read)
            .filter(q -> q.area().contains(Pos.of(loc)))
            .toList();
    if (puzzles.size() != 1) return false;
    Puzzle q = puzzles.getFirst();
    return builder(p, q)
        || supplies.owned(item, box, q)
            && (supplies.epoch(box).isEmpty()
                || supplies
                    .epoch(box)
                    .equals(Puzzle.itemData(item).getStringOr("classroom_generation", "")));
  }

  @EventHandler(ignoreCancelled = true)
  public void insert(org.bukkit.event.inventory.InventoryClickEvent ev) {
    if (!(ev.getWhoClicked() instanceof Player p)) return;
    var top = ev.getView().getTopInventory();
    boolean inBox = ev.getClickedInventory() == top;
    ItemStack item =
        switch (ev.getAction()) {
          case MOVE_TO_OTHER_INVENTORY -> inBox ? null : ev.getCurrentItem();
          case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD ->
              !inBox
                  ? null
                  : ev.getHotbarButton() < 0
                      ? p.getInventory().getItemInOffHand()
                      : p.getInventory().getItem(ev.getHotbarButton());
          case SWAP_WITH_CURSOR, PLACE_ALL, PLACE_ONE, PLACE_SOME -> inBox ? ev.getCursor() : null;
          default -> null;
        };
    if (!canDeposit(p, top, item)) {
      ev.setCancelled(true);
      p.sendMessage(
          "Only the builder changes the instruction set. Use Return instructions to box to start"
              + " again.");
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void drag(org.bukkit.event.inventory.InventoryDragEvent ev) {
    var top = ev.getView().getTopInventory();
    if (ev.getWhoClicked() instanceof Player p
        && ev.getRawSlots().stream().anyMatch(i -> i < top.getSize())
        && !canDeposit(p, top, ev.getOldCursor())) ev.setCancelled(true);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player p)) {
      sender.sendMessage("Use an in-game account.");
      return true;
    }
    try {
      String op = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
      switch (op) {
        case "menu" -> menu(p);
        case "survey" -> {
          require(staff(p), "Instructor access required.");
          index.survey(p, selected(p), true);
        }
        case "corner1", "corner2" -> corner(p, selected(p), op.equals("corner2"));
        case "select" -> select(p, p.getTargetBlockExact(12));
        case "tools" -> {
          for (String s : TOOLS) link(p, s, "give " + s);
          for (String s : INSTRUCTIONS) link(p, s, "give " + s);
          link(p, "Counter loop ×3 (edit with /bb give counter 5)", "give counter 3");
          for (String kind : List.of("if", "while"))
            link(p, kind + " air ahead", "give " + kind + " ahead air");
          if (staff(p)) {
            link(p, "Edit wand", "give wand");
            link(p, "Developer book", "give book");
          }
        }
        case "give" -> {
          require(
              staff(p) || creative(p, p.getLocation()),
              "Use tools in your assigned creative build space.");
          require(args.length >= 2, "Choose an item with /bb tools.");
          String s = args[1];
          if (TOOLS.contains(s)) function(p, "block/" + s + "/give");
          else if (s.equals("infinite_loop")) function(p, "instructions/loop/give_infinite");
          else if (INSTRUCTIONS.contains(s)) function(p, "instructions/" + s + "/give");
          else if (s.equals("counter") && args.length == 3) {
            int n = Integer.parseInt(args[2]);
            require(n > 0 && n <= 1000, "Use 1–1000 cycles.");
            function(p, "instructions/loop/counter/give {cycles:" + n + "}");
          } else if ((s.equals("if") || s.equals("while")) && args.length == 4) {
            String coords =
                switch (args[2]) {
                  case "ahead" -> "^ ^ ^1";
                  case "behind" -> "^ ^ ^-1";
                  case "left" -> "^1 ^ ^";
                  case "right" -> "^-1 ^ ^";
                  case "above" -> "~ ~1 ~";
                  case "below" -> "~ ~-1 ~";
                  default ->
                      throw new IllegalStateException(
                          "Use ahead, behind, left, right, above or below.");
                };
            Material mat = Material.matchMaterial(args[3]);
            require(mat != null && mat.isBlock(), "Choose a Minecraft block.");
            String predicate = "if block " + coords + " " + mat.getKey();
            function(
                p,
                "instructions/"
                    + (s.equals("while") ? "loop/while" : "if")
                    + "/give {condition:'"
                    + predicate
                    + "',description:'"
                    + args[2]
                    + " is "
                    + mat.getKey()
                    + "'}");
          } else if (staff(p) && s.equals("wand")) dispatch(p, "loot give @s loot whimc:edit_wand");
          else if (staff(p) && s.equals("book")) function(p, "dev/give_dev_book");
          else throw new IllegalStateException("Unknown tool.");
        }
        case "builder", "partner" -> {
          Puzzle q = selected(p);
          require(
              op.equals("builder") ? staff(p) : builder(p, q),
              "Only the instructor assigns a builder; the builder can assign a partner.");
          require(args.length == 2, "Supply the online player's account name.");
          Player peer = Bukkit.getPlayerExact(args[1]);
          require(peer != null, "Account must be online.");
          q.manager()
              .getPersistentDataContainer()
              .set(
                  op.equals("builder") ? owner : partner,
                  PersistentDataType.STRING,
                  peer.getUniqueId().toString());
          p.sendMessage("Puzzle #" + q.id() + " " + op + " is " + peer.getName());
        }
        case "lessons", "lesson" -> {
          require(staff(p), "Instructor access required.");
          var map = regions(p.getWorld());
          if (op.equals("lessons")) {
            map.keySet().stream()
                .filter(k -> k.startsWith("puzzle"))
                .sorted(
                    Comparator.comparingInt(k -> Integer.parseInt(k.substring(6, k.indexOf('-')))))
                .forEach(k -> link(p, k, "lesson " + k));
          } else {
            require(
                (args.length == 2 || args.length == 3)
                    && args[1].startsWith("puzzle")
                    && map.containsKey(args[1]),
                "Choose a named lesson.");
            Area row = map.get(args[1]);
            Area lane =
                map.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("1-"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
            Area first =
                new Area(
                    new Pos(Math.max(lane.lo().x(), row.lo().x()), row.lo().y(), row.lo().z()),
                    new Pos(Math.min(lane.hi().x(), row.hi().x()), row.hi().y(), row.hi().z()));
            var found =
                first.entities(p.getWorld()).stream()
                    .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
                    .toList();
            require(
                !found.isEmpty(),
                "No template start button in lane 1 for "
                    + args[1]
                    + ". Select a built puzzle in that row.");
            if (found.size() > 1 && args.length == 2) {
              for (var e : found)
                link(
                    p,
                    "Puzzle at " + Pos.of(e.getLocation()),
                    "lesson " + args[1] + " " + e.getUniqueId());
              return true;
            }
            var chosen =
                args.length == 3
                    ? found.stream()
                        .filter(e -> e.getUniqueId().toString().equals(args[2]))
                        .findFirst()
                        .orElseThrow()
                    : found.getFirst();
            var q = Puzzle.read(chosen);
            selections.put(p.getUniqueId(), q.manager().getUniqueId());
            anchors.put(p.getUniqueId(), q.manager().getLocation());
            inspect(p, q);
            menu(p);
          }
        }
        case "features" -> {
          link(p, "Win on green", "win touch_green");
          link(p, "Win on observer", "win touch_observer");
          link(p, "Use individual bot goals", "win defer");
          link(p, "Link held item to function block", "link-function");
          link(p, "Convert the targeted bot", "convert");
        }
        case "win", "link-function", "convert" -> {
          Puzzle q = selected(p);
          require(builder(p, q), "Only the builder or instructor can change puzzle features.");
          if (op.equals("win")) {
            require(
                args.length == 2
                    && Set.of("touch_green", "touch_observer", "defer").contains(args[1]),
                "Choose a supported success condition.");
            var marker =
                q.manager().getPassengers().stream()
                    .filter(e -> e.getScoreboardTags().contains("whimc.barrelbot.puzzle_storage"))
                    .findFirst()
                    .orElseThrow();
            var data = Puzzle.data(marker);
            data.putString("success_function", args[1]);
            Puzzle.data(marker, data);
          } else {
            Block target = p.getTargetBlockExact(8);
            require(
                target != null && q.area().contains(Pos.of(target.getLocation())),
                "Look at a block inside the selected puzzle.");
            String tag = op.equals("convert") ? "whimc.barrelbot" : "whimc.barrelbot.function";
            var hits =
                q.area().entities(q.world()).stream()
                    .filter(
                        e ->
                            e.getScoreboardTags().contains(tag)
                                && Pos.of(e.getLocation()).equals(Pos.of(target.getLocation())))
                    .toList();
            require(hits.size() == 1, "Expected one matching actor on this block.");
            if (op.equals("convert")) function(hits.getFirst(), "dispenser_bot/toggle");
            else {
              require(!p.getInventory().getItemInMainHand().isEmpty(), "Hold the item to link.");
              dispatch(
                  p,
                  "execute positioned "
                      + Pos.of(target.getLocation())
                      + " run function whimc:barrelbot/dev/link_function_item");
            }
          }
          p.sendMessage("Updated puzzle #" + q.id() + ".");
        }
        case "list", "lock", "return" -> {
          Puzzle q = selected(p);
          List<ItemDisplay> boxes = q.boxes();
          require(!boxes.isEmpty(), "No source box inside the puzzle boundary.");
          if (op.equals("return")) {
            require(
                builder(p, q)
                    || role(q, owner).equals(p.getUniqueId().toString())
                    || role(q, partner).equals(p.getUniqueId().toString())
                    || wg() && role(q, partner).isEmpty() && Regions.laneMember(p, q, true, false)
                    || wg() && role(q, owner).isEmpty() && Regions.laneMember(p, q, false, false),
                "Only the assigned builder, partner or instructor can recall.");
            supplies.recall(q, boxes);
            p.sendMessage("Returned instructions for puzzle #" + q.id() + ".");
          } else if (op.equals("lock")) {
            require(
                builder(p, q),
                "Only this puzzle's builder or an instructor can lock instructions.");
            Block target = p.getTargetBlockExact(12);
            var box =
                boxes.stream()
                    .filter(
                        e ->
                            boxes.size() == 1
                                || target != null
                                    && Pos.of(target.getLocation()).equals(Pos.of(e.getLocation())))
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalStateException("Look at the source box to lock."));
            supplies.lock(q, box, Puzzle.container(box).getInventory().getContents());
            saveConfig();
            p.sendMessage(
                "Locked instructions to puzzle #" + q.id() + " at " + Pos.of(box.getLocation()));
          }
          for (var box : boxes)
            p.sendMessage(
                "Box " + Pos.of(box.getLocation()) + " • " + Supplies.list(Supplies.baseline(box)));
        }
        case "preview",
            "apply",
            "test",
            "undo",
            "confirm-undo",
            "cancel",
            "targets",
            "conflicts" -> {
          require(staff(p), "Instructor access required.");
          jobs.command(p, args);
        }
        default -> menu(p);
      }
    } catch (RuntimeException ex) {
      p.sendMessage(
          Component.text(
              Objects.toString(ex.getMessage(), "Operation failed"), NamedTextColor.RED));
      getLogger().fine(ex.toString());
    }
    return true;
  }
}
