---
title: Lands
---

# ![](/icons/integrations/lands.jpg){.plugin-icon} Lands

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/53313/)

SmartSpawner kiểm tra flag role của Lands theo material `SPAWNER`.

| Thao tác | Flag |
|---|---|
| Mở menu | `INTERACT_CONTAINER` |
| Xếp chồng | `BLOCK_PLACE` |
| Phá | `BLOCK_BREAK` |

## Cấu hình role

Mở menu Lands và bật flag tương ứng cho role bạn muốn cho phép:

```bash
/lands menu
```

- **Interact Container** cho phép mở menu spawner.
- **Block Place** cho phép xếp chồng.
- **Block Break** cho phép phá.

Nếu Lands không được bật ở world chứa spawner, không có kiểm tra nào được áp dụng và thao tác được cho phép.
