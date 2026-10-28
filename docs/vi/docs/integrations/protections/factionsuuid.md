---
title: FactionsUUID
---

# ![](/icons/integrations/factionsuuid.png){.plugin-icon} FactionsUUID

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/1035/)

SmartSpawner kiểm tra quyền lãnh thổ của FactionsUUID cho cả ba thao tác.

| Thao tác | Quyền truy cập |
|---|---|
| Mở menu | `CONTAINER` |
| Xếp chồng | `BUILD` |
| Phá | `DESTROY` |

## Cách quyết định quyền

Bên trong đất đã claim của một faction, người chơi chỉ được phép khi faction cấp cho họ quyền tương ứng. Quyền được đặt theo vai trò và theo quan hệ (thành viên, đồng minh, đình chiến, trung lập, kẻ thù) bởi chủ faction.

- Thành viên của faction sở hữu đất thường có toàn quyền.
- Người chơi khác theo quyền quan hệ mà faction đã cấu hình.
- Ở wilderness, safezone và warzone, hook này cho phép thao tác và để các plugin khác của bạn quyết định.

Dùng `/f perm` để xem và chỉnh quyền truy cập của một faction.

::: warning Bản FactionsUUID nào
Hook này nhắm vào FactionsUUID hiện đại (`dev.kitteh`, phiên bản 4.x trở lên), tải từ [factions.support](https://factions.support/). Các bản `com.massivecraft` cũ dùng API khác và không được hỗ trợ.
:::
