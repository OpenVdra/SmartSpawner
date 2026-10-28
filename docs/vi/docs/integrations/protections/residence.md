---
title: Residence
---

# ![](/icons/integrations/residence.jpg){.plugin-icon} Residence

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/11480/)

SmartSpawner ánh xạ thao tác spawner tới flag của Residence. Lưu ý mở menu dùng flag `use`, **không** phải `container`.

| Thao tác | Flag |
|---|---|
| Mở menu | `use` |
| Xếp chồng | `build` |
| Phá | `build` |

## Cấu hình flag

```bash
# Cả residence
/res set <residence> use true
/res set <residence> build true

# Theo từng người chơi
/res pset <residence> <player> use,build true
```

Bên ngoài mọi residence, tất cả thao tác được cho phép. Nếu flag chưa được đặt, mặc định toàn cục của Residence cho flag đó sẽ áp dụng.
