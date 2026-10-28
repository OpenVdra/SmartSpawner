---
title: AuraSkills
---

# ![](https://cdn.modrinth.com/data/uDdZAVls/aadaedbbb7f8c142a625a11081aa0d9bdfafecc2.jpeg){.plugin-icon} AuraSkills

**Tải về:** [Modrinth](https://modrinth.com/plugin/auraskills)

Khi cài AuraSkills, SmartSpawner tạo `plugins/SmartSpawner/auraskills.yml`. Mỗi mob có thể ánh xạ kinh nghiệm nhận từ spawner sang một kỹ năng và tỷ lệ:

```yaml
enabled: true

entity_skills:
  zombie:
    skill: FIGHTING
    ratio: 0.5
  cow:
    skill: FARMING
    ratio: 0.3
```

Nếu zombie spawner chứa 100 kinh nghiệm và tỷ lệ là `0.5`, người chơi nhận 50 Fighting XP. Mob chưa ánh xạ sẽ không trao AuraSkills XP.

Tên kỹ năng hỗ trợ gồm `FIGHTING`, `FARMING`, `MINING`, `FORAGING`, `FISHING`, `EXCAVATION`, `ARCHERY`, `DEFENSE`, `ENDURANCE`, `AGILITY`, `ALCHEMY`, `ENCHANTING`, `SORCERY`, `HEALING` và `FORGING`.
