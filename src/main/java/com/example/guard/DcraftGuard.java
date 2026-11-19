package com.example.guard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DcraftGuard extends JavaPlugin implements CommandExecutor, Listener {

    // Hile Kontrol Haritaları
    private final Map<UUID, Long> flyBypass = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Integer> cpsCounter = new HashMap<>();
    private final Map<UUID, List<String>> chatHistory = new HashMap<>();
    private final Map<UUID, Long> totemCooldown = new HashMap<>();
    
    // Küfür ve Mute Sistemi
    private final Map<UUID, Integer> uyariSayisi = new HashMap<>();
    private final Map<UUID, Long> muteBitisZamani = new HashMap<>();
    private final List<String> kufurler = new ArrayList<>();
    private final List<String> beyazliste = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kufurler.addAll(getConfig().getStringList("kufurler"));
        beyazliste.addAll(getConfig().getStringList("beyazliste"));

        if (getCommand("guard") != null) getCommand("guard").setExecutor(this);
        if (getCommand("sikayet") != null) getCommand("sikayet").setExecutor(this);
        if (getCommand("mute") != null) getCommand("mute").setExecutor(this);
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("DcraftGuard (Anti-Cheat + Kufur) aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("guard")) {
            if (!(sender instanceof Player p) || !p.hasPermission("dcraft.guard.admin")) return true;
            p.sendMessage("§8§m-----------------------------------");
            p.sendMessage("§6§lDcraftGuard Durumu");
            p.sendMessage("§7Aktif Korumalar: §aHız, Uçuş, Reach, KillAura, Spam, Kufur, Mute");
            p.sendMessage("§7Mute Sistemi: §e3 Hak");
            p.sendMessage("§8§m-----------------------------------");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mute")) {
            if (!(sender instanceof Player p) || !p.hasPermission("dcraft.guard.admin")) return true;
            if (args.length < 1) { p.sendMessage("§cKullanım: /mute <oyuncu>"); return true; }
            
            Player hedef = Bukkit.getPlayer(args[0]);
            if (hedef == null) { p.sendMessage("§cOyuncu bulunamadı."); return true; }
            
            int sure = getConfig().getInt("ayarlar.mute_suresi_saniye", 60);
            muteBitisZamani.put(hedef.getUniqueId(), System.currentTimeMillis() + (sure * 1000L));
            hedef.sendMessage("§cYetkili tarafından " + sure + " saniye susturuldunuz.");
            p.sendMessage("§a" + hedef.getName() + " adlı oyuncu " + sure + " saniye susturuldu.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sikayet")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage("§cKullanım: /sikayet <oyuncu>"); return true; }
            
            Player hedef = Bukkit.getPlayer(args[0]);
            if (hedef == null) { p.sendMessage("§cOyuncu bulunamadı."); return true; }
            
            // Yetkililere tıklanabilir mesaj gönder
            Component sikayetMsg = Component.text("§8[§cŞikayet§8] §e" + p.getName() + " §7adlı oyuncu §c" + hedef.getName() + " §7adlı oyuncuyu şikayet etti! ")
                .append(Component.text("[MUTE AT]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/mute " + hedef.getName())))
                .append(Component.text(" "))
                .append(Component.text("[GEREK YOK]").color(NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand("/guard")));
            
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("dcraft.guard.admin")) {
                    online.sendMessage(sikayetMsg);
                }
            }
            p.sendMessage("§aŞikayetiniz yetkililere iletildi.");
            return true;
        }
        return false;
    }

    // --- HAREKET KONTROLLERİ (FLY, SPEED, NOFALL) ---
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("dcraft.guard.bypass") || p.isFlying() || p.getAllowFlight()) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double hiz = Math.sqrt(dx * dx + dz * dz);
        
        if (p.isOnGround() && hiz > 0.45) {
            p.teleport(from);
            return;
        }

        if (!p.isOnGround() && !p.isInsideVehicle() && !p.isSwimming() && !p.isGliding()) {
            long now = System.currentTimeMillis();
            long lastMove = lastMoveTime.getOrDefault(p.getUniqueId(), now);
            double lastYVal = lastY.getOrDefault(p.getUniqueId(), to.getY());

            if (now - lastMove > 3000 && Math.abs(to.getY() - lastYVal) < 0.1) {
                p.teleport(from);
                lastMoveTime.put(p.getUniqueId(), now);
            } else {
                lastMoveTime.put(p.getUniqueId(), now);
                lastY.put(p.getUniqueId(), to.getY());
            }
        } else {
            lastMoveTime.remove(p.getUniqueId());
            lastY.remove(p.getUniqueId());
        }

        if (p.getFallDistance() > 3.0 && p.isOnGround()) {
            p.damage(2.0);
        }
    }

    // --- SAVAŞ KONTROLLERİ (REACH, KILL AURA) ---
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.hasPermission("dcraft.guard.bypass")) return;

        double mesafe = p.getLocation().distance(e.getEntity().getLocation());
        if (mesafe > 4.5) {
            e.setCancelled(true);
            return;
        }

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastAttackTime.getOrDefault(uuid, 0L);
        
        if (now - last < 200) {
            int cps = cpsCounter.getOrDefault(uuid, 0) + 1;
            cpsCounter.put(uuid, cps);
            if (cps > 8) {
                e.setCancelled(true);
                cpsCounter.put(uuid, 0);
                return;
            }
        } else {
            cpsCounter.put(uuid, 0);
        }
        lastAttackTime.put(uuid, now);
    }

    // --- AUTO TOTEM KONTROLÜ ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (p.hasPermission("dcraft.guard.bypass")) return;

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return;

        if (e.getSlot() == 40 || e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR) {
            long now = System.currentTimeMillis();
            long lastTotem = totemCooldown.getOrDefault(p.getUniqueId(), 0L);
            
            if (now - lastTotem < 1000) {
                e.setCancelled(true);
            } else {
                totemCooldown.put(p.getUniqueId(), now);
            }
        }
    }

    // --- SOHBET KONTROLÜ (SPAM VE KUFUR) ---
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("dcraft.guard.bypass")) return;

        // Mute Kontrolü
        if (muteBitisZamani.containsKey(p.getUniqueId())) {
            if (System.currentTimeMillis() < muteBitisZamani.get(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage("§cSusturulmuşsunuz! Kalan süre: " + ((muteBitisZamani.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000) + " saniye.");
                return;
            } else {
                muteBitisZamani.remove(p.getUniqueId());
            }
        }

        String msg = e.getMessage().toLowerCase();
        UUID uuid = p.getUniqueId();

        // Spam Kontrolü
        List<String> history = chatHistory.getOrDefault(uuid, new ArrayList<>());
        int tekrar = 0;
        for (String s : history) {
            if (s.equalsIgnoreCase(msg)) tekrar++;
        }
        if (tekrar >= 2) {
            e.setCancelled(true);
            p.sendMessage("§cSpam yapmayı bırak!");
            return;
        }

        if (msg.length() > 5 && msg.equals(msg.toUpperCase())) {
            e.setCancelled(true);
            p.sendMessage("§cLütfen büyük harf kullanmayı bırak!");
            return;
        }

        // Küfür Kontrolü
        for (String kufur : kufurler) {
            if (msg.contains(kufur)) {
                // Beyaz liste kontrolü (örneğin "lan" kelimesi geçiyorsa küfür sayma)
                boolean beyazListede = false;
                for (String beyaz : beyazliste) {
                    if (msg.contains(beyaz)) {
                        beyazListede = true;
                        break;
                    }
                }
                if (beyazListede) continue;

                e.setCancelled(true);
                int uyari = uyariSayisi.getOrDefault(uuid, 0) + 1;
                uyariSayisi.put(uuid, uyari);

                int maxUyari = getConfig().getInt("ayarlar.max_uyari", 3);
                int muteSuresi = getConfig().getInt("ayarlar.mute_suresi_saniye", 60);

                if (uyari >= maxUyari) {
                    muteBitisZamani.put(uuid, System.currentTimeMillis() + (muteSuresi * 1000L));
                    uyariSayisi.put(uuid, 0);
                    p.sendMessage("§cKüfür sınırını aştığınız için " + muteSuresi + " saniye susturuldunuz!");
                    
                    Component muteMsg = Component.text("§8[§cMute§8] §e" + p.getName() + " §7küfür nedeniyle otomatik olarak susturuldu.");
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.hasPermission("dcraft.guard.admin")) online.sendMessage(muteMsg);
                    }
                } else {
                    p.sendMessage("§cKüfür etmeyin! Uyarı: §e" + uyari + "§c/§e" + maxUyari);
                    Component uyariMsg = Component.text("§8[§cUyarı§8] §e" + p.getName() + " §7küfür etti: §f" + msg + " ")
                        .append(Component.text("[MUTE AT]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/mute " + p.getName())))
                        .append(Component.text(" "))
                        .append(Component.text("[GEREK YOK]").color(NamedTextColor.GRAY)
                            .clickEvent(ClickEvent.runCommand("/guard")));
                    
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.hasPermission("dcraft.guard.admin")) online.sendMessage(uyariMsg);
                    }
                }
                return;
            }
        }

        history.add(msg);
        if (history.size() > 5) history.remove(0);
        chatHistory.put(uuid, history);
    }
        }
