---
title: SuperiorSkyblock2
---

# ![](/icons/integrations/superiorskyblock2.png){.plugin-icon} SuperiorSkyblock2

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/87411/)

Khi khởi động, SmartSpawner đăng ký hai đặc quyền đảo.

| Thao tác | Đặc quyền |
|---|---|
| Mở menu | `spawner_open_menu` |
| Xếp chồng | `spawner_stack` |
| Phá | Không kiểm tra |

Phá không có đặc quyền riêng của SmartSpawner. Cơ chế bảo vệ phá khối tích hợp sẵn của SuperiorSkyblock2 đã bảo vệ khối spawner, nên người chơi không thể phá khối trên đảo thì cũng không phá được spawner.

## Gán đặc quyền

Cấp các đặc quyền cho các role đảo cần dùng spawner qua menu quyền của đảo:

```bash
/is permissions
```

## Dọn dẹp

Khi một đảo bị **giải tán**, SmartSpawner tự động dọn các spawner mà nó đang theo dõi trên đảo đó. Bên ngoài mọi đảo, spawner không bị giới hạn.
