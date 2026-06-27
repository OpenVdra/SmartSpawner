<script setup>
import { ref } from 'vue'

defineProps({
  commands: { type: [String, Array], required: true },
  aliases: { type: [String, Array], default: () => [] },
  permission: { type: String, required: true },
  grouped: { type: Boolean, default: false }
})

const toArray = (val) => Array.isArray(val) ? val : [val]
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
  } catch {
    /* ignored */
  }
}
</script>

<template>
  <div class="perm-row" :class="{ 'is-grouped': grouped }">
    <div class="col-cmd">
      <span class="mobile-label">Command & Aliases:</span>
      <div class="badge-container">
        <span v-for="cmd in toArray(commands)" :key="cmd" class="badge cmd-badge clickable" :class="{ 'is-copied': copiedItem === cmd }" @click="copyToClipboard(cmd)" title="Click to copy">{{ cmd }}</span>
        <span v-for="alias in toArray(aliases)" :key="alias" class="badge alias-badge clickable" :class="{ 'is-copied': copiedItem === alias }" @click="copyToClipboard(alias)" title="Click to copy">{{ alias }}</span>
      </div>
    </div>

    <div class="col-perm">
      <span class="mobile-label">Permission:</span>
      <span class="badge perm-badge clickable" :class="{ 'is-copied': copiedItem === permission }" @click="copyToClipboard(permission)" title="Click to copy">{{ permission }}</span>
    </div>
  </div>
</template>

<style scoped>
.perm-row {
  position: relative;
  display: grid;
  grid-template-columns: 1.5fr 2fr;
  padding: 18px 20px;
  border-bottom: 2px solid var(--vp-c-border);
  gap: 16px;
  align-items: center;
  transition: background-color 0.2s;
}

.perm-row:last-child { border-bottom: none; }
.perm-row:hover { background-color: var(--vp-c-bg-mute); }

.perm-row.is-grouped { border-bottom: none; padding-bottom: 20px; }
.perm-row.is-grouped::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10%;
  width: 80%;
  height: 1px;
  background-color: var(--vp-c-border);
  opacity: 0.6;
}
.perm-row.is-grouped + .perm-row { padding-top: 20px; }

.mobile-label { display: none; }
.badge-container { display: flex; flex-wrap: wrap; gap: 8px; }

.badge {
  position: relative; padding: 4px 10px; border-radius: 6px;
  font-family: var(--vp-font-family-mono); font-size: 0.9em; font-weight: 600;
  white-space: nowrap; transition: all 0.2s ease;
}
.badge.clickable { cursor: pointer; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.badge.clickable:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
.badge.clickable:active { transform: translateY(0); }

.cmd-badge { background-color: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); }
.alias-badge { background-color: var(--vp-c-default-soft); color: var(--vp-c-text-2); }
.perm-badge { background-color: var(--vp-c-indigo-soft); color: var(--vp-c-indigo-1); }

.cmd-badge.is-copied { background-color: #4caf50 !important; color: white !important; }
.alias-badge.is-copied { background-color: var(--vp-c-text-2) !important; color: var(--vp-c-bg) !important; }
.perm-badge.is-copied { background-color: var(--vp-c-indigo-1) !important; color: white !important; transform: scale(1.05); }

.badge.is-copied::after { content: 'Copied!'; position: absolute; bottom: 120%; left: 50%; transform: translateX(-50%); background-color: var(--vp-c-text-1); color: var(--vp-c-bg); padding: 4px 8px; border-radius: 4px; font-family: var(--vp-font-family-base); font-size: 0.75rem; font-weight: bold; pointer-events: none; animation: popIn 0.2s forwards; }
.badge.is-copied::before { content: ''; position: absolute; bottom: 100%; left: 50%; transform: translateX(-50%); border-width: 5px; border-style: solid; border-color: var(--vp-c-text-1) transparent transparent transparent; pointer-events: none; animation: popIn 0.2s forwards; }

@keyframes popIn { 0% { opacity: 0; transform: translate(-50%, 5px) scale(0.8); } 100% { opacity: 1; transform: translate(-50%, 0) scale(1); } }

@media (max-width: 768px) {
  .perm-row { grid-template-columns: 1fr; gap: 12px; }
  .mobile-label { display: block; font-size: 0.8rem; text-transform: uppercase; font-weight: 700; color: var(--vp-c-text-3); margin-bottom: 6px; letter-spacing: 0.5px; }
}
</style>
