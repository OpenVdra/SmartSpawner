<script setup>
import { ref } from 'vue'

defineProps({
  commands: {
    type: [String, Array],
    required: true
  },
  aliases: {
    type: [String, Array],
    default: () => []
  },
  permission: {
    type: String,
    default: null
  }
})

const toArray = (val) => Array.isArray(val) ? val : (val ? [val] : [])

const copiedItem = ref(null)

const copyToClipboard = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copiedItem.value = text
    setTimeout(() => { copiedItem.value = null }, 2000)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    Object.assign(ta.style, { position: 'fixed', opacity: '0' })
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); copiedItem.value = text; setTimeout(() => { copiedItem.value = null }, 2000) } catch {}
    document.body.removeChild(ta)
  }
}
</script>

<template>
  <div class="command-row">
    <div class="header">
      <div class="badges">
        <span
          v-for="cmd in toArray(commands)"
          :key="cmd"
          class="badge cmd-badge clickable"
          :class="{ 'is-copied': copiedItem === cmd }"
          @click="copyToClipboard(cmd)"
          title="Click to copy"
        >{{ cmd }}</span>
        <span
          v-for="alias in toArray(aliases)"
          :key="alias"
          class="badge alias-badge clickable"
          :class="{ 'is-copied': copiedItem === alias }"
          @click="copyToClipboard(alias)"
          title="Alias — click to copy"
        >{{ alias }}</span>
      </div>
      <div v-if="permission" class="perm-line">
        <span class="perm-label">Permission:</span>
        <span
          class="badge perm-badge clickable"
          :class="{ 'is-copied': copiedItem === permission }"
          @click="copyToClipboard(permission)"
          title="Click to copy"
        >{{ permission }}</span>
      </div>
    </div>
    <div class="desc">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.command-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--vp-c-border);
  transition: background-color 0.2s;
}

.command-row:last-child { border-bottom: none; }
.command-row:hover { background-color: var(--vp-c-bg-mute); }

.header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.badge {
  position: relative;
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-family: var(--vp-font-family-mono);
  font-size: 0.82em;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.18s ease;
  line-height: 1.5;
}

.badge.clickable {
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
}

.badge.clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0,0,0,0.12);
}

.badge.clickable:active { transform: translateY(0); }

.cmd-badge {
  background-color: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
  border: 1px solid color-mix(in srgb, var(--vp-c-brand-1) 20%, transparent);
}

.alias-badge {
  background-color: var(--vp-c-default-soft);
  color: var(--vp-c-text-2);
  border: 1px solid var(--vp-c-divider);
  font-size: 0.78em;
}

.perm-badge {
  background-color: var(--vp-c-indigo-soft);
  color: var(--vp-c-indigo-1);
  border: 1px solid color-mix(in srgb, var(--vp-c-indigo-1) 20%, transparent);
}

.cmd-badge.is-copied,
.alias-badge.is-copied { background-color: #22c55e !important; color: white !important; border-color: #16a34a !important; }
.perm-badge.is-copied { background-color: var(--vp-c-indigo-1) !important; color: white !important; }

.badge.is-copied::after {
  content: 'Copied!';
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  background-color: var(--vp-c-text-1);
  color: var(--vp-c-bg);
  padding: 3px 8px;
  border-radius: 4px;
  font-family: var(--vp-font-family-base);
  font-size: 0.72rem;
  font-weight: 700;
  pointer-events: none;
  white-space: nowrap;
  animation: popIn 0.18s ease forwards;
  z-index: 10;
}

.badge.is-copied::before {
  content: '';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-top-color: var(--vp-c-text-1);
  pointer-events: none;
  animation: popIn 0.18s ease forwards;
  z-index: 10;
}

@keyframes popIn {
  0% { opacity: 0; transform: translate(-50%, 4px) scale(0.85); }
  100% { opacity: 1; transform: translate(-50%, 0) scale(1); }
}

.perm-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.perm-label {
  font-size: 0.75rem;
  color: var(--vp-c-text-3);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.desc {
  font-size: 0.92rem;
  color: var(--vp-c-text-1);
  line-height: 1.65;
  border-top: 1px dashed var(--vp-c-divider);
  padding-top: 10px;
}

.desc :deep(p:first-child) { margin-top: 0; }

.desc :deep(ul) {
  margin: 6px 0 0 0;
  padding-left: 16px;
}

.desc :deep(li) { margin: 2px 0; }

.desc :deep(code) {
  font-size: 0.85em;
  background-color: var(--vp-c-default-soft);
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
