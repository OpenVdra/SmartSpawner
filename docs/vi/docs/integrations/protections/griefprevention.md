---
title: GriefPrevention
---

# ![](https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp){.plugin-icon} GriefPrevention

**Tải về:** [Modrinth](https://modrinth.com/plugin/griefprevention)

SmartSpawner dùng cấp độ trust riêng cho từng thao tác spawner, giống cách GriefPrevention xử lý các block khác. Mở menu kho của spawner cần **container trust**. Xếp chồng và phá spawner cần **build trust**.

| Thao tác | Yêu cầu |
|---|---|
| Mở menu | Container trust |
| Xếp chồng | Build trust |
| Phá | Build trust |

Build trust cũng bao gồm mở menu, nên người có build trust làm được mọi thao tác.

## Cấp quyền

```bash
/containertrust <player>
```

`/containertrust` cho phép người chơi mở và dùng menu kho của spawner.

```bash
/trust <player>
```

`/trust` cấp build trust đầy đủ, bao gồm mở, xếp chồng và phá spawner.

## Các lệnh không đủ

- `/accesstrust` chỉ cho phép nút bấm và cửa, không mở được menu kho.

Bên ngoài mọi claim, tất cả thao tác đều được cho phép.
