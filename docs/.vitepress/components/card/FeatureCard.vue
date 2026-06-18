<script setup>
defineProps({
  title: String,
  icon: String
})
</script>

<template>
  <div class="feature-card">
    <div class="fc-header">
      <div class="fc-icon">{{ icon }}</div>
      <h3 class="fc-title">{{ title }}</h3>
    </div>
    <div class="fc-content">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.feature-card {
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
  transition: border-color 0.25s, background-color 0.25s;
}

.feature-card:hover {
  border-color: var(--vp-c-brand-1);
}

.fc-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.fc-icon {
  font-size: 1.8rem;
  background: var(--vp-c-bg-mute);
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.fc-title {
  margin: 0 !important;
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.fc-content {
  font-size: 0.95rem;
  color: var(--vp-c-text-2);
  line-height: 1.6;
  flex-grow: 1;
}

.fc-content :deep(img) {
  border-radius: 8px;
  margin-top: 16px;
  border: 1px solid var(--vp-c-border);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  width: 100%;
  height: auto;
}
</style>
