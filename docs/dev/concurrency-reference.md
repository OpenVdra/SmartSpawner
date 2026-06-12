# SmartSpawner Concurrency Reference

This document provides a comprehensive reference of all synchronization primitives, locks, and asynchronous execution patterns used throughout the SmartSpawner codebase.

---

## Table of Contents

1. [Concurrency Architecture Overview](#1-concurrency-architecture-overview)
2. [Lock & Synchronization Inventory](#2-lock--synchronization-inventory)
3. [Detailed Lock Usage](#3-detailed-lock-usage)
   - 3.1 [Fine-Grained Per-Spawner Locks](#31-fine-grained-per-spawner-locks)
   - 3.2 [Global Location-Based Locks](#32-global-location-based-locks)
   - 3.3 [CAS Guards (`AtomicBoolean`)](#33-cas-guards-atomicboolean)
   - 3.4 [`synchronized` Methods](#34-synchronized-methods)
   - 3.5 [`volatile` Fields](#35-volatile-fields)
   - 3.6 [Concurrent Collections](#36-concurrent-collections)
4. [Asynchronous Execution](#4-asynchronous-execution)
   - 4.1 [Scheduler Abstraction](#41-scheduler-abstraction)
   - 4.2 [`CompletableFuture` Async API](#42-completablefuture-async-api)
   - 4.3 [Detailed Async Flows](#43-detailed-async-flows)
5. [File-by-File Summary](#5-file-by-file-summary)
6. [Patterns & Best Practices](#6-patterns--best-practices)
7. [Warnings & Caveats](#7-warnings--caveats)

---

## 1. Concurrency Architecture Overview

SmartSpawner uses a **hybrid multi-threading** model that combines:

- **Bukkit/Paper main thread** (or Folia region threads) for all Bukkit API calls.
- **Async thread pool** for heavy computation (loot generation, sell calculation, spawner scanning).
- **Folia-aware scheduler** (`Scheduler.java`) that automatically dispatches work to the correct thread depending on the server software (Paper vs Folia).

Key concurrency problems addressed:

- **Race conditions** when a player breaks a spawner with a pickaxe while another player destacks it via GUI.
- **Double-sell exploits** from rapid repeated clicks.
- **Duplication exploits** when loot generation runs concurrently with stack-size changes.
- **Folia compatibility** — every Bukkit API call is dispatched back to the correct region/entity thread.

---

## 2. Lock & Synchronization Inventory

| File | Mechanism | Type | Purpose |
|------|-----------|------|---------|
| `SpawnerData.java` | `ReentrantLock inventoryLock` | Instance lock (per spawner) | Protects add/remove operations on `VirtualInventory` |
| `SpawnerData.java` | `ReentrantLock lootGenerationLock` | Instance lock (per spawner) | Guards loot generation cycles and pre-generated loot updates |
| `SpawnerData.java` | `ReentrantLock dataLock` | Instance lock (per spawner) | Protects metadata changes (stack size, exp, spawn timing) |
| `SpawnerData.java` | `AtomicBoolean selling` | CAS guard (per spawner) | Prevents concurrent sell operations; replaces old sellLock pattern |
| `SpawnerData.java` | `AtomicBoolean storageDirty` | CAS flag (per spawner) | Tracks whether storage GUI content was modified since last save |
| `SpawnerData.java` | `AtomicBoolean spawnerStop` | CAS flag (per spawner) | Thread-safe start/stop state for the spawner |
| `SpawnerData.java` | `synchronized` methods | Intrinsic lock (per spawner) | Protects pre-generated loot storage (`storePreGeneratedLoot`, `getAndClearPreGeneratedItems`, etc.) |
| `SpawnerData.java` | `volatile` fields (`accumulatedSellValue`, `sellValueDirty`, `preGeneratedItems`, `preGeneratedExperience`, `isPreGenerating`, `cachedHasNoLoot`) | Memory visibility | Allows safe reads from async threads without full locking |
| `SpawnerLocationLockManager.java` | `ConcurrentHashMap<Location, ReentrantLock>` | Global location locks | Prevents race conditions between pickaxe break and GUI stack/destack operations |
| `SpawnerLocationLockManager.java` | `ReentrantLock` (per location) | Fine-grained global lock | Same-location operations are serialized across all threads |
| `SpawnerStackerHandler.java` | `AtomicBoolean updateLocks` (per viewer UUID) | CAS guard | Prevents concurrent GUI refresh updates for the same player |
| `SpawnerStackerHandler.java` | `locationLockManager.tryLock()` | External global lock | Serializes stack changes with break/removal operations |
| `SpawnerBreakListener.java` | `locationLockManager.tryLock()` | External global lock | Serializes break handling with GUI stack/destack operations |
| `SpawnerRemovalService.java` | `locationLockManager.tryLock()` | External global lock | Serializes removal with break and GUI operations |
| `SpawnerRemovalService.java` | `ConcurrentHashMap.newKeySet() pendingRemovalIds` | Concurrent set | Tracks spawners currently in the removal pipeline |
| `SpawnerRemovalService.java` | `ConcurrentHashMap.newKeySet() pendingRemovalLocations` | Concurrent set | Tracks locations currently in the removal pipeline |
| `SpawnerHighlightManager.java` | `AtomicBoolean cancelled` | CAS flag (per scan session) | Allows cross-thread cancellation of active scans |
| `SpawnerHighlightManager.java` | `CopyOnWriteArrayList<BlockDisplay>` | Concurrent list | Stores highlight entities; optimized for many reads, few writes |
| `SpawnerHologram.java` | `AtomicReference<TextDisplay>` | Atomic reference | Thread-safe reference to the hologram entity |
| `VirtualInventory.java` | `ConcurrentHashMap<ItemSignature, Long>` | Concurrent map | Internal item storage (writes are protected by `inventoryLock` in `SpawnerData`) |
| `SpawnerManager.java` | `ConcurrentHashMap<String, SpawnerData> spawners` | Concurrent map | Thread-safe registry of all loaded spawners |
| `SpawnerManager.java` | `ConcurrentHashMap.newKeySet() confirmedGhostSpawners` | Concurrent set | Tracks spawners confirmed as ghosts to avoid repeated checks |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<UUID, Long> lastClickTime` | Concurrent map | Thread-safe click cooldown tracking |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<UUID, Scheduler.Task> pendingUpdates` | Concurrent map | Thread-safe pending GUI update task tracking |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<String, Set<UUID>> activeViewers` | Concurrent map | Thread-safe viewer tracking per spawner |
| `SpawnerHighlightManager.java` | `ConcurrentHashMap<UUID, ScanSession> activeSessions` | Concurrent map | Thread-safe active scan session tracking |
| `LRUCache.java` | `synchronized` methods | Intrinsic lock | Makes `LinkedHashMap` cache thread-safe for language/formatting lookups |
| `UpdateTaskManager.java` | `synchronized` methods + `volatile boolean isTaskRunning` | Hybrid lock | Thread-safe lifecycle management for the GUI update task |
| `SpawnerDatabaseHandler.java` | `volatile boolean isSaving` | Memory visibility | Signals whether an async database save is in progress |
| `SpawnerFileHandler.java` | `volatile boolean isSaving` | Memory visibility | Signals whether an async file save is in progress |
| `TimerUpdateService.java` | `volatile Boolean hasTimerPlaceholders` | Memory visibility | Cached result of placeholder check in timer strings |
| `SlotCacheManager.java` | `volatile int cachedStorageSlot` etc. | Memory visibility | Thread-safe GUI slot index caching |
| `WorldEventHandler.java` | `volatile boolean initialLoadAttempted` | Memory visibility | Ensures one-time world loading logic is visible across threads |
| `DiscordWebhookLogger.java` | `volatile DiscordWebhookConfig` etc. | Memory visibility | Allows safe config reload from another thread |
| `Config.java` | `volatile Config instance` | Memory visibility | Double-checked locking singleton pattern |
| `LanguageManager.java` | `AtomicInteger cacheHits` / `cacheMisses` | Atomic counter | Thread-safe cache statistics |
| `GuiLanguageSection.java` | `AtomicInteger cacheHits` / `cacheMisses` | Atomic counter | Thread-safe cache statistics |
| `SmallCapsFormatter.java` | `AtomicInteger cacheHits` / `cacheMisses` | Atomic counter | Thread-safe cache statistics |

---

## 3. Detailed Lock Usage

### 3.1 Fine-Grained Per-Spawner Locks

**File:** `SpawnerData.java`

Each `SpawnerData` instance owns three separate `ReentrantLock`s following the **Lock Striping** pattern. This avoids a single coarse lock that would block unrelated operations:

| Lock | Purpose | Used By |
|------|---------|---------|
| `inventoryLock` | Add/remove items in `VirtualInventory` | `addItemsAndUpdateSellValue()`, `removeItemsAndUpdateSellValue()` |
| `lootGenerationLock` | Loot generation cycle + pre-generated loot application | `SpawnerLootGenerator.spawnLootToSpawner()`, `preGenerateLoot()`, `addPreGeneratedLoot()` |
| `dataLock` | Metadata changes (stack size, exp, `lastSpawnTime`, capacity) | `setStackSize()`, loot generation timing updates |

**Critical lock ordering to prevent deadlocks** (in `setStackSize()`):

```java
dataLock.lock();
try {
    inventoryLock.lock();
    try {
        updateStackSize(...);
    } finally {
        inventoryLock.unlock();
    }
} finally {
    dataLock.unlock();
}
```

`lootGenerationLock` is **never** nested with `dataLock` or `inventoryLock` in a way that could create a circular wait.

---

### 3.2 Global Location-Based Locks

**File:** `SpawnerLocationLockManager.java`

A global `ConcurrentHashMap<Location, ReentrantLock>` assigns one lock per spawner location. This serializes all operations that affect the same physical block, preventing duplication exploits such as:

- Clicking the GUI to remove spawners while breaking with a pickaxe.
- Multiple players breaking the same spawner in the same tick.
- Simultaneous operations that modify the stack size.

**API:**
- `getLock(Location)` — gets or creates a lock for the location.
- `tryLock(Location)` — **non-blocking** acquire; returns `false` if already locked.
- `unlock(Location)` — releases only if the current thread holds the lock (`isHeldByCurrentThread()` check).
- `removeLock(Location)` — removes the lock entry when the spawner is deleted (prevents memory leaks).

**Cleanup:** `Scheduler.runTaskTimerAsync(...)` runs every 5 minutes to purge unused locks for locations that no longer have a spawner.

**Consumers of this lock:**
- `SpawnerBreakListener` — both smart and vanilla spawner breaks.
- `SpawnerStackerHandler` — all stack/destack operations (`handleStackDecrease`, `handleStackIncrease`, `handleAddAll`, `handleRemoveAll`).
- `SpawnerRemovalService` — `claimRemoval()` and `removeBlockAndFinalize()`.

---

### 3.3 CAS Guards (`AtomicBoolean`)

**File:** `SpawnerData.java`

| Field | Purpose |
|-------|---------|
| `selling` | Single CAS guard that replaces the old `sellLock` + double-lock pattern. All inventory-touching operations must check `isSelling()` before proceeding. |
| `storageDirty` | Set when items are moved/dropped inside the storage GUI; cleared (and spawner queued for save) when the GUI is closed. |
| `spawnerStop` | Atomic start/stop flag so the spawner state can be read safely from scheduler threads. |

**API:**
- `startSelling()` — `selling.compareAndSet(false, true)`. Returns `true` if the caller now owns the sell.
- `stopSelling()` — `selling.set(false)`.
- `isSelling()` — `selling.get()`.

**Consumers of the `selling` guard:**
- `SpawnerSellManager.sellAllItems()` — CAS guard before beginning any sell work.
- `SpawnerSellConfirmListener.handleConfirm()` — early-exit if already selling to drop duplicate click packets.
- `SpawnerBreakListener.handleSmartSpawnerBreak()` — blocks breaking while a sell is in flight.
- `SpawnerRemovalService.claimRemoval()` — blocks removal while a sell is in flight.
- `SpawnerLootGenerator.spawnLootToSpawner()` — skips loot generation while selling.

**Other `AtomicBoolean` usage:**
- `SpawnerStackerHandler` — `Map<UUID, AtomicBoolean> updateLocks` prevents concurrent GUI refresh for the same viewer (CAS `compareAndSet(false, true)`).
- `SpawnerHighlightManager` — `AtomicBoolean cancelled` inside `ScanSession` allows a scan to be cancelled from any thread.

---

### 3.4 `synchronized` Methods

**File:** `SpawnerData.java`

Pre-generated loot storage is guarded by `synchronized` methods rather than `ReentrantLock` because the operations are short, simple, and always accessed from the main/location thread:

- `storePreGeneratedLoot(List<ItemStack>, long)`
- `getAndClearPreGeneratedItems()`
- `getAndClearPreGeneratedExperience()`
- `hasPreGeneratedLoot()`
- `setPreGenerating(boolean)` / `isPreGenerating()`
- `clearPreGeneratedLoot()`

The underlying fields (`preGeneratedItems`, `preGeneratedExperience`, `isPreGenerating`) are also `volatile` for visibility when read outside these methods.

**File:** `LRUCache.java`

Every method (`get`, `put`, `clear`, `size`, `capacity`, `resize`) is `synchronized` because `LinkedHashMap` is not thread-safe and the cache may be accessed from both the main thread and async worker threads.

**File:** `UpdateTaskManager.java`

`startTask()` and `stopTask()` are `synchronized`, paired with a `volatile boolean isTaskRunning` so external code can query status without acquiring the monitor.

---

### 3.5 `volatile` Fields

| File | Field | Why it is `volatile` |
|------|-------|----------------------|
| `SpawnerData.java` | `accumulatedSellValue` | Sell value may be recalculated on an async thread and must be visible to the location thread that applies the result. |
| `SpawnerData.java` | `sellValueDirty` | Set from many places (config reload, loot changes) and read from sell logic. |
| `SpawnerData.java` | `preGeneratedItems`, `preGeneratedExperience`, `isPreGenerating` | Written by async callbacks, read by location thread when applying pre-generated loot. |
| `SpawnerData.java` | `cachedHasNoLoot` | Cached result of `hasNoLootOrExperience()`; invalidated when loot config changes. |
| `SpawnerHighlightManager.java` | `expiryTask` | May be assigned from the main thread and read/cancelled from cleanup logic. |
| `SpawnerHighlightManager.java` | `scannedSpawners` | Set from the main thread after async scanning finishes. |
| `SpawnerDatabaseHandler.java` | `isSaving` | Async save flag must be visible to the thread that initiates the next save check. |
| `SpawnerFileHandler.java` | `isSaving` | Same pattern as above for file-based persistence. |
| `Config.java` | `instance` | Standard double-checked locking singleton pattern. |
| `TimerUpdateService.java` | `hasTimerPlaceholders` | Cached check result; may be computed lazily on a background thread. |
| `UpdateTaskManager.java` | `isTaskRunning` | Read from outside `synchronized` blocks. |
| `SlotCacheManager.java` | `cachedStorageSlot`, `cachedExpSlot`, `cachedSpawnerInfoSlot` | Cached GUI slot indices written once and read frequently. |
| `WorldEventHandler.java` | `initialLoadAttempted` | One-shot flag checked from multiple threads during world load events. |
| `DiscordWebhookLogger.java` | `config`, `embedConfigManager` | Hot-reloadable config must be visible to the logging thread. |

---

### 3.6 Concurrent Collections

| File | Collection | Purpose |
|------|------------|---------|
| `SpawnerLocationLockManager.java` | `ConcurrentHashMap<Location, ReentrantLock>` | Location lock registry |
| `SpawnerManager.java` | `ConcurrentHashMap<String, SpawnerData>` | All loaded spawners |
| `SpawnerManager.java` | `ConcurrentHashMap.newKeySet()` (`confirmedGhostSpawners`) | Confirmed ghost spawner IDs |
| `VirtualInventory.java` | `ConcurrentHashMap<ItemSignature, Long>` | Consolidated item storage (note: writes are still protected by `inventoryLock` in `SpawnerData`) |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<UUID, Long>` | Click cooldown timestamps |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<UUID, Scheduler.Task>` | Pending delayed GUI update tasks |
| `SpawnerStackerHandler.java` | `ConcurrentHashMap<String, Set<UUID>>` | Active viewers per spawner |
| `SpawnerHighlightManager.java` | `ConcurrentHashMap<UUID, ScanSession>` | Active scan sessions |
| `SpawnerHighlightManager.java` | `CopyOnWriteArrayList<BlockDisplay>` | Highlight entities (many reads, few writes) |
| `SpawnerRemovalService.java` | `ConcurrentHashMap.newKeySet()` (`pendingRemovalIds`) | In-flight removal IDs |
| `SpawnerRemovalService.java` | `ConcurrentHashMap.newKeySet()` (`pendingRemovalLocations`) | In-flight removal locations |

---

## 4. Asynchronous Execution

### 4.1 Scheduler Abstraction

**File:** `Scheduler.java`

This is the single point of dispatch for every threaded operation in the plugin. It transparently supports both **traditional Paper** (Bukkit scheduler) and **Folia** (region-based scheduler).

| Method | Thread Target | Delay / Period | Use Case |
|--------|---------------|----------------|----------|
| `runTask(Runnable)` | Main / Global region | Immediate | Bukkit API calls that must run on the server thread |
| `runTaskAsync(Runnable)` | Async thread pool | Immediate | Pure computation with no Bukkit API |
| `runTaskLater(Runnable, delay)` | Main / Global region | Delay (ticks) | Deferred synchronous work |
| `runTaskLaterAsync(Runnable, delay)` | Async thread pool | Delay (ticks) | Deferred async work |
| `runTaskTimer(Runnable, delay, period)` | Main / Global region | Repeating | Repeating synchronous task |
| `runTaskTimerAsync(Runnable, delay, period)` | Async thread pool | Repeating | Repeating async task |
| `runEntityTask(Entity, Runnable)` | Entity's region (Folia) | Immediate | Operations tied to a specific entity (e.g. open/close inventory) |
| `runEntityTaskLater/Timer(...)` | Entity's region (Folia) | Delay / Repeating | Delayed or repeating entity-scoped work |
| `runLocationTask(Location, Runnable)` | Location's region (Folia) | Immediate | Block/world operations at a coordinate |
| `runLocationTaskLater/Timer(...)` | Location's region (Folia) | Delay / Repeating | Delayed or repeating location-scoped work |
| `runChunkTask(World, chunkX, chunkZ, Runnable)` | Chunk's region (Folia) | Immediate | Work scoped to a specific chunk |
| `runWorldTask(Location, Runnable)` | World's region (Folia) | Immediate | Work scoped to an entire world |
| `supplySync(Supplier<T>)` | Main / Global region | Immediate | Returns a `CompletableFuture<T>` completed on the sync thread |
| `supplyAsync(Supplier<T>)` | Async thread pool | Immediate | Returns a `CompletableFuture<T>` completed on the async thread |

**Heavy consumers:**
- `SpawnerLootGenerator` — offloads loot math to async, then dispatches inventory updates back to the location thread.
- `SpawnerSellManager` — offloads sell math to async, then dispatches economy deposits back to the location thread.
- `SpawnerHighlightManager` — offloads spawner scanning to async, then renders results on the main thread.
- `SpawnerHologram` — every spawn/update/remove of the `TextDisplay` is dispatched to its owning region thread.
- `SpawnerLocationLockManager` — cleanup task runs as an async timer.
- `SpawnerStackerHandler` — viewer update batching uses `runTaskLater`; inventory close verification uses `runEntityTaskLater`.
- `SpawnerSellConfirmListener` — reopens the previous GUI via `runEntityTask(player, ...)` so `ServerPlayer.initMenu()` runs on the player's own region thread.

---

### 4.2 `CompletableFuture` Async API

**File:** `SpawnerRemovalService.java`

`removeSpawner(SpawnerData)` returns a `CompletableFuture<Boolean>`. The flow:

1. Attempt to claim the removal (location lock + pending sets).
2. If the chunk is already loaded, dispatch directly to `Scheduler.runLocationTask(...)`.
3. If the chunk is **not** loaded, call `world.getChunkAtAsync(chunkX, chunkZ, true)` (Paper async chunk loading).
4. In `whenComplete`, dispatch to `Scheduler.runLocationTask(...)` to remove the block on the correct region thread.
5. Complete the future with `true`/`false` after block removal finishes.

**File:** `SmartSpawnerAPI.java` / `SmartSpawnerAPIImpl.java`

Public API exposes:
- `CompletableFuture<Boolean> removeSpawner(String spawnerId)`
- `CompletableFuture<Boolean> removeSpawner(Location location)`

This allows external plugins to remove spawners safely without worrying about chunk loading or thread context.

---

### 4.3 Detailed Async Flows

#### 4.3.1 Spawner Sell Flow

**File:** `SpawnerSellManager.java`

```
[Location/Main Thread]                [Async Thread]                  [Location/Main Thread]
       |                                     |                                 |
       |-- startSelling() CAS -------------->|                                 |
       |-- close all viewers                   |                                 |
       |-- snapshot virtual inventory          |                                 |
       |-- Scheduler.runTaskAsync() --------->|                                 |
       |                                     |-- calculateSellValue()          |
       |                                     |   (pure CPU, no Bukkit API)     |
       |                                     |-- Scheduler.runLocationTask()   |
       |                                     |                                 |-- applySellResult()
       |                                     |                                 |   + deposit money
       |                                     |                                 |   + remove items
       |                                     |                                 |   + update hologram
       |                                     |                                 |-- onComplete.run()
       |                                     |                                 |-- stopSelling()
```

Key points:
- The **CAS** (`startSelling()`) is the only guard — no nested locks.
- `stopSelling()` is always called in a `finally` block on the location thread, guaranteeing release even if the sell fails mid-way.

---

#### 4.3.2 Loot Generation Flow

**File:** `SpawnerLootGenerator.java`

```
[Timer Thread]                        [Async Thread]                  [Location Thread]
       |                                     |                                 |
       |-- tryLock(lootGenerationLock)        |                                 |
       |-- tryLock(dataLock)                  |                                 |
       |-- read minMobs / maxMobs / slots     |                                 |
       |-- unlock(dataLock)                   |                                 |
       |-- Scheduler.runTaskAsync() -------->|                                 |
       |                                     |-- generateLoot()                |
       |                                     |-- Scheduler.runLocationTask()   |
       |                                     |                                 |-- tryLock(lootGenerationLock)
       |                                     |                                 |-- update inventory & exp
       |                                     |                                 |-- update lastSpawnTime
       |                                     |                                 |-- handleGuiUpdates()
       |                                     |                                 |-- unlock(lootGenerationLock)
       |-- unlock(lootGenerationLock)         |                                 |
```

Key points:
- `lootGenerationLock` is held across the **entire** sync → async → sync boundary.
- `dataLock` is acquired only briefly to read metadata.
- If `tryLock()` fails at any point, the cycle is **skipped** — the server thread is never blocked.

---

#### 4.3.3 Pre-Generated Loot Flow

**File:** `SpawnerLootGenerator.java`

- `preGenerateLoot(...)` runs **async** to calculate loot before the timer expires, storing the result in volatile fields on `SpawnerData`.
- `addPreGeneratedLoot(...)` is called when the timer fires; it dispatches to the location thread and applies the already-calculated loot instantly, eliminating timer stutter.

---

#### 4.3.4 Spawner Removal Flow

**File:** `SpawnerRemovalService.java`

```
[Calling Thread]                      [Async Chunk Loader]            [Location Thread]
       |                                     |                                 |
       |-- claimRemoval()                     |                                 |
       |-- if chunk loaded:                   |                                 |
       |      Scheduler.runLocationTask() ----------------------------------->|
       |-- else:                              |                                 |
       |   world.getChunkAtAsync() --------->|                                 |
       |                                     |-- chunk loaded                   |
       |                                     |-- Scheduler.runLocationTask()   |
       |                                                                     |-- removeBlockAndFinalize()
       |                                                                     |   + set block to AIR
       |                                                                     |   + remove from indexes
       |                                                                     |   + complete future
```

Key points:
- The `CompletableFuture` is completed **exactly once** via `if (!future.isDone()) future.complete(value)`.
- Block mutation always happens on the location thread, even when the chunk had to be loaded asynchronously first.

---

## 5. File-by-File Summary

| File | Lock / Sync | Async / Scheduler | Primary Responsibility |
|------|-------------|-------------------|------------------------|
| `SpawnerData.java` | 3× `ReentrantLock`, `AtomicBoolean`, `synchronized`, `volatile` | None directly | Thread-safe spawner data container |
| `SpawnerLocationLockManager.java` | `ConcurrentHashMap<Location, ReentrantLock>` | `runTaskTimerAsync` | Global location-based race prevention |
| `SpawnerLootGenerator.java` | `lootGenerationLock`, `dataLock` (tryLock) | `runTaskAsync` → `runLocationTask` | Non-blocking loot generation |
| `SpawnerSellManager.java` | `AtomicBoolean selling` (CAS) | `runTaskAsync` → `runLocationTask` | Non-blocking sell execution |
| `SpawnerBreakListener.java` | `locationLockManager.tryLock()` | Callback dispatch via entity/location tasks | Safe spawner breaking |
| `SpawnerStackerHandler.java` | `locationLockManager.tryLock()`, `AtomicBoolean updateLocks` | `runTaskTimer`, `runTaskLater`, `runEntityTask` | Safe GUI stacker |
| `SpawnerRemovalService.java` | `locationLockManager`, pending sets (`ConcurrentHashMap`) | `CompletableFuture`, `getChunkAtAsync`, `runLocationTask` | Async-safe spawner removal |
| `SpawnerHighlightManager.java` | `AtomicBoolean cancelled`, `CopyOnWriteArrayList` | `runTaskAsync` → `runTask` | Async scan + highlight rendering |
| `SpawnerHologram.java` | `AtomicReference<TextDisplay>` | `runLocationTask`, `runEntityTask` | Folia-safe hologram management |
| `VirtualInventory.java` | `ConcurrentHashMap` (internally, guarded by `inventoryLock`) | None | Item storage backing |
| `SpawnerManager.java` | `ConcurrentHashMap` | `runLocationTask`, `runTask` | Spawner registry management |
| `Scheduler.java` | None | Bukkit/Folia scheduler wrapper | Universal dispatch abstraction |
| `LRUCache.java` | `synchronized` methods | None | Thread-safe language cache |
| `UpdateTaskManager.java` | `synchronized` + `volatile` | `runTaskTimer` | GUI update task lifecycle |
| `SpawnerSellConfirmListener.java` | `isSelling()` check | `runEntityTask` (reopen GUI) | Sell confirm GUI orchestration |
| `SmartSpawnerAPIImpl.java` | None directly | `CompletableFuture` | Public async removal API |
| `LanguageManager.java` | `AtomicInteger` (stats) | None | Language cache stats |

---

## 6. Patterns & Best Practices

### 6.1 Lock Ordering (Deadlock Prevention)

When multiple locks are required, they are always acquired in a strict global order:

```
dataLock → inventoryLock
```

`lootGenerationLock` is never nested with `dataLock` or `inventoryLock` in a way that could create a circular dependency.

### 6.2 CAS for Single-Point Guards

`SpawnerData.startSelling()` uses `AtomicBoolean.compareAndSet(false, true)` instead of a `synchronized` block or `ReentrantLock`. This is:

- **Lighter** than a full lock.
- **Non-blocking** when a sell is already in progress (returns `false` immediately).
- **Simpler** than nested lock management.

### 6.3 Try-Lock Non-Blocking Policy

Both `SpawnerLootGenerator` and `SpawnerLocationLockManager` use `tryLock()` instead of `lock()`:

- If the lock is already held, the operation is **skipped** rather than blocked.
- This guarantees that no server thread is ever blocked waiting for a spawner operation.

### 6.4 Folia-Safe Thread Dispatch

Every Bukkit API call goes through `Scheduler.java` with the correct context:

| Operation Type | Scheduler Method |
|----------------|------------------|
| Block / world operations | `runLocationTask(...)` |
| Entity operations (player, display) | `runEntityTask(...)` |
| Global plugin state | `runTask(...)` |
| Pure computation | `runTaskAsync(...)` |

### 6.5 Async → Sync Chain Pattern

Most heavy operations follow this three-phase pattern:

1. **Sync:** Acquire lock, read a snapshot of data.
2. **Async:** Perform CPU-heavy computation (no Bukkit API).
3. **Sync:** Re-acquire lock, apply results, release lock.

This keeps Bukkit API calls on the correct thread while still utilizing async performance.

---

## 7. Warnings & Caveats

1. **`VirtualInventory` uses `ConcurrentHashMap`** internally, but writes are protected by `inventoryLock` in `SpawnerData`. Do **not** call `VirtualInventory.addItems()` directly from multiple threads without going through `SpawnerData`.

2. **`preGeneratedItems` and `preGeneratedExperience` are `volatile`** and accessed via `synchronized` methods. Async callbacks write to them, and the location thread reads them — the combination of `volatile` + `synchronized` ensures visibility and atomicity.

3. **`SpawnerLocationLockManager.unlock()`** only unlocks if `lock.isHeldByCurrentThread()`. This prevents `IllegalMonitorStateException` when `tryLock()` fails but `unlock()` is still reached in a `finally` block.

4. **`CompletableFuture` in `SpawnerRemovalService`** must be completed **exactly once**. The code guards this with `if (!future.isDone()) future.complete(value)`.

5. **Cleanup task** in `SpawnerLocationLockManager` runs on an async timer. When purging old locks, it calls `tryLock()` first to ensure it never deletes a lock that is currently in use.
