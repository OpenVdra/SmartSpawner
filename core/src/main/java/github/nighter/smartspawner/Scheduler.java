package github.nighter.smartspawner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Universal scheduler utility supporting both traditional Bukkit scheduling
 * and Folia's region-based scheduling system using a high-performance Strategy Pattern.
 */
public final class Scheduler {
    
    private static Plugin plugin;
    private static PlatformScheduler schedulerImpl;

    /**
     * Initialisiert den Scheduler. Sollte in der onEnable-Methode des Plugins aufgerufen werden.
     */
    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {}

        if (isFolia) {
            schedulerImpl = new FoliaScheduler();
            plugin.getLogger().info("Folia detected! Using region-based threading system.");
        } else {
            schedulerImpl = new BukkitScheduler();
            plugin.getLogger().info("Standard Paper/Bukkit server detected.");
        }
    }

    // --- ÖFFENTLICHE API ---

    public static Task runTask(Runnable runnable) { return schedulerImpl.runTask(runnable); }
    public static Task runTaskAsync(Runnable runnable) { return schedulerImpl.runTaskAsync(runnable); }
    
    public static Task runTaskLater(Runnable runnable, long delayTicks) { 
        return delayTicks <= 0 ? runTask(runnable) : schedulerImpl.runTaskLater(runnable, delayTicks); 
    }
    public static Task runTaskLaterAsync(Runnable runnable, long delayTicks) { 
        return delayTicks <= 0 ? runTaskAsync(runnable) : schedulerImpl.runTaskLaterAsync(runnable, delayTicks); 
    }
    
    public static Task runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) { return schedulerImpl.runTaskTimer(runnable, delayTicks, periodTicks); }
    public static Task runTaskTimerAsync(Runnable runnable, long delayTicks, long periodTicks) { return schedulerImpl.runTaskTimerAsync(runnable, delayTicks, periodTicks); }

    public static Task runEntityTask(Entity entity, Runnable runnable) {
        if (entity == null || !entity.isValid()) return runTask(runnable);
        return schedulerImpl.runEntityTask(entity, runnable);
    }
    public static Task runEntityTaskLater(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || !entity.isValid()) return runTaskLater(runnable, delayTicks);
        return delayTicks <= 0 ? schedulerImpl.runEntityTask(entity, runnable) : schedulerImpl.runEntityTaskLater(entity, runnable, delayTicks);
    }
    public static Task runEntityTaskTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (entity == null || !entity.isValid()) return runTaskTimer(runnable, delayTicks, periodTicks);
        return schedulerImpl.runEntityTaskTimer(entity, runnable, delayTicks, periodTicks);
    }

    public static Task runLocationTask(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) return runTask(runnable);
        return schedulerImpl.runLocationTask(location, runnable);
    }
    public static Task runLocationTaskLater(Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null) return runTaskLater(runnable, delayTicks);
        return delayTicks <= 0 ? schedulerImpl.runLocationTask(location, runnable) : schedulerImpl.runLocationTaskLater(location, runnable, delayTicks);
    }
    public static Task runLocationTaskTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) return runTaskTimer(runnable, delayTicks, periodTicks);
        return schedulerImpl.runLocationTaskTimer(location, runnable, delayTicks, periodTicks);
    }

    public static Task runChunkTask(World world, int chunkX, int chunkZ, Runnable runnable) {
        if (world == null) return runTask(runnable);
        return schedulerImpl.runChunkTask(world, chunkX, chunkZ, runnable);
    }

    public static <T> CompletableFuture<T> supplySync(Supplier<T> supplier) { return schedulerImpl.supplySync(supplier); }
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) { return schedulerImpl.supplyAsync(supplier); }

    // --- INTERFACES & WRAPPER ---

    public interface Task {
        void cancel();
        boolean isCancelled();
    }

    private interface PlatformScheduler {
        Task runTask(Runnable r);
        Task runTaskAsync(Runnable r);
        Task runTaskLater(Runnable r, long delay);
        Task runTaskLaterAsync(Runnable r, long delay);
        Task runTaskTimer(Runnable r, long delay, long period);
        Task runTaskTimerAsync(Runnable r, long delay, long period);
        Task runEntityTask(Entity e, Runnable r);
        Task runEntityTaskLater(Entity e, Runnable r, long delay);
        Task runEntityTaskTimer(Entity e, Runnable r, long delay, long period);
        Task runLocationTask(Location l, Runnable r);
        Task runLocationTaskLater(Location l, Runnable r, long delay);
        Task runLocationTaskTimer(Location l, Runnable r, long delay, long period);
        Task runChunkTask(World w, int cx, int cz, Runnable r);
        <T> CompletableFuture<T> supplySync(Supplier<T> s);
        <T> CompletableFuture<T> supplyAsync(Supplier<T> s);
    }

    // --- BUKKIT IMPLEMENTIERUNG ---

    private static final class BukkitScheduler implements PlatformScheduler {
        private Task wrap(BukkitTask task) { return new Task() {
            @Override public void cancel() { if (task != null) task.cancel(); }
            @Override public boolean isCancelled() { return task == null || task.isCancelled(); }
        };}

        @Override public Task runTask(Runnable r) { return wrap(Bukkit.getScheduler().runTask(plugin, r)); }
        @Override public Task runTaskAsync(Runnable r) { return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, r)); }
        @Override public Task runTaskLater(Runnable r, long d) { return wrap(Bukkit.getScheduler().runTaskLater(plugin, r, d)); }
        @Override public Task runTaskLaterAsync(Runnable r, long d) { return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, r, d)); }
        @Override public Task runTaskTimer(Runnable r, long d, long p) { return wrap(Bukkit.getScheduler().runTaskTimer(plugin, r, d, p)); }
        @Override public Task runTaskTimerAsync(Runnable r, long d, long p) { return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, r, d, p)); }
        @Override public Task runEntityTask(Entity e, Runnable r) { return runTask(r); }
        @Override public Task runEntityTaskLater(Entity e, Runnable r, long d) { return runTaskLater(r, d); }
        @Override public Task runEntityTaskTimer(Entity e, Runnable r, long d, long p) { return runTaskTimer(r, d, p); }
        @Override public Task runLocationTask(Location l, Runnable r) { return runTask(r); }
        @Override public Task runLocationTaskLater(Location l, Runnable r, long d) { return runTaskLater(r, d); }
        @Override public Task runLocationTaskTimer(Location l, Runnable r, long d, long p) { return runTaskTimer(r, d, p); }
        @Override public Task runChunkTask(World w, int cx, int cz, Runnable r) { return runTask(r); }

        @Override public <T> CompletableFuture<T> supplySync(Supplier<T> s) {
            CompletableFuture<T> f = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
            return f;
        }
        @Override public <T> CompletableFuture<T> supplyAsync(Supplier<T> s) {
            CompletableFuture<T> f = new CompletableFuture<>();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
            return f;
        }
    }

    // --- FOLIA IMPLEMENTIERUNG ---

    private static final class FoliaScheduler implements PlatformScheduler {
        private Task wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) { return new Task() {
            @Override public void cancel() { if (task != null) task.cancel(); }
            @Override public boolean isCancelled() { return task == null || task.isCancelled(); }
        };}

        @Override public Task runTask(Runnable r) { return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, t -> r.run())); }
        @Override public Task runTaskAsync(Runnable r) { return wrap(Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run())); }
        @Override public Task runTaskLater(Runnable r, long d) { return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> r.run(), d)); }
        @Override public Task runTaskLaterAsync(Runnable r, long d) { return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, t -> r.run(), d * 50, TimeUnit.MILLISECONDS)); }
        @Override public Task runTaskTimer(Runnable r, long d, long p) { return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> r.run(), d, p)); }
        @Override public Task runTaskTimerAsync(Runnable r, long d, long p) { return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> r.run(), d * 50, p * 50, TimeUnit.MILLISECONDS)); }
        
        @Override public Task runEntityTask(Entity e, Runnable r) { return wrap(e.getScheduler().run(plugin, t -> r.run(), null)); }
        @Override public Task runEntityTaskLater(Entity e, Runnable r, long d) { return wrap(e.getScheduler().runDelayed(plugin, t -> r.run(), null, d)); }
        @Override public Task runEntityTaskTimer(Entity e, Runnable r, long d, long p) { return wrap(e.getScheduler().runAtFixedRate(plugin, t -> r.run(), null, d, p)); }
        
        @Override public Task runLocationTask(Location l, Runnable r) { return wrap(Bukkit.getRegionScheduler().run(plugin, l, t -> r.run())); }
        @Override public Task runLocationTaskLater(Location l, Runnable r, long d) { return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, l, t -> r.run(), d)); }
        @Override public Task runLocationTaskTimer(Location l, Runnable r, long d, long p) { return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin, l, t -> r.run(), d, p)); }
        
        @Override public Task runChunkTask(World w, int cx, int cz, Runnable r) { return wrap(Bukkit.getRegionScheduler().run(plugin, w, cx, cz, t -> r.run())); }

        @Override public <T> CompletableFuture<T> supplySync(Supplier<T> s) {
            CompletableFuture<T> f = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
            return f;
        }
        @Override public <T> CompletableFuture<T> supplyAsync(Supplier<T> s) {
            CompletableFuture<T> f = new CompletableFuture<>();
            Bukkit.getAsyncScheduler().runNow(plugin, t -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
            return f;
        }
    }
}
