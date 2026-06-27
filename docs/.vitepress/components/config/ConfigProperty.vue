<script setup>
import { ref } from 'vue'

defineProps({
  name: String,
  value: {
    type: [String, Number, Boolean, Array],
    required: true
  },
  type: {
    type: String,
    default: 'string'
  }
})

const showInfo = ref(false)
</script>

<template>
  <div class="config-property">
    <div class="property-line" @click="showInfo = !showInfo" :class="{ 'has-info': $slots.default }">

      <div class="property-header">
        <div class="spacer"></div>

        <span class="key">{{ name }}:</span>
        <span v-if="type !== 'list'" class="value" :class="type">{{ value }}</span>

        <svg v-if="$slots.default" class="info-icon" :class="{ active: showInfo }" viewBox="0 0 24 24" fill="currentColor">
          <polygon points="8 5 19 12 8 19"></polygon>
        </svg>
      </div>

      <div v-if="type === 'list'" class="yaml-list">
        <div v-for="(item, index) in value" :key="index" class="yaml-list-item">
          <span class="yaml-dash">-</span>
          <span class="value string">'{{ item }}'</span>
        </div>
      </div>

    </div>

    <transition name="fade-slide">
      <div v-if="showInfo && $slots.default" class="info-box">
        <slot></slot>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.config-property {
  font-family: var(--vp-font-family-mono);
  font-size: 0.95rem;
  margin: 4px 0;
}

.property-line {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 4px 6px;
  border-radius: 6px;
  transition: background-color 0.2s;
  margin-left: -6px;
}

.property-line.has-info {
  cursor: pointer;
}

.property-line.has-info:hover {
  background-color: var(--vp-c-bg-soft);
}

.property-header {
  display: inline-flex;
  align-items: center;
}

.spacer {
  width: 20px;
  height: 20px;
  margin-right: 4px;
  flex-shrink: 0;
}

.key {
  color: var(--vp-c-text-2);
  margin-right: 8px;
}
.value.boolean { color: #a8d642; }
.value.number { color: #ff9800; }
.value.string { color: #4caf50; }

.yaml-list {
  display: flex;
  flex-direction: column;
  margin-top: 4px;
  margin-left: 24px;
}
.yaml-list-item {
  display: flex;
  margin-top: 2px;
  line-height: 1.5;
}
.yaml-dash {
  color: var(--vp-c-text-2);
  margin-right: 8px;
  user-select: none;
}

.info-icon {
  width: 11px;
  height: 11px;
  margin-left: 8px;
  color: var(--vp-c-text-3);
  transition: transform 0.2s ease, color 0.2s;
}
.property-header:hover .info-icon,
.property-line:hover .info-icon {
  color: var(--vp-c-brand-1);
}
.info-icon.active {
  transform: rotate(90deg);
  color: var(--vp-c-brand-1);
}

.info-box {
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 8px;
  padding: 16px;
  margin: 8px 0 12px 24px;
  font-family: var(--vp-font-family-base);
  font-size: 0.95rem;
  line-height: 1.5;
  color: var(--vp-c-text-1);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.fade-slide-enter-active, .fade-slide-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}
</style>
