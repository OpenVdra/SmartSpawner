---
title: Bảo vệ và Claim
---

# Bảo Vệ và Claim

Hook bảo vệ hoạt động **tự động**, không có công tắc trong `config.yml`. Khi có plugin bảo vệ được hỗ trợ, SmartSpawner sẽ hỏi plugin đó trước ba thao tác:

- **Mở menu**: chuột phải vào spawner để mở GUI.
- **Xếp chồng**: thêm spawner vào chồng.
- **Phá**: đào hoặc gỡ spawner.

Flag hoặc mức trust quyết định từng thao tác nằm bên trong plugin bảo vệ, không nằm trong SmartSpawner. Chọn plugin của bạn bên dưới để xem cấu hình chính xác.

::: tip Ai bỏ qua mọi kiểm tra
Operator và người chơi có quyền wildcard `*` (cùng với `worldguard.region.bypass` cho WorldGuard) bỏ qua toàn bộ kiểm tra bảo vệ. Hãy thử bằng tài khoản người chơi thường.
:::

## Tra cứu nhanh

| Plugin | Mở menu | Xếp chồng | Phá |
|---|---|---|---|
| ![](https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp){.plugin-icon} [WorldGuard](/vi/docs/integrations/protections/worldguard) | `interact` | `block-place` | `block-break` |
| ![](https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp){.plugin-icon} [GriefPrevention](/vi/docs/integrations/protections/griefprevention) | Container trust | Build trust | Build trust |
| ![](/icons/integrations/lands.jpg){.plugin-icon} [Lands](/vi/docs/integrations/protections/lands) | `INTERACT_CONTAINER` | `BLOCK_PLACE` | `BLOCK_BREAK` |
| ![](https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp){.plugin-icon} [Towny](/vi/docs/integrations/protections/towny) | Cư dân / trusted | Cư dân / trusted | Cư dân / trusted |
| ![](/icons/integrations/residence.jpg){.plugin-icon} [Residence](/vi/docs/integrations/protections/residence) | `use` | `build` | `build` |
| ![](https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp){.plugin-icon} [RedProtect](/vi/docs/integrations/protections/redprotect) | `chest` | `build` | Không kiểm tra |
| ![](https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp){.plugin-icon} [SimpleClaimSystem](/vi/docs/integrations/protections/simpleclaimsystem) | `InteractBlocks` / `interact_spawner` | Như trên | `Destroy` / quyền phá |
| ![](/icons/integrations/factionsuuid.png){.plugin-icon} [FactionsUUID](/vi/docs/integrations/protections/factionsuuid) | `CONTAINER` | `BUILD` | `DESTROY` |
| ![](https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1){.plugin-icon} [BlockLocker](/vi/docs/integrations/protections/blocklocker) | Chủ / tin cậy | Chủ / tin cậy | Chủ / tin cậy |

Bên ngoài mọi region hoặc claim, tất cả thao tác đều được cho phép mặc định.

Với các plugin plot và đảo (PlotSquared, SuperiorSkyblock2, BentoBox, v.v.), xem [Đảo và Plot](/vi/docs/integrations/islands/).
