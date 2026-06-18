---
layout: home

hero:
  name: "SmartSpawner"
  text: "Next-Gen Spawner Plugin"
  tagline: High-performance GUI spawner management. Generates items and XP without ever spawning a mob.
  image:
    src: /logo.png
    alt: SmartSpawner
  actions:
    - theme: brand
      text: Get Started
      link: /docs/
    - theme: alt
      text: Features
      link: /docs/features
    - theme: alt
      text: Download
      link: /download

features:
  - title: 🚀 Zero Lag Design
    details: Spawners generate drops and experience directly. No mobs, no entity lag, no TPS drops.
    link: /docs/features
  - title: 📦 Stackable Spawners
    details: Stack hundreds of spawners into a single block. Scale your farm without cluttering your world.
    link: /docs/features
  - title: 🛒 Shop Integration
    details: Sell directly from spawner storage. Supports EconomyShopGUI, ShopGUI+, zShop, and more.
    link: /docs/features
  - title: ⚙️ Fully Configurable
    details: Customize every drop, every GUI layout, every message. Full control over your spawner experience.
    link: /docs/configuration
  - title: 🔌 Developer API
    details: Extend SmartSpawner with your own plugins using a clean, documented Java API.
    link: /docs/developer-api/
  - title: 🔓 Open Source
    details: SmartSpawner is completely free and open source on GitHub. Contribute, fork, or build your own integrations on top of it.
    link: https://github.com/OpenVdra/SmartSpawner
---

<div class="home-stats">
  <div class="home-stats-inner">
    <h2 class="home-stats-title">Trusted by 3,000+ Servers</h2>
    <p class="home-stats-sub">SmartSpawner is running on thousands of Minecraft servers worldwide. See live usage data on bStats.</p>
    <a href="https://bstats.org/plugin/bukkit/SmartSpawner" target="_blank" rel="noopener noreferrer">
      <img src="https://bstats.org/signatures/bukkit/SmartSpawner.svg" alt="bStats Statistics" class="home-stats-chart" />
    </a>
  </div>
</div>

<style>
.home-stats {
  border-top: 1px solid var(--vp-c-border);
  padding: 64px 24px;
}
.home-stats-inner {
  max-width: 900px;
  margin: 0 auto;
  text-align: center;
}
.home-stats-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin: 0 0 10px;
  letter-spacing: -0.02em;
}
.home-stats-sub {
  color: var(--vp-c-text-2);
  font-size: 0.97rem;
  line-height: 1.7;
  margin: 0 0 32px;
}
.home-stats-chart {
  border-radius: 10px;
  max-width: 860px;
  width: 100%;
  display: block;
  margin: 0 auto;
}
</style>

<Contributors />
