---
title: Towny
---

# ![](https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp){.plugin-icon} Towny

**Tải về:** [Modrinth](https://modrinth.com/plugin/towny)

Ở tích hợp này Towny không có flag riêng cho spawner. Quyền truy cập được quyết định hoàn toàn bởi tư cách thành viên town.

| Thao tác | Yêu cầu |
|---|---|
| Mở menu | Cư dân hoặc trusted resident của town |
| Xếp chồng | Cư dân hoặc trusted resident của town |
| Phá | Cư dân hoặc trusted resident của town |

## Cách hoạt động

- Người chơi chỉ có thể mở, xếp chồng hoặc phá spawner nếu họ là **cư dân** của town sở hữu plot, hoặc **trusted resident** của town đó.
- Bên ngoài town, spawner không bị giới hạn.
- Các dòng quyền `switch` và `destroy` của chính Towny **không** được dùng cho kiểm tra này. Điều quan trọng là tư cách thành viên.

## Cấp quyền

Thêm người chơi vào town, hoặc trust họ:

```bash
/town add <player>
/town trust add <player>
```
