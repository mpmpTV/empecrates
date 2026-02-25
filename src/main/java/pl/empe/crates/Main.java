package pl.empe.crates;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Location, String> activeCrates = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        if (getCommand("skrzynia") != null) {
            getCommand("skrzynia").setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(color(" &6&m---------------------------------------"));
        Bukkit.getConsoleSender().sendMessage(color("    &e&lEmpeCrates &fv" + getDescription().getVersion()));
        Bukkit.getConsoleSender().sendMessage(color("    &aPlugin zostal poprawnie uruchomiony!"));
        Bukkit.getConsoleSender().sendMessage(color("    &7Autor: &fempe"));
        Bukkit.getConsoleSender().sendMessage(color(" &6&m---------------------------------------"));
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void loadData() {
        activeCrates.clear();
        ConfigurationSection locSection = getConfig().getConfigurationSection("locations");
        if (locSection != null) {
            for (String key : locSection.getKeys(false)) {
                String worldName = getConfig().getString("locations." + key + ".world");
                if (worldName == null || Bukkit.getWorld(worldName) == null) continue;

                double x = getConfig().getDouble("locations." + key + ".x");
                double y = getConfig().getDouble("locations." + key + ".y");
                double z = getConfig().getDouble("locations." + key + ".z");
                String type = getConfig().getString("locations." + key + ".type");

                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                activeCrates.put(loc, type);
                updateHologram(loc, type);
            }
        }
    }

    private void updateHologram(Location loc, String type) {
        String holoName = getHoloName(loc);
        String displayName = color(getConfig().getString("crates." + type + ".display_name", "&c&lSKRZYNIA"));
        Location holoLoc = loc.clone().add(0.5, 2.5, 0.5);

        try {
            if (DHAPI.getHologram(holoName) != null) DHAPI.removeHologram(holoName);
            DHAPI.createHologram(holoName, holoLoc, Arrays.asList(displayName, color("&7(Kliknij PPM, aby otworzyc)")));
        } catch (NoClassDefFoundError e) {
            Bukkit.getConsoleSender().sendMessage(color("&c[EmpeCrates] Blad: Brak DecentHolograms!"));
        }
    }

    private String getHoloName(Location loc) {
        return "crate_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private ItemStack createKey(String crateType) {
        String path = "crates." + crateType + ".key";
        Material mat = Material.valueOf(getConfig().getString(path + ".material", "TRIPWIRE_HOOK"));
        ItemStack key = new ItemStack(mat);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName(color(getConfig().getString(path + ".name", "&eKlucz " + crateType)));

        List<String> lore = new ArrayList<>();
        for (String s : getConfig().getStringList(path + ".lore")) {
            lore.add(color(s));
        }
        meta.setLore(lore);
        key.setItemMeta(meta);
        return key;
    }

    private List<ItemStack> getRewards(String crateType) {
        List<ItemStack> rewards = new ArrayList<>();
        List<String> list = getConfig().getStringList("crates." + crateType + ".rewards");
        for (String s : list) {
            try {
                String[] p = s.split(";");
                ItemStack item = new ItemStack(Material.valueOf(p[0]), Integer.parseInt(p[2]), (short) Integer.parseInt(p[1]));
                ItemMeta m = item.getItemMeta();
                m.setDisplayName(color(p[3]));
                item.setItemMeta(m);

                if (p.length >= 5 && !p[4].isEmpty()) {
                    String[] enchants = p[4].split(",");
                    for (String enchData : enchants) {
                        String[] enchParts = enchData.split(":");
                        Enchantment enchantment = Enchantment.getByName(enchParts[0].toUpperCase());
                        if (enchantment != null) {
                            item.addUnsafeEnchantment(enchantment, Integer.parseInt(enchParts[1]));
                        }
                    }
                }
                rewards.add(item);
            } catch (Exception e) {
                getLogger().severe("Blad w formacie nagrody: " + s);
            }
        }
        return rewards;
    }

    private ItemStack getRandomReward(String crateType) {
        List<String> list = getConfig().getStringList("crates." + crateType + ".rewards");
        double totalWeight = 0;
        for (String s : list) {
            String[] p = s.split(";");
            totalWeight += (p.length >= 6) ? Double.parseDouble(p[5]) : 1.0;
        }
        double r = random.nextDouble() * totalWeight;
        double countWeight = 0;
        for (String s : list) {
            String[] p = s.split(";");
            double weight = (p.length >= 6) ? Double.parseDouble(p[5]) : 1.0;
            countWeight += weight;
            if (countWeight >= r) {
                ItemStack item = new ItemStack(Material.valueOf(p[0]), Integer.parseInt(p[2]), (short) Integer.parseInt(p[1]));
                ItemMeta m = item.getItemMeta();
                m.setDisplayName(color(p[3]));
                item.setItemMeta(m);
                if (p.length >= 5 && !p[4].isEmpty()) {
                    for (String ench : p[4].split(",")) {
                        String[] ep = ench.split(":");
                        item.addUnsafeEnchantment(Enchantment.getByName(ep[0].toUpperCase()), Integer.parseInt(ep[1]));
                    }
                }
                return item;
            }
        }
        return getRewards(crateType).get(0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("crates.admin")) {
            sender.sendMessage(color("&cBlad: Nie posiadasz uprawnien!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(color("&6--- EmpeCrates Menu ---"));
            sender.sendMessage(color("&e/skrzynia ustaw <typ> &7- Ustawia skrzynie"));
            sender.sendMessage(color("&e/skrzynia usun &7- Usuwa skrzynie na ktora patrzysz"));
            sender.sendMessage(color("&e/skrzynia klucz <gracz> <typ> <ilosc> &7- Daje klucz graczowi"));
            sender.sendMessage(color("&e/skrzynia rozdajklucz <typ> <ilosc> &7- Daje klucze kazdemu"));
            sender.sendMessage(color("&e/skrzynia reload &7- Przeladowuje plugin"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadData();
            sender.sendMessage(color("&aPlugin zostal przeladowany!"));
            return true;
        }

        if (args[0].equalsIgnoreCase("rozdajklucz") && args.length >= 3) {
            String type = args[1].toLowerCase();
            if (!getConfig().contains("crates." + type)) return true;
            int amount = Integer.parseInt(args[2]);
            ItemStack keyItem = createKey(type);
            keyItem.setAmount(amount);
            for (Player p : Bukkit.getOnlinePlayers()) p.getInventory().addItem(keyItem.clone());
            Bukkit.broadcastMessage(color("&8&l> &eWszyscy gracze otrzymali &f" + amount + "x " + keyItem.getItemMeta().getDisplayName() + "&e!"));
            return true;
        }

        if (args[0].equalsIgnoreCase("usun") && sender instanceof Player) {
            Player p = (Player) sender;
            Block target = p.getTargetBlock((Set<Material>) null, 5);
            if (target != null && activeCrates.containsKey(target.getLocation())) {
                Location loc = target.getLocation();
                activeCrates.remove(loc);
                getConfig().set("locations.loc_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ(), null);
                saveConfig();
                try { if (DHAPI.getHologram(getHoloName(loc)) != null) DHAPI.removeHologram(getHoloName(loc)); } catch (Exception ignored) {}
                p.sendMessage(color("&aSkrzynia zostala pomyslnie usunieta!"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("ustaw") && args.length >= 2 && sender instanceof Player) {
            String type = args[1].toLowerCase();
            if (!getConfig().contains("crates." + type)) return true;
            Player p = (Player) sender;
            Block target = p.getTargetBlock((Set<Material>) null, 5);
            if (target != null && target.getType() != Material.AIR) {
                Location loc = target.getLocation();
                activeCrates.put(loc, type);
                String k = "locations.loc_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
                getConfig().set(k + ".world", loc.getWorld().getName());
                getConfig().set(k + ".x", (double)loc.getBlockX());
                getConfig().set(k + ".y", (double)loc.getBlockY());
                getConfig().set(k + ".z", (double)loc.getBlockZ());
                getConfig().set(k + ".type", type);
                saveConfig();
                updateHologram(loc, type);
                p.sendMessage(color("&aSkrzynia &f" + type + " &azostala ustawiona!"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("klucz") && args.length >= 4) {
            Player target = Bukkit.getPlayer(args[1]);
            String type = args[2].toLowerCase();
            if (target != null && getConfig().contains("crates." + type)) {
                int amount = Integer.parseInt(args[3]);
                ItemStack keys = createKey(type);
                keys.setAmount(amount);
                target.getInventory().addItem(keys);
                sender.sendMessage(color("&aDano &f" + amount + "x &akluczy dla &f" + target.getName()));
            }
            return true;
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String path = "refunds." + p.getUniqueId();
        if (getConfig().contains(path)) {
            for (String type : getConfig().getStringList(path)) {
                p.getInventory().addItem(createKey(type));
                p.sendMessage(color("&6&l[!] &eOddano klucz &f" + type + "&e za wyjscie!"));
            }
            getConfig().set(path, null);
            saveConfig();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;
        ConfigurationSection crates = getConfig().getConfigurationSection("crates");
        if (crates != null) {
            for (String type : crates.getKeys(false)) {
                if (item.getItemMeta().getDisplayName().equals(color(getConfig().getString("crates." + type + ".key.name")))) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(color("&cNie mozesz stawiac kluczy!"));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        if (!activeCrates.containsKey(loc)) return;

        e.setCancelled(true);
        String type = activeCrates.get(loc);
        Player p = e.getPlayer();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            openPreview(p, type);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = p.getItemInHand();
            ItemStack requiredKey = createKey(type);
            if (hand != null && hand.hasItemMeta() && hand.getItemMeta().getDisplayName().equals(requiredKey.getItemMeta().getDisplayName())) {
                if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
                else p.setItemInHand(null);
                startRoulette(p, type);
            } else {
                p.sendMessage(color("&cPotrzebujesz klucza: " + requiredKey.getItemMeta().getDisplayName()));
            }
        }
    }

    private void openPreview(Player p, String type) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Podglad: " + type));
        List<String> list = getConfig().getStringList("crates." + type + ".rewards");
        double total = 0;
        for (String s : list) {
            String[] p_ = s.split(";");
            total += (p_.length >= 6) ? Double.parseDouble(p_[5]) : 1.0;
        }
        for (String s : list) {
            try {
                String[] p_ = s.split(";");
                ItemStack item = new ItemStack(Material.valueOf(p_[0]), Integer.parseInt(p_[2]), (short) Integer.parseInt(p_[1]));
                ItemMeta m = item.getItemMeta();
                m.setDisplayName(color(p_[3]));
                double weight = (p_.length >= 6) ? Double.parseDouble(p_[5]) : 1.0;
                double percent = (weight / total) * 100.0;
                List<String> lore = new ArrayList<>();
                lore.add(color("&7Szansa: &e" + String.format("%.2f", percent) + "%"));
                m.setLore(lore);
                if (p_.length >= 5 && !p_[4].isEmpty()) {
                    for (String ench : p_[4].split(",")) {
                        String[] ep = ench.split(":");
                        m.addEnchant(Enchantment.getByName(ep[0].toUpperCase()), Integer.parseInt(ep[1]), true);
                    }
                }
                item.setItemMeta(m);
                inv.addItem(item);
            } catch (Exception ignored) {}
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title.contains("Losowanie") || title.contains("Podglad") || title.contains("SKRZYNIA")) e.setCancelled(true);
    }

    private void startRoulette(Player p, String type) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("crates." + type + ".gui_title", "&8Losowanie...")));
        ItemStack border = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 15);
        ItemMeta bm = border.getItemMeta(); bm.setDisplayName(" "); border.setItemMeta(bm);
        ItemStack pointer = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 4);
        ItemMeta pm = pointer.getItemMeta(); pm.setDisplayName(color("&e&lWYGRANA")); pointer.setItemMeta(pm);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17) {
                if (i == 4 || i == 22) inv.setItem(i, pointer);
                else inv.setItem(i, border);
            }
        }
        p.openInventory(inv);
        runAnimation(p, inv, type, 0, 2L);
    }

    private void runAnimation(Player p, Inventory inv, String type, int ticks, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() == null) {
                    if (!p.isOnline()) {
                        List<String> refunds = getConfig().getStringList("refunds." + p.getUniqueId());
                        refunds.add(type);
                        getConfig().set("refunds." + p.getUniqueId(), refunds);
                        saveConfig();
                    }
                    return;
                }
                for (int i = 9; i < 17; i++) inv.setItem(i, inv.getItem(i + 1));
                inv.setItem(17, getRandomReward(type));
                p.playSound(p.getLocation(), Sound.CLICK, 1f, 1f);
                int nt = ticks + 1;
                long nd = (nt > 25) ? (nt > 33 ? delay + 2 : delay + 1) : delay;
                if (nt < 40) runAnimation(p, inv, type, nt, nd);
                else {
                    ItemStack won = inv.getItem(13);
                    p.playSound(p.getLocation(), Sound.LEVEL_UP, 1f, 1f);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline()) {
                                p.getInventory().addItem(won.clone());
                                Bukkit.broadcastMessage(color("&8&l> &7Gracz &f" + p.getName() + " &7wygral " + won.getItemMeta().getDisplayName() + " &7ze skrzyni " + type));
                                p.closeInventory();
                            }
                        }
                    }.runTaskLater(Main.this, 40L);
                }
            }
        }.runTaskLater(this, delay);
    }

    private String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}