package com.bendemen.eywaportal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EywaPortalPlugin extends JavaPlugin implements Listener {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, BukkitTask> activeTransitions = new HashMap<>();
    private final Set<UUID> dispatchingWarp = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EywaPortal enabled. Fullscreen image overlays are not included in this initial build.");
    }

    @Override
    public void onDisable() {
        activeTransitions.values().forEach(BukkitTask::cancel);
        activeTransitions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!getConfig().getBoolean("warp-command.enabled", true)) return;

        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message.length() < 2) return;

        String[] split = message.substring(1).trim().split("\\s+");
        if (split.length < 2) return; // Leave /warp with no arguments unchanged.

        String label = split[0].toLowerCase(Locale.ROOT);
        List<String> aliases = getConfig().getStringList("warp-command.aliases");
        if (aliases.stream().map(s -> s.toLowerCase(Locale.ROOT)).noneMatch(label::equals)) return;
        if (dispatchingWarp.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        cancelTransition(player, false);
        startTransition(player, message.substring(1).trim());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelTransition(event.getPlayer(), true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("eywaportal")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be run by a player.");
            return true;
        }
        if (!player.hasPermission("eywaportal.admin")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            player.sendMessage(prefix().append(text(" <#55FFAA>Configuration reloaded.")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("test")) {
            startTransition(player, null);
            return true;
        }
        player.sendMessage(miniMessage.deserialize("<#55FFFF>/eywaportal reload</#55FFFF> <gray>|</gray> Reload configuration"));
        player.sendMessage(miniMessage.deserialize("<#55FFFF>/eywaportal test</#55FFFF> <gray>|</gray> Preview portal particles and transition"));
        return true;
    }

    private void startTransition(Player player, String commandToRun) {
        UUID id = player.getUniqueId();
        int seconds = Math.max(1, getConfig().getInt("warp-command.countdown-seconds", 3));
        player.sendMessage(prefix().append(text(" " + getConfig().getString("messages.starting", "<gray>Eywa Portal opening..."))));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);

        final int[] remaining = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline()) {
                cancelTransition(player, true);
                return;
            }
            if (remaining[0] <= 0) {
                BukkitTask running = activeTransitions.remove(id);
                if (running != null) running.cancel();
                if (commandToRun != null) {
                    dispatchingWarp.add(id);
                    try {
                        Bukkit.dispatchCommand(player, commandToRun);
                    } finally {
                        dispatchingWarp.remove(id);
                    }
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            playBurst(player.getLocation());
                            showArrival(player);
                        }
                    }, 2L);
                } else {
                    playBurst(player.getLocation());
                    showArrival(player);
                }
                if (getConfig().getBoolean("screen-transition.darkness-effect", true)) {
                    int ticks = Math.max(1, getConfig().getInt("screen-transition.darkness-ticks", 30));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
                }
                return;
            }
            showCountdown(player, remaining[0]);
            playSpiral(player.getLocation(), remaining[0]);
            remaining[0]--;
        }, 0L, 20L);
        activeTransitions.put(id, task);
    }

    private void showCountdown(Player player, int seconds) {
        if (!getConfig().getBoolean("screen-transition.enabled", true)) return;
        String title = getConfig().getString("screen-transition.title", "<#55FFFF><bold>EYWA PORTAL</bold>");
        String subtitle = getConfig().getString("screen-transition.subtitle", "<#B7F7FF>Opening in <seconds>...")
                .replace("<seconds>", Integer.toString(seconds));
        player.showTitle(net.kyori.adventure.title.Title.title(
                miniMessage.deserialize(title), miniMessage.deserialize(subtitle)));
    }

    private void showArrival(Player player) {
        if (!getConfig().getBoolean("screen-transition.enabled", true)) return;
        player.showTitle(net.kyori.adventure.title.Title.title(
                miniMessage.deserialize(getConfig().getString("screen-transition.arrival-title", "<#55FFFF><bold>PORTAL OPENED</bold>")),
                miniMessage.deserialize(getConfig().getString("screen-transition.arrival-subtitle", "<#D9FFFF>You have arrived."))));
    }

    private void playSpiral(Location base, int seconds) {
        double radius = getConfig().getDouble("visuals.radius", 1.15);
        double height = getConfig().getDouble("visuals.height", 2.2);
        double speed = getConfig().getDouble("visuals.rotation-speed", 0.28);
        int count = Math.max(1, getConfig().getInt("visuals.particle-count-per-tick", 18));
        List<String> configured = getConfig().getStringList("visuals.particles");
        Particle[] particles = configured.stream().map(this::parseParticle).filter(p -> p != null).toArray(Particle[]::new);
        if (particles.length == 0) particles = new Particle[]{Particle.END_ROD, Particle.WITCH};
        double time = System.currentTimeMillis() / 100.0 + seconds * speed;
        for (int i = 0; i < count; i++) {
            double progress = (i / (double) count + time * speed) % 1.0;
            double angle = progress * Math.PI * 2.0;
            double y = 0.15 + progress * height;
            Location point = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            try {
                base.getWorld().spawnParticle(particles[i % particles.length], point, 1, 0.02, 0.02, 0.02, 0.0);
            } catch (IllegalArgumentException ignored) {
                // Ignore particle types not supported by the current server version.
            }
        }
        base.getWorld().playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.12f, 1.5f);
    }

    private void playBurst(Location location) {
        int count = Math.max(1, getConfig().getInt("visuals.arrival-burst-count", 45));
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), count, 0.8, 1.0, 0.8, 0.04);
        location.getWorld().spawnParticle(Particle.WITCH, location.clone().add(0, 1, 0), count / 2, 0.7, 0.8, 0.7, 0.02);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private void cancelTransition(Player player, boolean silent) {
        BukkitTask task = activeTransitions.remove(player.getUniqueId());
        if (task != null) task.cancel();
        if (!silent && player.isOnline()) {
            player.sendMessage(prefix().append(text(" " + getConfig().getString("messages.cancelled", "<red>Transition cancelled."))));
        }
    }

    private Component prefix() {
        return miniMessage.deserialize(getConfig().getString("messages.prefix", "<#5555FF><bold>EywaMC</bold> <reset><#555555>|"));
    }

    private Component message(String key) {
        return prefix().append(text(" " + getConfig().getString("messages." + key, "<red>" + key)));
    }

    private Component text(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }
}
