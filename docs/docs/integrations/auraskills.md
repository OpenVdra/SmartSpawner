---
title: AuraSkills
---

# ![](https://cdn.modrinth.com/data/uDdZAVls/aadaedbbb7f8c142a625a11081aa0d9bdfafecc2.jpeg){.plugin-icon} AuraSkills

**Download:** [Modrinth](https://modrinth.com/plugin/auraskills)

When AuraSkills is installed, SmartSpawner creates `plugins/SmartSpawner/auraskills.yml`. Each mob can map claimed spawner experience to a skill and ratio:

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

If a zombie spawner holds 100 experience and the ratio is `0.5`, claiming it awards 50 Fighting XP. Unmapped mobs do not award AuraSkills XP.

Supported skill names include `FIGHTING`, `FARMING`, `MINING`, `FORAGING`, `FISHING`, `EXCAVATION`, `ARCHERY`, `DEFENSE`, `ENDURANCE`, `AGILITY`, `ALCHEMY`, `ENCHANTING`, `SORCERY`, `HEALING`, and `FORGING`.
