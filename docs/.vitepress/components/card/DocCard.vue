<script setup>
import { withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

defineProps({
  title: String,
  icon: String,
  // A picture in place of the Lucide icon, such as another plugin's logo.
  image: String,
  // The image is a white logo on transparent, drawn on a dark tile.
  dark: Boolean,
  link: String,
  desc: String
})
</script>

<template>
  <a :href="withBase(link)" class="doc-card">
    <img v-if="image" :src="withBase(image)" alt="" class="card-image" :class="{ 'plugin-icon-dark': dark }" />
    <div v-else-if="icon" class="card-icon">
      <LucideIcon :name="icon" :size="20" />
    </div>
    <div class="card-content">
      <h3 class="card-title">{{ title }}</h3>
      <p class="card-desc">{{ desc }}</p>
    </div>
  </a>
</template>

<style scoped>
.doc-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  text-decoration: none !important;
  transition: border-color 0.25s, background-color 0.25s, transform 0.25s;
}

.doc-card:hover {
  border-color: var(--vp-c-brand-1);
  background-color: var(--vp-c-bg-mute);
  transform: translateY(-2px);
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
  margin-bottom: 14px;
}

.card-image {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  object-fit: contain;
  margin-bottom: 14px;
}

.card-title {
  margin: 0 !important;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.card-desc {
  margin: 6px 0 0 !important;
  font-size: 0.875rem;
  color: var(--vp-c-text-2);
  line-height: 1.5;
}
</style>
