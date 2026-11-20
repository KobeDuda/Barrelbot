package org.whimc.barrelbot;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

/** Each preview owns its tickets until expiry. Only the instructor who made it can apply it. */
final class LessonJobs {
  final Classroom app;

  LessonJobs(Classroom app) {
    this.app = app;
  }

  Plan current;

  record Target(String lane, Pos anchor, Area area, List<UUID> managers) {}

  record Backup(World world, Scene scene, String after) {}

  final Map<UUID, List<Backup>> undo = new HashMap<>();
  final Map<UUID, List<String>> undoStates = new HashMap<>();
  final Map<UUID, Long> undoPreview = new HashMap<>();

  record Key(int x, int z) {}

  static final class Plan {
    final UUID operator;
    final String confirmation = UUID.randomUUID().toString();
    final Puzzle source;
    final World world;
    final String op, row;
    final Area survey;
    final List<Target> targets = new ArrayList<>();
    final Set<Key> tickets = new HashSet<>();
    final List<String> conflicts = new ArrayList<>();
    final Set<Target> done = new HashSet<>();
    final Map<Target, String> signatures = new HashMap<>();
    final List<Backup> backups = new ArrayList<>();
    List<ItemStack> additions;
    final Pos paste;
    List<Pos> placements = List.of();
    String sourceSignature;
    boolean ready;
    final Map<String, Integer> repairTypes = new HashMap<>();

    Plan(Player p, Puzzle q, String op, String row, Area survey, Pos paste) {
      operator = p.getUniqueId();
      source = q;
      world = q.world();
      this.op = op;
      this.row = row;
      this.survey = survey;
      this.paste = paste;
      additions = List.of(p.getInventory().getItemInMainHand().clone());
    }
  }

  void release(Plan job) {
    if (current != job) return;
    for (Key k : job.tickets) job.world.removePluginChunkTicket(k.x, k.z, app);
    current = null;
  }

  void command(Player p, String[] args) {
    String action = args[0];
    if (action.equals("undo") || action.equals("confirm-undo")) {
      var back = undo.get(p.getUniqueId());
      app.require(
          back != null && !back.isEmpty(), "No setup operation to undo in this server session.");
      app.require(current == null, "Finish or cancel the open preview first.");
      List<String> states = new ArrayList<>();
      for (Backup b : back) {
        app.require(b.world.equals(p.getWorld()), "Return to the operation's world.");
        String state = signature(b.scene.area(), b.world);
        states.add(state);
        if (action.equals("undo") && !state.equals(b.after))
          p.sendMessage(
              "Changed since setup at "
                  + b.scene.area().lo()
                  + ". Undo will restore the earlier blocks and actors here.");
        clearPlayers(b.world, b.scene.area(), p);
        if (b.scene.area().lo().at(b.world).distance(p.getLocation()) < 128)
          app.outline(p, b.scene.area(), Color.RED);
      }
      if (action.equals("undo")) {
        undoStates.put(p.getUniqueId(), states);
        undoPreview.put(p.getUniqueId(), System.currentTimeMillis() + 120000);
        p.sendMessage(
            "Restore "
                + back.size()
                + " destination snapshots in "
                + p.getWorld().getName()
                + " from the last setup operation.");
        app.link(p, "Confirm setup undo", "confirm-undo");
        return;
      }
      app.require(
          undoPreview.getOrDefault(p.getUniqueId(), 0L) >= System.currentTimeMillis(),
          "Preview the undo first.");
      app.require(
          states.equals(undoStates.get(p.getUniqueId())),
          "Destination changed since the undo preview. Preview again.");
      undoStates.remove(p.getUniqueId());
      undoPreview.remove(p.getUniqueId());
      while (!back.isEmpty()) {
        var b = back.getLast();
        b.scene.place(b.world, new Pos(0, 0, 0), false, 0, 0);
        restoreHomeRegistry(b);
        back.removeLast();
      }
      app.saveConfig();
      p.sendMessage("Setup snapshots restored.");
      return;
    }
    if (action.equals("preview")) {
      if (current != null) {
        app.require(
            current.operator.equals(p.getUniqueId()), "Another instructor has an open preview.");
        release(current);
      }
      app.require(
          args.length >= 2, "Use /bb preview add, reset, overwrite, repair, or paste <x> <y> <z>.");
      Puzzle q = app.selected(p);
      String op = args[1];
      app.require(
          Set.of("add", "reset", "overwrite", "repair", "paste", "cascade", "import", "repair-here")
              .contains(op),
          "Unknown row operation.");
      Pos dest = null;
      Map<String, Area> map = app.regions(q.world());
      String row = "custom paste";
      Area survey = q.area();
      if (op.equals("paste") || op.equals("cascade")) {
        app.require(
            args.length == (op.equals("paste") ? 5 : 9),
            "Use /bb preview paste <button x> <y> <z>.");
        dest =
            new Pos(
                Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
      } else if (op.equals("repair-here")) {
        row = "individual puzzle";
      } else if (op.equals("import")) {
        app.require(
            !map.isEmpty(), "WorldGuard classroom regions are required to import this world.");
        row = "classroom import";
      } else {
        var rows =
            map.entrySet().stream()
                .filter(e -> e.getKey().startsWith("puzzle") && e.getValue().contains(q.anchor()))
                .toList();
        app.require(rows.size() == 1, "Select a puzzle in exactly one WorldGuard lesson row.");
        row = rows.getFirst().getKey();
        survey = rows.getFirst().getValue();
      }
      Plan job = new Plan(p, q, op, row, survey, dest);
      if (dest != null) {
        int nx = op.equals("cascade") ? Integer.parseInt(args[5]) : 1,
            nz = op.equals("cascade") ? Integer.parseInt(args[6]) : 1;
        int sx = op.equals("cascade") ? Integer.parseInt(args[7]) : 0,
            sz = op.equals("cascade") ? Integer.parseInt(args[8]) : 0;
        app.require(
            nx != 0 && nz != 0 && Math.abs((long) nx * nz) <= 128, "Choose 1 to 128 total copies.");
        List<Pos> positions = new ArrayList<>();
        for (int x = 0; x < Math.abs(nx); x++)
          for (int z = 0; z < Math.abs(nz); z++)
            positions.add(
                new Pos(
                    Math.toIntExact(dest.x() + (long) x * Integer.signum(nx) * sx),
                    dest.y(),
                    Math.toIntExact(dest.z() + (long) z * Integer.signum(nz) * sz)));
        job.placements = List.copyOf(positions);
      }
      if (op.equals("add") && args.length == 3 && args[2].equals("hotbar")) {
        job.additions =
            java.util.stream.IntStream.range(0, 9)
                .mapToObj(p.getInventory()::getItem)
                .filter(i -> i != null && Puzzle.itemData(i).contains("instruction"))
                .map(ItemStack::clone)
                .toList();
      }
      if (op.equals("add"))
        app.require(
            !job.additions.isEmpty()
                && job.additions.stream()
                    .allMatch(i -> i.getAmount() > 0 && Puzzle.itemData(i).contains("instruction")),
            "Hold the instruction stack to add. Its count and settings are used.");
      current = job;
      try {
        load(p, job, map);
      } catch (RuntimeException ex) {
        release(job);
        throw ex;
      }
      return;
    }
    Plan job = current;
    app.require(
        job != null && job.operator.equals(p.getUniqueId()), "Create your own preview first.");
    if (action.equals("conflicts")) {
      job.conflicts.forEach(p::sendMessage);
      return;
    }
    if (action.equals("targets")) {
      int page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
      app.require(page > 0 && page <= (job.targets.size() + 11) / 12, "Choose an available page.");
      job.targets.stream()
          .skip((page - 1L) * 12)
          .limit(12)
          .forEach(
              t ->
                  p.sendMessage(
                      t.lane + " • " + t.anchor + " • " + t.area.lo() + " to " + t.area.hi()));
      return;
    }
    if (action.equals("cancel")) {
      release(job);
      p.sendMessage("Preview closed.");
      return;
    }
    app.require(
        job.ready && job.world.equals(p.getWorld()), "Wait for the preview, in the same world.");
    if (args.length > 1)
      app.require(
          job.confirmation.equals(args[1]),
          "That preview has been replaced. Open the current preview.");
    boolean test = action.equals("test");
    List<Target> next =
        job.targets.stream()
            .filter(t -> !job.done.contains(t) && (!test || t.lane.startsWith("2-")))
            .toList();
    app.require(!next.isEmpty(), "No pending targets in that selection.");
    app.require(
        signature(job.source.area(), job.world).equals(job.sourceSignature),
        "Template changed. Preview again.");
    for (var t : next) {
      app.require(
          signature(t.area, job.world).equals(job.signatures.get(t)),
          "Destination changed at " + t.anchor + ". Preview again.");
      clearPlayers(job.world, t.area, p);
    }
    for (String field : List.of("bot_id", "puzzle_type_id", "puzzle_id")) {
      var o = Puzzle.objective(field);
      var counter = o.getScore("$global");
      int max = counter.getScore();
      for (String entry : o.getScoreboard().getEntries()) {
        var value = o.getScore(entry);
        if (value.isScoreSet()) max = Math.max(max, value.getScore());
      }
      if (max > counter.getScore()) counter.setScore(max);
    }
    job.ready = false;
    applyNext(p, job, next.iterator());
  }

  void restoreHomeRegistry(Backup b) {
    app.index.chunk(b.scene.area().lo().at(b.world).getChunk());
    for (Entity e : b.scene.area().entities(b.world))
      if (e instanceof ItemDisplay box && e.getScoreboardTags().contains(Puzzle.BOX)) {
        String gen = app.supplies.epoch(box);
        if (!gen.isEmpty()) {
          var qs =
              b.scene.area().entities(b.world).stream()
                  .filter(m -> m.getScoreboardTags().contains(Puzzle.MANAGER))
                  .map(Puzzle::read)
                  .filter(q -> q.area().contains(Pos.of(box.getLocation())))
                  .toList();
          if (qs.size() == 1) app.supplies.remember(qs.getFirst(), box, gen);
        }
      }
  }

  static void clearPlayers(World w, Area a, Player instructor) {
    for (Player p : w.getPlayers())
      if (!p.equals(instructor) && a.contains(Pos.of(p.getLocation())))
        throw new IllegalStateException(
            p.getName() + " is in the affected area. Wait for setup time.");
  }

  void chunks(Set<Key> keys, Area a) {
    for (int x = a.lo().x() >> 4; x <= a.hi().x() >> 4; x++)
      for (int z = a.lo().z() >> 4; z <= a.hi().z() >> 4; z++) keys.add(new Key(x, z));
  }

  void load(Player p, Plan j, Map<String, Area> regions) {
    Set<Key> keys = new LinkedHashSet<>();
    chunks(keys, j.source.area());
    chunks(keys, j.survey);
    if (j.op.equals("import")) for (Area a : regions.values()) chunks(keys, a);
    for (Pos at : j.placements) {
      Area a = j.source.area().shift(at.minus(j.source.anchor()));
      checkArea(j.world, a);
      chunks(keys, a);
    }
    var queue = new ArrayDeque<>(keys);
    p.sendMessage("Checking " + keys.size() + " chunks in " + j.world.getName() + " • " + j.row);
    Bukkit.getScheduler()
        .runTaskTimer(
            app,
            task -> {
              if (current != j) {
                task.cancel();
                return;
              }
              try {
                app.require(
                    p.isOnline() && p.getWorld() == j.world,
                    "Preview closed because the instructor left this world.");
                for (int i = 0; i < 4 && !queue.isEmpty(); i++) {
                  Key k = queue.remove();
                  app.require(
                      j.world.isChunkGenerated(k.x, k.z),
                      "Missing terrain chunk " + k.x + ", " + k.z + ".");
                  j.world.addPluginChunkTicket(k.x, k.z, app);
                  j.tickets.add(k);
                  j.world.getChunkAt(k.x, k.z).getEntities();
                }
                if (queue.isEmpty() && !app.index.scanning.contains(j.world.getUID())) {
                  task.cancel();
                  prepare(p, j, regions);
                  j.ready = true;
                  Bukkit.getScheduler()
                      .runTaskLater(
                          app,
                          () -> {
                            if (current == j && j.ready) {
                              release(j);
                              p.sendMessage("Preview expired.");
                            }
                          },
                          2400);
                }
              } catch (RuntimeException ex) {
                task.cancel();
                release(j);
                p.sendMessage("Preview stopped: " + ex.getMessage());
              }
            },
            1,
            1);
  }

  void checkArea(World w, Area a) {
    app.require(
        a.lo().y() >= w.getMinHeight()
            && a.hi().y() < w.getMaxHeight()
            && w.getWorldBorder().isInside(a.lo().at(w))
            && w.getWorldBorder().isInside(a.hi().at(w)),
        "Destination extends outside the world.");
  }

  List<Puzzle> managers(World w) {
    return w.getEntities().stream()
        .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
        .map(Puzzle::read)
        .toList();
  }

  void prepare(Player p, Plan j, Map<String, Area> regions) {
    List<Puzzle> all = managers(j.world);
    if (!j.op.startsWith("repair") && !j.op.equals("import"))
      app.require(
          j.source.id() > 0
              && j.source.type() > 0
              && !app.index.duplicate(j.world, j.source)
              && all.stream().filter(q -> q.id() == j.source.id()).count() == 1,
          "Template has a missing or duplicate ID. Preview repair first.");
    app.require(
        !j.source.manager().getScoreboardTags().contains(Puzzle.MANAGER + ".active"),
        "Stop the template before previewing edits.");
    j.sourceSignature = signature(j.source.area(), j.world);
    long count =
        all.stream()
            .filter(
                q ->
                    j.survey.contains(q.anchor())
                        && q.type() == j.source.type()
                        && !q.manager().equals(j.source.manager()))
            .count();
    p.sendMessage(
        j.row
            + " • source #"
            + j.source.id()
            + " • type #"
            + j.source.type()
            + " • "
            + count
            + " other copies of this type in the surveyed row.");
    if (j.op.equals("repair-here")) target(j, "selected puzzle", j.source.anchor(), all);
    else if (j.op.equals("import")) {
      var lanes =
          regions.entrySet().stream().filter(e -> Character.isDigit(e.getKey().charAt(0))).toList();
      for (Puzzle q : all) {
        if (q.id() > 0
            && q.type() > 0
            && q.boxes().stream()
                .noneMatch(
                    box ->
                        !box.getPersistentDataContainer()
                            .getOrDefault(
                                new NamespacedKey(app, "origin_world"),
                                org.bukkit.persistence.PersistentDataType.STRING,
                                j.world.getUID().toString())
                            .equals(j.world.getUID().toString()))) continue;
        var lane = lanes.stream().filter(e -> e.getValue().contains(q.anchor())).findFirst();
        var lesson =
            regions.entrySet().stream()
                .filter(e -> e.getKey().startsWith("puzzle") && e.getValue().contains(q.anchor()))
                .findFirst();
        if (lane.isEmpty() && lesson.isEmpty()) continue;
        if (all.stream()
            .anyMatch(
                other -> !other.manager().equals(q.manager()) && other.area().overlaps(q.area()))) {
          j.conflicts.add(q.anchor() + " • overlapping puzzle boundaries");
          continue;
        }
        target(j, lane.map(Map.Entry::getKey).orElse("outside numbered lane"), q.anchor(), all);
      }
    } else if (j.paste != null) {
      int n = 0;
      for (Pos at : j.placements) target(j, "copy " + (++n), at, all);
    } else {
      var lanes =
          regions.entrySet().stream()
              .filter(e -> Character.isDigit(e.getKey().charAt(0)))
              .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey().split("-")[0])))
              .toList();
      var sourceLane =
          lanes.stream()
              .filter(
                  e ->
                      e.getValue().lo().x() <= j.source.anchor().x()
                          && e.getValue().hi().x() >= j.source.anchor().x())
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No source lane."));
      for (var lane : lanes) {
        if (j.op.equals("overwrite") && lane.getKey().equals(sourceLane.getKey())) continue;
        var a = lane.getValue();
        var existing =
            all.stream()
                .filter(
                    q ->
                        j.survey.contains(q.anchor())
                            && q.anchor().x() >= a.lo().x()
                            && q.anchor().x() <= a.hi().x())
                .toList();
        if (existing.isEmpty() && !a.overlaps(j.survey)) {
          p.sendMessage(lane.getKey() + " outside this lesson region; excluded.");
          continue;
        }
        var positions = existing.stream().map(Puzzle::anchor).distinct().toList();
        if (positions.size() > 1
            && (j.op.equals("reset") || j.op.equals("add") || j.op.equals("repair"))) {
          for (Pos pos : positions) target(j, lane.getKey(), pos, all);
          continue;
        }
        if (positions.size() > 1) {
          int expected = j.source.anchor().x() + a.lo().x() - sourceLane.getValue().lo().x();
          positions =
              positions.stream()
                  .filter(
                      pos ->
                          pos.y() == j.source.anchor().y()
                              && pos.z() == j.source.anchor().z()
                              && Math.abs(pos.x() - expected) <= 3)
                  .toList();
          app.require(
              positions.size() == 1,
              "Ambiguous template match in " + lane.getKey() + ". Use custom paste for this slot.");
        }
        if (positions.isEmpty() && !j.op.equals("overwrite")) {
          app.require(
              j.op.equals("repair"),
              "Missing puzzle in " + lane.getKey() + ". Preview an overwrite to fill it.");
          continue;
        }
        Pos at =
            positions.isEmpty()
                ? j.source.anchor().plus(new Pos(a.lo().x() - sourceLane.getValue().lo().x(), 0, 0))
                : positions.getFirst();
        target(j, lane.getKey(), at, all);
      }
    }
    app.require(!j.targets.isEmpty(), "No destinations.");
    for (Target t : j.targets) {
      j.signatures.put(t, signature(t.area, j.world));
      p.sendMessage(
          t.lane
              + " • "
              + t.anchor
              + " • "
              + t.managers.size()
              + " existing • "
              + t.area.lo()
              + " to "
              + t.area.hi());
      if (t.anchor.at(j.world).distance(p.getLocation()) < 96) app.outline(p, t.area, Color.AQUA);
    }
    if (!j.conflicts.isEmpty()) {
      p.sendMessage(j.conflicts.size() + " conflicting anchors excluded.");
      app.link(p, "Show excluded puzzle locations", "conflicts");
    }
    if (j.op.equals("import") && j.targets.size() > 12)
      p.sendMessage("First 12 destinations shown. Use /bb targets <page> for the full list.");
    p.sendMessage(
        "Preview "
            + j.op
            + " • "
            + j.targets.size()
            + " destinations"
            + (j.op.equals("add")
                ? " • add "
                    + Supplies.list(j.additions.toArray(ItemStack[]::new))
                    + " to each source box"
                : ""));
    if (j.paste == null) app.link(p, "Apply to lane 2 only", "test " + j.confirmation);
    app.link(p, "Apply remaining destinations", "apply " + j.confirmation);
    app.link(p, "Cancel", "cancel");
  }

  void target(Plan j, String lane, Pos at, List<Puzzle> all) {
    var hits = all.stream().filter(q -> q.anchor().equals(at)).toList();
    boolean clone = j.op.equals("overwrite") || j.paste != null;
    if (!clone)
      app.require(
          hits.size() == 1, "Overlapping managers at " + at + ". Preview an explicit overwrite.");
    Area area = clone ? j.source.area().shift(at.minus(j.source.anchor())) : hits.getFirst().area();
    checkArea(j.world, area);
    if (j.op.startsWith("repair") || j.op.equals("import"))
      for (Puzzle other : all)
        if (!hits.contains(other) && area.overlaps(other.area()))
          throw new IllegalStateException(
              "Repair bounds overlap another puzzle at " + other.anchor());
    if (clone) {
      app.require(!area.overlaps(j.source.area()), "Destination overlaps the source.");
      for (Puzzle q : all)
        if (area.overlaps(q.area()))
          app.require(
              hits.contains(q) && area.equals(q.area()),
              "Would cut through another puzzle boundary at " + q.anchor());
    } else if (!j.op.startsWith("repair") && !j.op.equals("import")) {
      Puzzle q = hits.getFirst();
      app.require(
          q.id() > 0
              && !app.index.duplicate(j.world, q)
              && all.stream().filter(other -> other.id() == q.id()).count() == 1,
          "ID collision at " + q.anchor() + ". Preview repair.");
      // Additions and resets operate on a named geographic row, including mixed lesson types.
      if (j.op.equals("add")) {
        var boxes = q.boxes();
        app.require(!boxes.isEmpty(), "No source box in " + lane);
        for (var b : boxes) app.supplies.add(q, b, j.additions, false);
      }
    }
    app.require(
        j.targets.stream().noneMatch(t -> t.area.overlaps(area)), "Overlapping destinations.");
    Set<Key> needed = new HashSet<>();
    chunks(needed, area);
    for (Key k : needed)
      if (j.tickets.add(k)) {
        app.require(j.world.isChunkGenerated(k.x, k.z), "Missing target chunk.");
        j.world.addPluginChunkTicket(k.x, k.z, app);
        j.world.getChunkAt(k.x, k.z).getEntities();
      }
    j.targets.add(
        new Target(lane, at, area, hits.stream().map(q -> q.manager().getUniqueId()).toList()));
  }

  String signature(Area a, World w) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      for (int x = a.lo().x(); x <= a.hi().x(); x++)
        for (int y = a.lo().y(); y <= a.hi().y(); y++)
          for (int z = a.lo().z(); z <= a.hi().z(); z++) {
            var block = w.getBlockAt(x, y, z);
            digest.update(
                block
                    .getBlockData()
                    .getAsString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var level = ((org.bukkit.craftbukkit.CraftWorld) w).getHandle();
            var tile = level.getBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
            if (tile != null)
              digest.update(
                  tile.saveWithoutMetadata(level.registryAccess())
                      .toString()
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
          }
      a.entities(w).stream()
          .filter(Scene::actor)
          .sorted(Comparator.comparing(Entity::getUniqueId))
          .forEach(
              e -> {
                String data =
                    e.getUniqueId()
                        + " "
                        + Pos.of(e.getLocation())
                        + " "
                        + Puzzle.score(e, "puzzle_id")
                        + " "
                        + Puzzle.score(e, "puzzle_type_id")
                        + " "
                        + Puzzle.data(e)
                        + " "
                        + new TreeSet<>(e.getScoreboardTags());
                digest.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (e instanceof ItemDisplay display)
                  digest.update(display.getItemStack().serializeAsBytes());
              });
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot verify setup snapshot", ex);
    }
  }

  void applyNext(Player p, Plan j, Iterator<Target> next) {
    if (current != j) return;
    if (!next.hasNext()) {
      j.ready = true;
      p.sendMessage(
          "Verified "
              + j.done.size()
              + " of "
              + j.targets.size()
              + " destinations in "
              + j.row
              + ".");
      if (j.done.size() == j.targets.size()) release(j);
      return;
    }
    Target t = next.next();
    Backup before = null;
    try {
      app.require(
          p.isOnline() && p.getWorld() == j.world && app.staff(p),
          "Instructor left this setup operation.");
      clearPlayers(j.world, t.area, p);
      app.require(
          t.area.entities(j.world).stream()
              .noneMatch(e -> e.getScoreboardTags().contains(Puzzle.MANAGER + ".active")),
          "Stop running puzzles in this destination first.");
      app.require(
          signature(t.area, j.world).equals(j.signatures.get(t)),
          "Destination changed since preview.");
      before = new Backup(j.world, Scene.capture(j.world, t.area), null);
      if (j.op.equals("overwrite") || j.paste != null) {
        app.require(
            signature(j.source.area(), j.world).equals(j.sourceSignature),
            "Template changed since preview.");
        Entity old = t.managers.size() == 1 ? j.world.getEntity(t.managers.getFirst()) : null;
        String builder =
            old == null
                ? null
                : old.getPersistentDataContainer()
                    .get(app.owner, org.bukkit.persistence.PersistentDataType.STRING);
        String partner =
            old == null
                ? null
                : old.getPersistentDataContainer()
                    .get(app.partner, org.bukkit.persistence.PersistentDataType.STRING);
        Scene source = Scene.capture(j.world, j.source.area());
        int id = Puzzle.allocate("puzzle_id");
        source.place(j.world, t.anchor.minus(j.source.anchor()), true, id, j.source.type());
        var created =
            t.area.entities(j.world).stream()
                .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
                .toList();
        app.require(created.size() == 1, "Overwrite did not produce one manager.");
        Puzzle q = Puzzle.read(created.getFirst());
        app.require(
            q.id() == id && q.area().equals(t.area), "Copied boundary or ID failed verification.");
        var strings = org.bukkit.persistence.PersistentDataType.STRING;
        String parent =
            j.source
                .manager()
                .getPersistentDataContainer()
                .getOrDefault(app.identity, strings, j.source.manager().getUniqueId().toString());
        j.source.manager().getPersistentDataContainer().set(app.identity, strings, parent);
        q.manager()
            .getPersistentDataContainer()
            .set(app.identity, strings, UUID.randomUUID().toString());
        q.manager().getPersistentDataContainer().set(app.parent, strings, parent);
        app.require(
            Scene.actors(j.world, t.area).size() == source.actors().size(),
            "Copied actor count failed verification.");
        q.manager().getPersistentDataContainer().remove(app.owner);
        q.manager().getPersistentDataContainer().remove(app.partner);
        if (t.managers.size() == 1) {
          if (builder != null)
            q.manager()
                .getPersistentDataContainer()
                .set(app.owner, org.bukkit.persistence.PersistentDataType.STRING, builder);
          if (partner != null)
            q.manager()
                .getPersistentDataContainer()
                .set(app.partner, org.bukkit.persistence.PersistentDataType.STRING, partner);
        }
        for (var b : q.boxes()) app.supplies.lock(q, b, Supplies.baseline(b));
      } else {
        Puzzle q = Puzzle.read(Objects.requireNonNull(j.world.getEntity(t.managers.getFirst())));
        switch (j.op) {
          case "repair", "repair-here", "import" -> repair(q, j);
          case "reset" -> {
            var boxes = q.boxes();
            if (!boxes.isEmpty()) app.supplies.recall(q, boxes);
            app.function(q.manager(), "manager/reset_puzzle");
          }
          case "add" -> {
            for (var b : q.boxes()) app.supplies.add(q, b, j.additions, true);
          }
        }
      }
      undoPreview.remove(p.getUniqueId());
      j.backups.add(new Backup(j.world, before.scene, signature(t.area, j.world)));
      undo.put(p.getUniqueId(), j.backups);
      j.done.add(t);
      app.index.chunk(t.anchor.at(j.world).getChunk());
      app.saveConfig();
      if (t.anchor.equals(j.source.anchor()))
        j.sourceSignature = signature(j.source.area(), j.world);
      p.sendMessage(t.lane + " • " + t.anchor + " • " + j.op + " complete.");
      Bukkit.getScheduler().runTaskLater(app, () -> applyNext(p, j, next), 1);
    } catch (RuntimeException ex) {
      try {
        if (before != null) {
          before.scene.place(j.world, new Pos(0, 0, 0), false, 0, 0);
          restoreHomeRegistry(before);
          app.saveConfig();
        }
      } finally {
        release(j);
        p.sendMessage(
            "Stopped at "
                + t.lane
                + " • "
                + ex.getMessage()
                + " • "
                + j.done.size()
                + " completed.");
      }
    }
  }

  void repair(Puzzle q, Plan j) {
    int id = Puzzle.allocate("puzzle_id");
    // Separate exercises in the same WorldGuard band can have distinct template families.
    String slot = q.anchor().y() + "/" + q.anchor().z();
    String lesson =
        j.op.equals("import")
            ? app.regions(j.world).entrySet().stream()
                .filter(e -> e.getKey().startsWith("puzzle") && e.getValue().contains(q.anchor()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("independent")
            : j.row;
    slot = lesson + "/" + slot;
    if (lesson.contains("-create") || lesson.contains("-pairs") || lesson.equals("independent"))
      slot = q.manager().getUniqueId().toString();
    int type =
        q.type() > 0
            ? q.type()
            : j.repairTypes.computeIfAbsent(slot, k -> Puzzle.allocate("puzzle_type_id"));
    for (Entity e : q.area().entities(q.world()))
      if (Scene.actor(e)) {
        Puzzle.objective("puzzle_id").getScoreFor(e).setScore(id);
        Puzzle.objective("puzzle_type_id").getScoreFor(e).setScore(type);
        if (e.getScoreboardTags().contains(Puzzle.BOX)
            || e.getScoreboardTags().contains("whimc.barrelbot"))
          Puzzle.objective("bot_id").getScoreFor(e).setScore(Puzzle.allocate("bot_id"));
      }
    Puzzle fixed = Puzzle.read(q.manager());
    for (ItemDisplay b : fixed.boxes()) {
      var saved = Supplies.baseline(b);
      if (Arrays.stream(saved).allMatch(Objects::isNull))
        saved = Puzzle.container(b).getInventory().getContents();
      app.supplies.lock(fixed, b, saved);
    }
  }
}
