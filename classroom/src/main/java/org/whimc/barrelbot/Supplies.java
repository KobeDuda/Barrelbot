package org.whimc.barrelbot;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

final class Supplies {
  final Classroom app;
  final NamespacedKey epoch;

  Supplies(Classroom app) {
    this.app = app;
    epoch = new NamespacedKey(app, "generation");
  }

  String home(ItemDisplay box) {
    return box.getWorld().getUID() + "@" + Pos.of(box.getLocation()).toString().replace(' ', ',');
  }

  String epoch(ItemDisplay box) {
    return box.getPersistentDataContainer().getOrDefault(epoch, PersistentDataType.STRING, "");
  }

  static ItemStack[] baseline(ItemDisplay box) {
    var stack = CraftItemStack.asNMSCopy(box.getItemStack());
    var contents = stack.get(DataComponents.CONTAINER);
    ItemStack[] saved = new ItemStack[Puzzle.container(box).getInventory().getSize()];
    if (contents != null) {
      var list = contents.itemCopies().toList();
      if (list.size() > saved.length)
        throw new IllegalStateException("Starting inventory exceeds source box capacity");
      for (int i = 0; i < list.size(); i++)
        if (!list.get(i).isEmpty()) saved[i] = CraftItemStack.asBukkitCopy(list.get(i));
    }
    if (contents == null) {
      // Before item components, Barrelbot stored a full inventory inside the display item's custom
      // Items list.
      var legacy = stack.get(DataComponents.CUSTOM_DATA);
      if (legacy != null)
        for (var entry : legacy.copyTag().getListOrEmpty("Items")) {
          if (!(entry instanceof net.minecraft.nbt.CompoundTag raw))
            throw new IllegalStateException("Invalid saved instruction stack");
          int slot = raw.getIntOr("Slot", -1);
          if (slot < 0 || slot >= saved.length)
            throw new IllegalStateException("Invalid saved instruction slot " + slot);
          var old = raw.copy();
          old.remove("Slot");
          old.putInt("DataVersion", 3700);
          try (var bytes = new java.io.ByteArrayOutputStream()) {
            net.minecraft.nbt.NbtIo.writeCompressed(old, bytes);
            saved[slot] = ItemStack.deserializeBytes(bytes.toByteArray());
          } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not upgrade a saved instruction", ex);
          }
        }
    }
    return saved;
  }

  void save(ItemDisplay box, ItemStack[] items) {
    var stack = CraftItemStack.asNMSCopy(box.getItemStack());
    stack.set(
        DataComponents.CONTAINER,
        ItemContainerContents.fromItems(
            Arrays.stream(items).map(CraftItemStack::asNMSCopy).toList()));
    box.setItemStack(CraftItemStack.asBukkitCopy(stack));
  }

  static String list(ItemStack[] items) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (ItemStack item : items)
      if (item != null && !item.isEmpty()) {
        String name =
            item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        var data = Puzzle.itemData(item);
        if (data.contains("cycles")) name += " (" + data.getIntOr("cycles", 0) + " cycles)";
        if (data.contains("condition")) name += " (" + data.getStringOr("condition", "") + ")";
        counts.merge(name, item.getAmount(), Integer::sum);
      }
    return counts.isEmpty() ? "empty" : counts.toString();
  }

  void remember(Puzzle p, ItemDisplay box, String generation) {
    box.getPersistentDataContainer().set(epoch, PersistentDataType.STRING, generation);
    box.getPersistentDataContainer()
        .set(
            new NamespacedKey(app, "origin_world"),
            PersistentDataType.STRING,
            p.world().getUID().toString());
    app.getConfig()
        .set(
            "homes." + home(box),
            List.of(generation, p.area().lo().toString(), p.area().hi().toString()));
  }

  ItemStack[] bind(Puzzle p, ItemDisplay box, ItemStack[] items, String generation) {
    int bot = Puzzle.score(box, "bot_id");
    if (p.id() <= 0 || bot <= 0)
      throw new IllegalStateException("Repair puzzle and box IDs before locking instructions.");
    ItemStack[] result = items.clone();
    for (int i = 0; i < result.length; i++)
      if (result[i] != null && !result[i].isEmpty())
        result[i] = Puzzle.bind(result[i], box, p.id(), bot, home(box), generation);
    return result;
  }

  void legacyAddress(
      World w, ItemDisplay box, net.minecraft.nbt.CompoundTag data, boolean retired) {
    if (data.contains("classroom_home") || !data.getBooleanOr("puzzle_item", false)) return;
    String path =
        "legacy."
            + w.getUID()
            + "."
            + data.getIntOr("puzzle_id", -1)
            + "_"
            + data.getIntOr("instructor_id", -1);
    String coord = Pos.of(box.getLocation()).toString();
    String existing = app.getConfig().getString(path + ".pos", coord);
    app.getConfig().set(path + ".pos", existing.equals(coord) ? coord : "ambiguous");
    if (retired) app.getConfig().set(path + ".retired", true);
  }

  void indexLegacy(ItemDisplay box) {
    var nms = CraftItemStack.asNMSCopy(box.getItemStack()).get(DataComponents.CUSTOM_DATA);
    if (nms == null) return;
    for (var t : nms.copyTag().getListOrEmpty("Items"))
      if (t instanceof net.minecraft.nbt.CompoundTag old) {
        var d = old.getCompoundOrEmpty("tag").getCompoundOrEmpty("barrelbot");
        if (d.contains("instructor_id")) {
          legacyAddress(box.getWorld(), box, d, false);
          break;
        }
      }
  }

  void lock(Puzzle p, ItemDisplay box, ItemStack[] items) {
    for (var item : items) legacyAddress(p.world(), box, Puzzle.itemData(item), true);
    String generation = UUID.randomUUID().toString();
    ItemStack[] bound = bind(p, box, items, generation);
    save(box, bound);
    Puzzle.container(box).getInventory().setContents(bound);
    remember(p, box, generation);
  }

  static ItemStack[] plus(ItemStack[] content, ItemStack addition) {
    var test = Bukkit.createInventory(null, content.length);
    test.setContents(
        Arrays.stream(content).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new));
    if (!test.addItem(addition.clone()).isEmpty())
      throw new IllegalStateException("Source box lacks space for the addition");
    return test.getContents();
  }

  void add(Puzzle p, ItemDisplay box, List<ItemStack> additions, boolean apply) {
    String gen = epoch(box);
    if (gen.isEmpty()) gen = UUID.randomUUID().toString();
    ItemStack[] saved = baseline(box), live = Puzzle.container(box).getInventory().getContents();
    if (epoch(box).isEmpty() && Arrays.stream(saved).allMatch(Objects::isNull)) {
      if (Arrays.stream(live).anyMatch(Objects::nonNull))
        throw new IllegalStateException(
            "Lock the existing source box before adding to its starting inventory");
    }
    ItemStack[] next = saved, now = live;
    for (var extra : bind(p, box, additions.toArray(ItemStack[]::new), gen)) {
      next = plus(next, extra);
      now = plus(now, extra);
    }
    if (apply) {
      save(box, next);
      Puzzle.container(box).getInventory().setContents(now);
      remember(p, box, gen);
      if (!Arrays.equals(next, baseline(box))
          || !Arrays.equals(now, Puzzle.container(box).getInventory().getContents()))
        throw new IllegalStateException(
            "Instruction quantities failed verification at " + Pos.of(box.getLocation()));
    }
  }

  boolean owned(ItemStack item, ItemDisplay box, Puzzle p) {
    var d = Puzzle.itemData(item);
    if (d.contains("classroom_home")) return home(box).equals(d.getStringOr("classroom_home", ""));
    return d.getBooleanOr("puzzle_item", false)
        && d.getIntOr("puzzle_id", -1) == p.id()
        && d.getIntOr("instructor_id", -1) == Puzzle.score(box, "bot_id");
  }

  void purge(Inventory inv, Puzzle p, ItemDisplay box) {
    for (int i = 0; i < inv.getSize(); i++) if (owned(inv.getItem(i), box, p)) inv.setItem(i, null);
  }

  void recall(Puzzle p, List<ItemDisplay> boxes) {
    if (boxes.isEmpty()) throw new IllegalStateException("This puzzle has no source box.");
    Map<ItemDisplay, ItemStack[]> saved = new LinkedHashMap<>();
    for (ItemDisplay box : boxes) {
      ItemStack[] items = baseline(box);
      if (epoch(box).isEmpty() && Arrays.stream(items).allMatch(Objects::isNull))
        throw new IllegalStateException("The builder must lock the source inventory first.");
      bind(p, box, items, "validation");
      saved.put(box, items);
    }
    if (p.manager().getScoreboardTags().contains(Puzzle.MANAGER + ".active"))
      app.function(p.manager(), "manager/stop_execution");
    for (ItemDisplay box : boxes) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (!player.getWorld().equals(p.world())) continue;
        purge(player.getInventory(), p, box);
        if (owned(player.getItemOnCursor(), box, p)) player.setItemOnCursor(null);
      }
      for (Entity e : p.area().entities(p.world()))
        if (e instanceof Item drop && owned(drop.getItemStack(), box, p)) drop.remove();
      for (int x = p.area().lo().x() >> 4; x <= p.area().hi().x() >> 4; x++)
        for (int z = p.area().lo().z() >> 4; z <= p.area().hi().z() >> 4; z++)
          for (var tile : p.world().getChunkAt(x, z).getTileEntities())
            if (tile instanceof Container c && p.area().contains(Pos.of(c.getLocation())))
              purge(c.getInventory(), p, box);
      lock(p, box, saved.get(box));
    }
    app.saveConfig();
  }

  void returnLegacy(Player p, int slot, ItemStack item, net.minecraft.nbt.CompoundTag d) {
    String key =
        "legacy."
            + p.getWorld().getUID()
            + "."
            + d.getIntOr("puzzle_id", -1)
            + "_"
            + d.getIntOr("instructor_id", -1);
    String target = app.getConfig().getString(key + ".pos");
    if (target == null || target.equals("ambiguous")) return;
    var loc = pos(target).at(p.getWorld());
    loc.getChunk().getEntities();
    var box =
        p.getWorld().getNearbyEntities(loc.clone().add(.5, .5, .5), 1, 1, 1).stream()
            .filter(
                e ->
                    e.getScoreboardTags().contains(Puzzle.BOX)
                        && Pos.of(e.getLocation()).equals(Pos.of(loc)))
            .toList();
    if (box.size() != 1 || !(loc.getBlock().getState() instanceof Container c)) return;
    if (app.getConfig().getBoolean(key + ".retired")) {
      p.getInventory().setItem(slot, null);
      return;
    }
    var candidates =
        p.getWorld().getNearbyEntities(loc, 64, 64, 64).stream()
            .filter(e -> e.getScoreboardTags().contains(Puzzle.MANAGER))
            .map(Puzzle::read)
            .filter(q -> q.area().contains(Pos.of(loc)))
            .toList();
    if (candidates.size() != 1 || candidates.getFirst().area().contains(Pos.of(p.getLocation())))
      return;
    var leftovers = c.getInventory().addItem(item.clone());
    p.getInventory().setItem(slot, leftovers.get(0));
  }

  static Pos pos(String s) {
    String[] v = s.split("[ ,]");
    return new Pos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
  }

  void check(Player player) {
    check(player, false);
  }

  void check(Player player, boolean leavingWorld) {
    var inv = player.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (item == null) continue;
      var d = Puzzle.itemData(item);
      String home = d.getStringOr("classroom_home", "");
      if (home.isEmpty()) {
        if (d.getBooleanOr("puzzle_item", false)) returnLegacy(player, i, item, d);
        continue;
      }
      if (!home.startsWith(player.getWorld().getUID() + "@")) continue;
      var row = app.getConfig().getStringList("homes." + home);
      if (row.size() != 3) continue;
      if (!row.getFirst().equals(d.getStringOr("classroom_generation", ""))) {
        inv.setItem(i, null);
        continue;
      }
      String[] address = home.split("@");
      World w = player.getWorld();
      if (!leavingWorld
          && new Area(pos(row.get(1)), pos(row.get(2))).contains(Pos.of(player.getLocation())))
        continue;
      Location shelf = pos(address[1]).at(w);
      shelf.getChunk().getEntities();
      var boxes =
          w.getNearbyEntities(shelf.clone().add(.5, .5, .5), .6, .6, .6).stream()
              .filter(
                  e ->
                      e instanceof ItemDisplay box
                          && e.getScoreboardTags().contains(Puzzle.BOX)
                          && home(box).equals(home)
                          && epoch(box).equals(row.getFirst()))
              .toList();
      if (boxes.size() != 1 || !(shelf.getBlock().getState() instanceof Container c)) continue;
      var leftovers = c.getInventory().addItem(item.clone());
      inv.setItem(i, leftovers.get(0));
      if (leftovers.isEmpty())
        player.sendMessage(
            "Returned "
                + item.getAmount()
                + " instruction(s) to puzzle #"
                + d.getIntOr("puzzle_id", 0)
                + ".");
    }
  }
}
