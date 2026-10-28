---
title: SimpleClaimSystem
---

# ![](https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp){.plugin-icon} SimpleClaimSystem

**Tải về:** [Modrinth](https://modrinth.com/plugin/simpleclaimsystem)

SmartSpawner hỗ trợ cả hai thế hệ chính của SimpleClaimSystem. Tên quyền khác nhau giữa chúng.

## SimpleClaimSystem 1.x

| Thao tác | Quyền |
|---|---|
| Mở menu | `InteractBlocks` |
| Xếp chồng | `InteractBlocks` |
| Phá | `Destroy` |

## SimpleClaimSystem 2.x

| Thao tác | Quyền |
|---|---|
| Mở menu | `interact_spawner` |
| Xếp chồng | `interact_spawner` |
| Phá | `destroy_block` **và** `destroy_spawners` |

Ở 2.x, phá spawner cần bật **cả** `destroy_block` và `destroy_spawners` cho role của người chơi. Chỉ bật một cái là chưa đủ.

Đặt các quyền này theo từng role trong phần cài đặt claim của SimpleClaimSystem. Bên ngoài mọi claim, tất cả thao tác được cho phép.
