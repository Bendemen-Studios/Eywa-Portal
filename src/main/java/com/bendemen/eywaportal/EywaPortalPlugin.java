package com.bendemen.eywaportal;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
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
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
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
    private final Map<UUID, BukkitTask> overlayAnimations = new HashMap<>();
    private final Set<UUID> dispatchingWarp = new HashSet<>();
    private final Set<UUID> resourcePackLoaded = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EywaPortal enabled. Java fullscreen overlay activates when the merged resource pack is loaded.");
        getLogger().info("Bedrock players currently receive the compatible title/particle fallback.");
    }

    @Override
    public void onDisable() {
        activeTransitions.values().forEach(BukkitTask::cancel);
        overlayAnimations.values().forEach(BukkitTask::cancel);
        activeTransitions.clear();
        overlayAnimations.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!getConfig().getBoolean("warp-command.enabled", true)) return;

        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message.length() < 2) return;

        String[] split = message.substring(1).trim().split("\\s+");
        String label = split[0].toLowerCase(Locale.ROOT);
        List<String> warpAliases = getConfig().getStringList("warp-command.aliases");
        List<String> portalCommands = getConfig().getStringList("portal-commands.aliases");
        boolean isWarp = warpAliases.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(label::equals);
        boolean isPortalCommand = portalCommands.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(label::equals);

        if (!isWarp && !isPortalCommand) return;
        // Warp needs a destination argument; /home and /spawn are valid without one.
        if (isWarp && split.length < 2) return;
        if (label.equals("tpa") && split.length < 2) return;
        if (dispatchingWarp.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        cancelTransition(player, false);
        // Keep the full original command (arguments included), without the leading slash.
        startTransition(player, message.substring(1).trim());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelTransition(event.getPlayer(), true);
        resourcePackLoaded.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            resourcePackLoaded.add(id);
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            resourcePackLoaded.remove(id);
        }
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
        playVEnginePortal(player.getLocation(), seconds * 20 + 20);

        final int[] remaining = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline()) {
                cancelTransition(player, true);
                return;
            }
            if (remaining[0] <= 0) {
                BukkitTask running = activeTransitions.remove(id);
                if (running != null) running.cancel();

                if (!shouldUseFullscreenOverlay(player)
                        && getConfig().getBoolean("screen-transition.darkness-effect", true)) {
                    int ticks = Math.max(1, getConfig().getInt("screen-transition.darkness-ticks", 30));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
                }

                if (commandToRun != null) {
                    dispatchingWarp.add(id);
                    try {
                        Bukkit.dispatchCommand(player, commandToRun);
                    } finally {
                        dispatchingWarp.remove(id);
                    }
                }

                Runnable arrival = () -> {
                    if (!player.isOnline()) return;
                    playBurst(player.getLocation());
                    showArrival(player);
                };
                if (shouldUseFullscreenOverlay(player)) {
                    animateOverlay(player, false, arrival);
                } else {
                    Bukkit.getScheduler().runTaskLater(this, arrival, commandToRun == null ? 0L : 2L);
                }
                return;
            }

            showCountdown(player, remaining[0]);
            playSpiral(player.getLocation(), remaining[0]);
            if (remaining[0] == 1 && shouldUseFullscreenOverlay(player)) {
                animateOverlay(player, true, () -> {});
            }
            remaining[0]--;
        }, 0L, 20L);
        activeTransitions.put(id, task);
    }

    private boolean shouldUseFullscreenOverlay(Player player) {
        if (!getConfig().getBoolean("fullscreen-overlay.enabled", true)) return false;
        if (!resourcePackLoaded.contains(player.getUniqueId())) return false;
        // Unison can bridge Java resource-pack font images to Bedrock. Without it,
        // Floodgate players stay on the compatible title/particle/darkness fallback.
        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                Object result = api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
                if (Boolean.TRUE.equals(result)
                        && getServer().getPluginManager().getPlugin("Unison") == null) return false;
            } catch (ReflectiveOperationException ignored) {
                // If Floodgate's API differs, prefer the safe fallback rather than risking a broken overlay.
                return false;
            }
        }
        return true;
    }

    private void animateOverlay(Player player, boolean closing, Runnable after) {
        UUID id = player.getUniqueId();
        BukkitTask previous = overlayAnimations.remove(id);
        if (previous != null) previous.cancel();

        int frameCount = Math.max(1, Math.min(8, getConfig().getInt("fullscreen-overlay.frames", 8)));
        final int lastFrame = frameCount - 1;
        final int[] frame = {closing ? 0 : lastFrame};
        final int step = closing ? 1 : -1;
        long frameInterval = Math.max(1L, getConfig().getLong("fullscreen-overlay.frame-interval-ticks", 2L));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || !resourcePackLoaded.contains(id)) {
                BukkitTask running = overlayAnimations.remove(id);
                if (running != null) running.cancel();
                if (after != null) after.run();
                return;
            }

            showOverlayFrame(player, frame[0]);
            if (frame[0] == (closing ? lastFrame : 0)) {
                BukkitTask running = overlayAnimations.remove(id);
                if (running != null) running.cancel();
                if (!closing && after != null) {
                    Bukkit.getScheduler().runTaskLater(this, after, 1L);
                }
                return;
            }
            frame[0] += step;
        }, 0L, frameInterval);
        overlayAnimations.put(id, task);
    }

    private void showOverlayFrame(Player player, int frame) {
        char glyph = (char) (0xF500 + Math.max(0, Math.min(7, frame)));
        Component image = Component.text(String.valueOf(glyph)).font(Key.key("eywaportal", "portal"));
        player.showTitle(Title.title(image, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(120), Duration.ZERO)));
    }

    private void showCountdown(Player player, int seconds) {
        if (!getConfig().getBoolean("screen-transition.enabled", true)) return;
        String title = getConfig().getString("screen-transition.title", "<#55FFFF><bold>EYWA PORTAL</bold>");
        String subtitle = getConfig().getString("screen-transition.subtitle", "<#B7F7FF>Opening in <seconds>...")
                .replace("<seconds>", Integer.toString(seconds));
        player.showTitle(Title.title(miniMessage.deserialize(title), miniMessage.deserialize(subtitle)));
    }

    private void showArrival(Player player) {
        if (!getConfig().getBoolean("screen-transition.enabled", true)) return;
        player.showTitle(Title.title(
                miniMessage.deserialize(getConfig().getString("screen-transition.arrival-title", "<#55FFFF><bold>PORTAL OPENED</bold>")),
                miniMessage.deserialize(getConfig().getString("screen-transition.arrival-subtitle", "<#D9FFFF>You have arrived."))));
    }

    private void playVEnginePortal(Location location, int durationTicks) {
        if (!getConfig().getBoolean("vengine.enabled", true)) return;
        if (getServer().getPluginManager().getPlugin("VEngine") == null) return;

        String effect = getConfig().getString("vengine.effect", "eywa_portal");
        double scale = getConfig().getDouble("vengine.scale", 1.0);
        String command = String.format(Locale.ROOT, "ve play %s %.3f %.3f %.3f %s %.2f %d",
                effect,
                location.getX(), location.getY(), location.getZ(),
                location.getWorld().getName(), scale, Math.max(20, durationTicks));
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            getLogger().warning("VEngine command could not be dispatched. Native particle fallback remains active.");
        }
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
        UUID id = player.getUniqueId();
        BukkitTask task = activeTransitions.remove(id);
        if (task != null) task.cancel();
        BukkitTask overlay = overlayAnimations.remove(id);
        if (overlay != null) overlay.cancel();
        // Only announce cancellation when there was actually a running transition.
        // startTransition() calls this before every new transition to clean up stale tasks.
        if (!silent && player.isOnline() && (task != null || overlay != null)) {
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
