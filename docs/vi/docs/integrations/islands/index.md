---
title: Đảo và Plot
---

# Đảo và Plot

Giống [bảo vệ](/vi/docs/integrations/protections/), hook đảo và plot hoạt động tự động. SmartSpawner hỏi plugin trước khi người chơi mở menu, xếp chồng hoặc phá spawner.

::: tip Ai bỏ qua mọi kiểm tra
Operator và người chơi có quyền wildcard `*` bỏ qua toàn bộ kiểm tra. Hãy thử bằng tài khoản người chơi thường.
:::

## Tra cứu nhanh

| Plugin | Mở menu | Xếp chồng | Phá |
|---|---|---|---|
| ![](/icons/integrations/plotsquared.png){.plugin-icon} [PlotSquared](/vi/docs/integrations/islands/plotsquared) | Được add / trust vào plot | Được add / trust vào plot | Được add / trust vào plot |
| ![](/icons/integrations/mineplots.svg){.plugin-icon} [minePlots](/vi/docs/integrations/islands/mineplots) | Có quyền plot | Có quyền plot | Có quyền plot |
| ![](/icons/integrations/superiorskyblock2.png){.plugin-icon} [SuperiorSkyblock2](/vi/docs/integrations/islands/superiorskyblock2) | `spawner_open_menu` | `spawner_stack` | Không kiểm tra |
| ![](https://cdn.modrinth.com/data/aBVLHiAW/2ad201b237497093c4957095fa9f97bf61708ed1_96.webp){.plugin-icon} [BentoBox](/vi/docs/integrations/islands/bentobox) | `CONTAINER` | `PLACE_BLOCKS` | Không kiểm tra |
| ![](https://cdn.modrinth.com/data/uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png){.plugin-icon} [IridiumSkyblock](/vi/docs/integrations/islands/iridiumskyblock) | `SpawnerOpenMenuPermission` | `SpawnerStackPermission` | Không kiểm tra |

Bên ngoài mọi plot hoặc đảo, tất cả thao tác đều được cho phép mặc định.
