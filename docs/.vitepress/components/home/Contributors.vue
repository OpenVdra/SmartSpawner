<script setup>
import { ref, onMounted } from 'vue'

const contributors = ref([])
const loading = ref(true)
const error = ref(false)

onMounted(async () => {
  try {
    const res = await fetch('https://api.github.com/repos/OpenVdra/SmartSpawner/contributors?per_page=50')
    if (!res.ok) throw new Error()
    contributors.value = await res.json()
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="contributors-section">
    <div class="contributors-inner">
      <h2 class="contributors-title">Contributors</h2>
      <p class="contributors-sub">
        SmartSpawner is open source and built with help from the community.<br>
        Every contribution, big or small, makes a difference.
      </p>

      <div v-if="loading" class="contributors-placeholder">
        <div v-for="i in 8" :key="i" class="avatar-skeleton" />
      </div>

      <div v-else-if="error" class="contributors-error">
        Could not load contributors. View them on
        <a href="https://github.com/OpenVdra/SmartSpawner/graphs/contributors" target="_blank" rel="noopener noreferrer">GitHub</a>.
      </div>

      <div v-else class="contributors-grid">
        <a
          v-for="c in contributors"
          :key="c.id"
          :href="c.html_url"
          target="_blank"
          rel="noopener noreferrer"
          class="contributor"
          :title="c.login"
        >
          <img :src="c.avatar_url + '&s=80'" :alt="c.login" class="contributor-avatar" loading="lazy" />
          <span class="contributor-name">{{ c.login }}</span>
        </a>
      </div>

      <a
        href="https://github.com/OpenVdra/SmartSpawner/graphs/contributors"
        target="_blank"
        rel="noopener noreferrer"
        class="contributors-cta"
      >
        View all on GitHub →
      </a>
    </div>
  </div>
</template>

<style scoped>
.contributors-section {
  border-top: 1px solid var(--vp-c-border);
  padding: 64px 24px 80px;
}

.contributors-inner {
  max-width: 900px;
  margin: 0 auto;
  text-align: center;
}

.contributors-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin: 0 0 10px;
  letter-spacing: -0.02em;
}

.contributors-sub {
  color: var(--vp-c-text-2);
  font-size: 0.97rem;
  line-height: 1.7;
  margin: 0 0 36px;
}

.contributors-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 14px;
  justify-content: center;
  margin-bottom: 32px;
}

.contributor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  text-decoration: none;
  color: var(--vp-c-text-2);
  transition: color 0.18s, transform 0.18s;
}

.contributor:hover {
  color: var(--vp-c-brand-1);
  transform: translateY(-3px);
}

.contributor-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 2px solid var(--vp-c-border);
  background-color: var(--vp-c-bg-mute);
  transition: border-color 0.18s, box-shadow 0.18s;
}

.contributor:hover .contributor-avatar {
  border-color: var(--vp-c-brand-1);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--vp-c-brand-1) 20%, transparent);
}

.contributor-name {
  font-size: 0.75rem;
  font-weight: 500;
  max-width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contributors-placeholder {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 14px;
  justify-content: center;
  margin-bottom: 32px;
}

.avatar-skeleton {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(90deg, var(--vp-c-bg-mute) 25%, var(--vp-c-bg-soft) 50%, var(--vp-c-bg-mute) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.contributors-error {
  color: var(--vp-c-text-2);
  margin-bottom: 24px;
}

.contributors-error a {
  color: var(--vp-c-brand-1);
  text-decoration: underline;
}

.contributors-cta {
  display: inline-block;
  color: var(--vp-c-brand-1);
  font-size: 0.9rem;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid color-mix(in srgb, var(--vp-c-brand-1) 30%, transparent);
  padding: 8px 20px;
  border-radius: 20px;
  transition: background-color 0.18s, color 0.18s;
}

.contributors-cta:hover {
  background-color: var(--vp-c-brand-soft);
}
</style>
