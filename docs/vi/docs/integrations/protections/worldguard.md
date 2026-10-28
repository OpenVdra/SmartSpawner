---
title: WorldGuard
---

# ![](https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp){.plugin-icon} WorldGuard

**Tải về:** [Modrinth](https://modrinth.com/plugin/worldguard)

SmartSpawner ánh xạ mỗi thao tác spawner tới một flag region của WorldGuard.

| Thao tác | Flag |
|---|---|
| Mở menu | `interact` |
| Xếp chồng | `block-place` |
| Phá | `block-break` |

Phép kiểm tra dùng build test của WorldGuard, nên **thành viên region luôn được phép**. Với người ngoài (ví dụ region công cộng hoặc toàn cục), bạn phải bật flag một cách rõ ràng.

## Cho phép dùng spawner trong region

```bash
/rg flag <region> interact allow
/rg flag <region> block-place allow
/rg flag <region> block-break allow
```

## Chặn dùng spawner với tất cả

Đặt flag thành `deny` sẽ chặn kể cả thành viên:

```bash
/rg flag <region> interact deny
```

Muốn áp dụng cho cả world, đặt các flag này trên region `__global__`.

## Bỏ qua

Operator và người chơi có `worldguard.region.bypass` sẽ bỏ qua các flag này.
