---
title: RedProtect
---

# ![](https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp){.plugin-icon} RedProtect

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/15841/)

SmartSpawner kiểm tra quyền region của RedProtect cho hai trong ba thao tác.

| Thao tác | Quyền |
|---|---|
| Mở menu | `chest` (canChest) |
| Xếp chồng | `build` (canBuild) |
| Phá | Không được hook này kiểm tra |

## Ghi chú

- Mở menu cần quyền **chest** của region.
- Xếp chồng cần quyền **build** của region.
- Phá không bị hook RedProtect chặn. Bảo vệ phá khối riêng của RedProtect vẫn áp dụng qua sự kiện Bukkit thông thường, nên người chơi không thể phá khối trong region thì cũng không phá được spawner.

Có thể chỉnh flag mặc định trong `plugins/RedProtect/<world>/config.yml` và các file flag. Dùng `/rp flag info` để xem một region.
