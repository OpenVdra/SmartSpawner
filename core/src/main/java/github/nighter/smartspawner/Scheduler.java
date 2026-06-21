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
 * and Folia's region-based scheduling system using a Strategy Pattern.
 */
public final class Scheduler {

    private static final long TICK_MS = 50L;

    private static Plugin plugin;
    private static PlatformScheduler impl;

    private Scheduler() {
    }

    /**
     * Initializes the scheduler. Must be called from the plugin's onEnable method.
     */
    public static void init(Plugin pluginInstance) {
        if (impl != null) {
            pluginInstance.getLogger().warning("Scheduler.init() called more than once; ignoring.");
            return;
        }
        plugin = pluginInstance;
        if (detectFolia()) {
            impl = new FoliaSchedulerImpl();
            plugin.getLogger().info("Folia detected! Using region-based threading system.");
        } else {
            impl = new BukkitSchedulerImpl();
            plugin.getLogger().info("Running on standard Paper server.");
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void ensureInitialized() {
        if (impl == null) {
            throw new IllegalStateException("Scheduler.init() was not called");
        }
    }

    private static long foliaDelay(long delayTicks) {
        return delayTicks < 1 ? 1 : delayTicks;
    }

    // --- Public API ---

    public static Task runTask(Runnable runnable) {
        ensureInitialized();
        return impl.runTask(runnable);
    }

    public static Task runTaskAsync(Runnable runnable) {
        ensureInitialized();
        return impl.runTaskAsync(runnable);
    }

    public static Task runTaskLater(Runnable runnable, long delayTicks) {
        ensureInitialized();
        return impl.runTaskLater(runnable, delayTicks);
    }

    public static Task runTaskLaterAsync(Runnable runnable, long delayTicks) {
        ensureInitialized();
        return impl.runTaskLaterAsync(runnable, delayTicks);
    }

    public static Task runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
        ensureInitialized();
        return impl.runTaskTimer(runnable, delayTicks, periodTicks);
    }

    public static Task runTaskTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        ensureInitialized();
        return impl.runTaskTimerAsync(runnable, delayTicks, periodTicks);
    }

    public static Task runEntityTask(Entity entity, Runnable runnable) {
        ensureInitialized();
        if (entity == null || !entity.isValid()) {
            return runTask(runnable);
        }
        return impl.runEntityTask(entity, runnable);
    }

    public static Task runEntityTaskLater(Entity entity, Runnable runnable, long delayTicks) {
        ensureInitialized();
        if (entity == null || !entity.isValid()) {
            return runTaskLater(runnable, delayTicks);
        }
        return impl.runEntityTaskLater(entity, runnable, delayTicks);
    }

    public static Task runEntityTaskTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        ensureInitialized();
        if (entity == null || !entity.isValid()) {
            return runTaskTimer(runnable, delayTicks, periodTicks);
        }
        return impl.runEntityTaskTimer(entity, runnable, delayTicks, periodTicks);
    }

    public static Task runLocationTask(Location location, Runnable runnable) {
        ensureInitialized();
        if (location == null || location.getWorld() == null) {
            return runTask(runnable);
        }
        return impl.runLocationTask(location, runnable);
    }

    public static Task runChunkTask(World world, int chunkX, int chunkZ, Runnable runnable) {
        ensureInitialized();
        if (world == null) {
            return runTask(runnable);
        }
        return impl.runChunkTask(world, chunkX, chunkZ, runnable);
    }

    public static Task runLocationTaskLater(Location location, Runnable runnable, long delayTicks) {
        ensureInitialized();
        if (location == null || location.getWorld() == null) {
            return runTaskLater(runnable, delayTicks);
        }
        return impl.runLocationTaskLater(location, runnable, delayTicks);
    }

    public static Task runLocationTaskTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        ensureInitialized();
        if (location == null || location.getWorld() == null) {
            return runTaskTimer(runnable, delayTicks, periodTicks);
        }
        return impl.runLocationTaskTimer(location, runnable, delayTicks, periodTicks);
    }

    public static Task runWorldTask(Location location, Runnable runnable) {
        return runLocationTask(location, runnable);
    }

    public static Task runWorldTaskLater(Location location, Runnable runnable, long delayTicks) {
        return runLocationTaskLater(location, runnable, delayTicks);
    }

    public static <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        ensureInitialized();
        return impl.supplySync(supplier);
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        ensureInitialized();
        return impl.supplyAsync(supplier);
    }

    // --- Task wrapper ---

    public interface Task {
        void cancel();

        boolean isCancelled();
    }

    // --- Platform strategy ---

    private interface PlatformScheduler {
        Task runTask(Runnable runnable);

        Task runTaskAsync(Runnable runnable);

        Task runTaskLater(Runnable runnable, long delayTicks);

        Task runTaskLaterAsync(Runnable runnable, long delayTicks);

        Task runTaskTimer(Runnable runnable, long delayTicks, long periodTicks);

        Task runTaskTimerAsync(Runnable runnable, long delayTicks, long periodTicks);

        Task runEntityTask(Entity entity, Runnable runnable);

        Task runEntityTaskLater(Entity entity, Runnable runnable, long delayTicks);

        Task runEntityTaskTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks);

        Task runLocationTask(Location location, Runnable runnable);

        Task runLocationTaskLater(Location location, Runnable runnable, long delayTicks);

        Task runLocationTaskTimer(Location location, Runnable runnable, long delayTicks, long periodTicks);

        Task runChunkTask(World world, int chunkX, int chunkZ, Runnable runnable);

        <T> CompletableFuture<T> supplySync(Supplier<T> supplier);

        <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier);
    }

    // --- Bukkit implementation ---

    private static final class BukkitSchedulerImpl implements PlatformScheduler {

        private Task wrap(BukkitTask task) {
            return new Task() {
                @Override
                public void cancel() {
                    if (task != null) {
                        task.cancel();
                    }
                }

                @Override
                public boolean isCancelled() {
                    return task == null || task.isCancelled();
                }
            };
        }

        @Override
        public Task runTask(Runnable runnable) {
            return wrap(Bukkit.getScheduler().runTask(plugin, runnable));
        }

        @Override
        public Task runTaskAsync(Runnable runnable) {
            return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
        }

        @Override
        public Task runTaskLater(Runnable runnable, long delayTicks) {
            return wrap(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
        }

        @Override
        public Task runTaskLaterAsync(Runnable runnable, long delayTicks) {
            return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks));
        }

        @Override
        public Task runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
            return wrap(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
        }

        @Override
        public Task runTaskTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
            return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
        }

        @Override
        public Task runEntityTask(Entity entity, Runnable runnable) {
            return runTask(runnable);
        }

        @Override
        public Task runEntityTaskLater(Entity entity, Runnable runnable, long delayTicks) {
            return runTaskLater(runnable, delayTicks);
        }

        @Override
        public Task runEntityTaskTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
            return runTaskTimer(runnable, delayTicks, periodTicks);
        }

        @Override
        public Task runLocationTask(Location location, Runnable runnable) {
            return runTask(runnable);
        }

        @Override
        public Task runLocationTaskLater(Location location, Runnable runnable, long delayTicks) {
            return runTaskLater(runnable, delayTicks);
        }

        @Override
        public Task runLocationTaskTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
            return runTaskTimer(runnable, delayTicks, periodTicks);
        }

        @Override
        public Task runChunkTask(World world, int chunkX, int chunkZ, Runnable runnable) {
            return runTask(runnable);
        }

        @Override
        public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable ex) {
                        future.completeExceptionally(ex);
                        plugin.getLogger().log(Level.SEVERE, "Error while executing sync task", ex);
                    }
                });
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }

        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable ex) {
                        future.completeExceptionally(ex);
                        plugin.getLogger().log(Level.SEVERE, "Error while executing async task", ex);
                    }
                });
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }
    }

    // --- Folia implementation ---

    private static final class FoliaSchedulerImpl implements PlatformScheduler {

        private Task wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
            return new Task() {
                @Override
                public void cancel() {
                    if (task != null) {
                        task.cancel();
                    }
                }

                @Override
                public boolean isCancelled() {
                    return task == null || task.isCancelled();
                }
            };
        }

        @Override
        public Task runTask(Runnable runnable) {
            try {
                return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> runnable.run()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runTaskAsync(Runnable runnable) {
            try {
                return wrap(Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> runnable.run()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling async task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runTaskLater(Runnable runnable, long delayTicks) {
            if (delayTicks <= 0) {
                return runTask(runnable);
            }
            try {
                return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> runnable.run(),
                        foliaDelay(delayTicks)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling delayed task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runTaskLaterAsync(Runnable runnable, long delayTicks) {
            try {
                long delayMs = delayTicks * TICK_MS;
                return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> runnable.run(),
                        delayMs, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling delayed async task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
            try {
                return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(),
                        foliaDelay(delayTicks), periodTicks));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling timer task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runTaskTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
            try {
                long delayMs = delayTicks * TICK_MS;
                long periodMs = periodTicks * TICK_MS;
                return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(),
                        delayMs, periodMs, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling timer async task in Folia", e);
                return wrap(null);
            }
        }

        @Override
        public Task runEntityTask(Entity entity, Runnable runnable) {
            try {
                return wrap(entity.getScheduler().run(plugin, scheduledTask -> runnable.run(), null));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling entity task in Folia, falling back to global scheduler", e);
                return runTask(runnable);
            }
        }

        @Override
        public Task runEntityTaskLater(Entity entity, Runnable runnable, long delayTicks) {
            if (delayTicks <= 0) {
                return runEntityTask(entity, runnable);
            }
            try {
                return wrap(entity.getScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), null,
                        foliaDelay(delayTicks)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling delayed entity task in Folia, falling back to global scheduler", e);
                return runTaskLater(runnable, delayTicks);
            }
        }

        @Override
        public Task runEntityTaskTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
            try {
                return wrap(entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(), null,
                        foliaDelay(delayTicks), periodTicks));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling timer entity task in Folia, falling back to global scheduler", e);
                return runTaskTimer(runnable, delayTicks, periodTicks);
            }
        }

        @Override
        public Task runLocationTask(Location location, Runnable runnable) {
            try {
                return wrap(Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> runnable.run()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling location task in Folia, falling back to global scheduler", e);
                return runTask(runnable);
            }
        }

        @Override
        public Task runLocationTaskLater(Location location, Runnable runnable, long delayTicks) {
            if (delayTicks <= 0) {
                return runLocationTask(location, runnable);
            }
            try {
                return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> runnable.run(),
                        foliaDelay(delayTicks)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling delayed location task in Folia, falling back to global scheduler", e);
                return runTaskLater(runnable, delayTicks);
            }
        }

        @Override
        public Task runLocationTaskTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
            try {
                return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, scheduledTask -> runnable.run(),
                        foliaDelay(delayTicks), periodTicks));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling timer location task in Folia, falling back to global scheduler", e);
                return runTaskTimer(runnable, delayTicks, periodTicks);
            }
        }

        @Override
        public Task runChunkTask(World world, int chunkX, int chunkZ, Runnable runnable) {
            try {
                return wrap(Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, scheduledTask -> runnable.run()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error scheduling chunk task in Folia, falling back to global scheduler", e);
                return runTask(runnable);
            }
        }

        @Override
        public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable ex) {
                        future.completeExceptionally(ex);
                        plugin.getLogger().log(Level.SEVERE, "Error while executing sync task", ex);
                    }
                });
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }

        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable ex) {
                        future.completeExceptionally(ex);
                        plugin.getLogger().log(Level.SEVERE, "Error while executing async task", ex);
                    }
                });
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }
    }
}
