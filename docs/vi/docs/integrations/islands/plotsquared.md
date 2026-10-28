---
title: PlotSquared
---

# ![](/icons/integrations/plotsquared.png){.plugin-icon} PlotSquared

**Tải về:** [Modrinth](https://modrinth.com/plugin/plotsquared)

Quyền truy cập được quyết định bởi tư cách thành viên plot. Người chơi chỉ có thể mở, xếp chồng hoặc phá spawner nếu họ được **add hoặc trust** vào plot.

| Thao tác | Yêu cầu |
|---|---|
| Mở menu | Được add / trust vào plot |
| Xếp chồng | Được add / trust vào plot |
| Phá | Được add / trust vào plot |

## Cấp quyền

```bash
/plot trust <player>
/plot add <player>
```

## Dọn dẹp

Khi một plot bị xóa, SmartSpawner tự động gỡ các spawner mà nó đang theo dõi trên plot đó. Bên ngoài mọi plot, spawner không bị giới hạn.
