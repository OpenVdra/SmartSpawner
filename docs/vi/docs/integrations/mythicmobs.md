---
title: MythicMobs
---

# ![](https://cdn.modrinth.com/data/NLuc7Jjh/80ddd8e4535a8305eac4edfd5ced1e8e5344e11d_96.webp){.plugin-icon .plugin-icon-dark} MythicMobs

**Tải về:** [Modrinth](https://modrinth.com/plugin/mythicmobs)

SmartSpawner đăng ký drop MythicMobs tên `smartspawner`. Dùng nó trong bảng drop MythicMobs để tạo vật phẩm Smart Spawner:

```yaml
ExampleDrops:
  Drops:
    - smartspawner ZOMBIE 1
    - smartspawner SKELETON 1-3
```

Cú pháp:

```text
smartspawner <ENTITY_TYPE> [amount|minimum-maximum]
```

Mob phải tương ứng với một `EntityType` Bukkit hợp lệ. Tên mob hoặc khoảng giá trị không hợp lệ sẽ bị từ chối và ghi vào log.
