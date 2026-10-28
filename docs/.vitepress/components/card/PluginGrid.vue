<script setup lang="ts">
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import { ISLANDS, OTHERS, PROTECTIONS, SHOPS, type Plugin } from '../../plugins'

// Every plugin SmartSpawner hooks into, grouped like the sidebar, with the same icons.
const { lang } = useData()
const vi = computed(() => lang.value === 'vi')
const prefix = computed(() => (vi.value ? '/vi/docs/integrations/' : '/docs/integrations/'))

const groups = computed<{ title: string; plugins: Plugin[] }[]>(() => [
  { title: vi.value ? 'Cửa hàng và kinh tế' : 'Shops and economy', plugins: SHOPS },
  { title: vi.value ? 'Bảo vệ và claim' : 'Protections and claims', plugins: PROTECTIONS },
  { title: vi.value ? 'Đảo và plot' : 'Islands and plots', plugins: ISLANDS },
  { title: vi.value ? 'Khác' : 'Other', plugins: OTHERS }
])
</script>

<template>
  <div class="plugin-grid-groups">
    <div v-for="group in groups" :key="group.title" class="plugin-grid-group">
      <h3 class="plugin-grid-title">{{ group.title }}</h3>
      <div class="plugin-grid">
        <a
          v-for="plugin in group.plugins"
          :key="plugin.name"
          class="plugin-grid-item"
          :href="withBase(prefix + plugin.page)"
        >
          <img class="plugin-grid-icon" :class="{ 'plugin-icon-dark': plugin.dark }" :src="withBase(plugin.icon)" alt="" loading="lazy" />
          <span class="plugin-grid-name">{{ plugin.name }}</span>
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plugin-grid-group + .plugin-grid-group {
  margin-top: 8px;
}

.plugin-grid-title {
  margin: 24px 0 12px !important;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--vp-c-text-2);
}

.plugin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 10px;
}

.plugin-grid-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 10px;
  text-decoration: none !important;
  transition: border-color 0.25s, background-color 0.25s, transform 0.25s;
}

.plugin-grid-item:hover {
  border-color: var(--vp-c-brand-1);
  background-color: var(--vp-c-bg-mute);
  transform: translateY(-2px);
}

.plugin-grid-icon {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: contain;
}

.plugin-grid-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
