---
title: Tương thích plugin
---

# Tương Thích Plugin

SmartSpawner tự phát hiện plugin tùy chọn và chỉ bật những tích hợp có trên máy chủ.

## Các Tích Hợp Được Hỗ Trợ

<CardGrid>

<FeatureCard icon="ShieldCheck" title="Bảo Vệ và Claim">

- [WorldGuard](/vi/docs/integrations/protections/worldguard)
- [GriefPrevention](/vi/docs/integrations/protections/griefprevention)
- [Lands](/vi/docs/integrations/protections/lands)
- [Towny Advanced](/vi/docs/integrations/protections/towny)
- [SimpleClaimSystem](/vi/docs/integrations/protections/simpleclaimsystem)
- [RedProtect](/vi/docs/integrations/protections/redprotect)
- [Residence](/vi/docs/integrations/protections/residence)
- [FactionsUUID](/vi/docs/integrations/protections/factionsuuid)
- [BlockLocker](/vi/docs/integrations/protections/blocklocker)

</FeatureCard>

<FeatureCard icon="Home" title="Đảo và Plot">

- [PlotSquared](/vi/docs/integrations/islands/plotsquared)
- [minePlots](/vi/docs/integrations/islands/mineplots)
- [SuperiorSkyblock2](/vi/docs/integrations/islands/superiorskyblock2)
- [BentoBox](/vi/docs/integrations/islands/bentobox) *(cần thiết lập, xem [tài liệu BentoBox](https://docs.bentobox.world))*
- [IridiumSkyblock](/vi/docs/integrations/islands/iridiumskyblock)

</FeatureCard>

<FeatureCard icon="ShoppingCart" title="Cửa Hàng và Kinh Tế">

- [EconomyShopGUI](/vi/docs/integrations/shops/economyshopgui) *(bản free và Premium)*
- [ShopGUI+](/vi/docs/integrations/shops/shopguiplus)
- [zShop](/vi/docs/integrations/shops/zshop)
- [Vault](/vi/docs/integrations/shops/vault)
- [ExcellentEconomy](/vi/docs/integrations/shops/excellenteconomy)

</FeatureCard>

<FeatureCard icon="Globe2" title="Quản Lý Thế Giới">

- Multiverse-Core
- MultiWorld
- Worlds

</FeatureCard>

<FeatureCard icon="Swords" title="RPG và Mob">

- **[AuraSkills](/vi/docs/integrations/auraskills)**: XP từ spawner được tính vào kỹ năng
- **[MythicMobs](/vi/docs/integrations/mythicmobs)**: Bảng vật phẩm mob tùy chỉnh
- **[MMOItems](/vi/docs/integrations/mmoitems)**: Vật phẩm MMOItems làm drop và làm Item Spawner

</FeatureCard>

</CardGrid>

## Xung Đột Đã Biết

Các plugin sau có thể ghi đè hành vi spawner và xung đột với SmartSpawner. Nếu chạy mà không thực hiện thay đổi bên dưới, chúng có thể ghi đè SmartSpawner và gây lỗi.

| Plugin | Việc cần làm |
| --- | --- |
| WildStacker | Đặt `spawners: enabled:` thành `false` trong `config.yml` của nó. |
| RoseStacker | Đặt `stacking-enabled:` thành `false` trong `config.yml` của nó. |
| SpawnerMeta | Gỡ hoặc tắt plugin. Nó ghi đè các tính năng của SmartSpawner. |
