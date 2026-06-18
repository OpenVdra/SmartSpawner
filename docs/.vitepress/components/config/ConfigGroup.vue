<script setup>
import { ref, useSlots } from 'vue'

const props = defineProps({
  name: String,
  defaultOpen: {
    type: Boolean,
    default: true
  }
})

const isOpen = ref(props.defaultOpen)
const showInfo = ref(false)
const slots = useSlots()

const toggleInfo = () => {
  if (slots.info) {
    showInfo.value = !showInfo.value
  }
}
</script>

<template>
  <div class="config-group">

    <div class="group-header-line" :class="{ 'has-info': $slots.info }" @click="toggleInfo">

      <div class="arrow-wrapper" @click.stop="isOpen = !isOpen">
        <svg class="arrow" :class="{ open: isOpen }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"></polyline>
        </svg>
      </div>

      <span class="key">{{ name }}:</span>

      <svg v-if="$slots.info" class="info-icon" :class="{ active: showInfo }" viewBox="0 0 24 24" fill="currentColor">
        <polygon points="8 5 19 12 8 19"></polygon>
      </svg>
    </div>

    <transition name="fade-slide">
      <div v-if="showInfo && $slots.info" class="info-box">
        <slot name="info"></slot>
      </div>
    </transition>

    <div v-show="isOpen" class="group-content">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.config-group {
  font-family: var(--vp-font-family-mono);
  font-size: 0.95rem;
  margin-top: 4px;
}

.group-header-line {
  display: inline-flex;
  align-items: center;
  padding: 4px 6px;
  border-radius: 6px;
  transition: background-color 0.2s;
  margin-left: -6px;
}

.group-header-line.has-info {
  cursor: pointer;
}

.group-header-line.has-info:hover {
  background-color: var(--vp-c-bg-soft);
}

.arrow-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
  border-radius: 4px;
  margin-right: 4px;
  transition: background-color 0.2s;
}

.arrow-wrapper:hover {
  background-color: var(--vp-c-default-soft);
}

.arrow {
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
  color: var(--vp-c-text-3);
}

.arrow.open {
  transform: rotate(90deg);
}

.key {
  color: var(--vp-c-text-2);
}

.info-icon {
  width: 11px;
  height: 11px;
  margin-left: 8px;
  color: var(--vp-c-text-3);
  transition: transform 0.2s ease, color 0.2s;
}

.group-header-line:hover .info-icon {
  color: var(--vp-c-brand-1);
}

.info-icon.active {
  transform: rotate(90deg);
  color: var(--vp-c-brand-1);
}

.group-content {
  margin-left: 20px;
  padding-left: 10px;
  border-left: 1px solid var(--vp-c-border);
}

.info-box {
  background-color: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 8px;
  padding: 16px;
  margin: 8px 0 16px 20px;
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
