<script setup>
import { ref } from 'vue'

defineProps({
  permission: { type: String, required: true },
  defaultVal: { type: String, default: 'op' }
})

const copiedItem = ref(null)

const showCopiedState = (text) => {
  copiedItem.value = text
  setTimeout(() => { if (copiedItem.value === text) copiedItem.value = null }, 2000)
}

const copyToClipboard = async (text) => {
  if (!text) return
  if (!navigator.clipboard) {
    const ta = document.createElement('textarea')
    ta.value = text
    Object.assign(ta.style, { position: 'fixed', opacity: '0' })
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); showCopiedState(text) } catch {}
    document.body.removeChild(ta)
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    showCopiedState(text)
  } catch { /* ignored */ }
}
</script>

<template>
  <div class="perm-row">
    <div class="col-perm">
      <span class="mobile-label">Permission:</span>
      <span class="badge perm-badge clickable" :class="{ 'is-copied': copiedItem === permission }" @click="copyToClipboard(permission)" title="Click to copy">{{ permission }}</span>
    </div>

    <div class="col-desc">
      <span class="mobile-label">Description:</span>
      <slot></slot>
    </div>

    <div class="col-default">
      <span class="mobile-label">Default:</span>
      <span class="default-badge" :class="defaultVal">{{ defaultVal }}</span>
    </div>
  </div>
</template>

<style scoped>
.perm-row {
  position: relative;
  display: grid;
  grid-template-columns: 2fr 3fr 0.6fr;
  padding: 16px 20px;
  border-bottom: 2px solid var(--vp-c-border);
  gap: 16px;
  align-items: start;
  transition: background-color 0.2s;
}

.perm-row:last-child { border-bottom: none; }
.perm-row:hover { background-color: var(--vp-c-bg-mute); }

.mobile-label { display: none; }

.col-desc {
  line-height: 1.6;
  font-size: 0.95rem;
  color: var(--vp-c-text-1);
  padding-top: 4px;
}

.col-default {
  display: flex;
  align-items: flex-start;
  padding-top: 4px;
}

.badge {
  position: relative; padding: 4px 10px; border-radius: 6px;
  font-family: var(--vp-font-family-mono); font-size: 0.85em; font-weight: 600;
  white-space: nowrap; transition: all 0.2s ease; display: inline-block;
}
.badge.clickable { cursor: pointer; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.badge.clickable:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
.badge.clickable:active { transform: translateY(0); }

.perm-badge { background-color: var(--vp-c-indigo-soft); color: var(--vp-c-indigo-1); }
.perm-badge.is-copied { background-color: var(--vp-c-indigo-1) !important; color: white !important; }

.badge.is-copied::after { content: 'Copied!'; position: absolute; bottom: 120%; left: 50%; transform: translateX(-50%); background-color: var(--vp-c-text-1); color: var(--vp-c-bg); padding: 4px 8px; border-radius: 4px; font-family: var(--vp-font-family-base); font-size: 0.75rem; font-weight: bold; pointer-events: none; animation: popIn 0.2s forwards; }
.badge.is-copied::before { content: ''; position: absolute; bottom: 100%; left: 50%; transform: translateX(-50%); border-width: 5px; border-style: solid; border-color: var(--vp-c-text-1) transparent transparent transparent; pointer-events: none; animation: popIn 0.2s forwards; }

@keyframes popIn { 0% { opacity: 0; transform: translate(-50%, 5px) scale(0.8); } 100% { opacity: 1; transform: translate(-50%, 0) scale(1); } }

.default-badge {
  padding: 3px 8px;
  border-radius: 6px;
  font-family: var(--vp-font-family-mono);
  font-size: 0.82em;
  font-weight: 700;
  white-space: nowrap;
}

.default-badge.op {
  background-color: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.default-badge.true {
  background-color: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

.default-badge.false {
  background-color: var(--vp-c-default-soft);
  color: var(--vp-c-text-3);
}

@media (max-width: 768px) {
  .perm-row { grid-template-columns: 1fr; gap: 12px; }
  .mobile-label { display: block; font-size: 0.8rem; text-transform: uppercase; font-weight: 700; color: var(--vp-c-text-3); margin-bottom: 6px; letter-spacing: 0.5px; }
  .col-desc, .col-default { padding-top: 0; }
}
</style>
