<script setup>
defineProps({
  title: String,
  icon: String,
  link: String,
  desc: String
})
</script>

<template>
  <a :href="link" class="doc-card">
    <div class="card-icon">{{ icon }}</div>
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
  font-size: 2.5rem;
  margin-bottom: 16px;
}

.card-title {
  margin: 0 !important;
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.card-desc {
  margin: 8px 0 0 0 !important;
  font-size: 0.9rem;
  color: var(--vp-c-text-2);
  line-height: 1.5;
}
</style>
