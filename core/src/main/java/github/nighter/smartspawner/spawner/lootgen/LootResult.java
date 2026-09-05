package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.spawner.model.ItemSignature;

import java.util.Map;

public record LootResult(Map<ItemSignature, Long> items, long experience) {}
