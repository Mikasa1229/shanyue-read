<template>
  <main class="page">
    <div class="container">

      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">书源管理</h1>
        <p class="page-desc">管理 legado 格式书源，书籍搜索请前往「发现」页面</p>
      </div>

      <div class="toolbar-row">
        <span class="count-badge">共 {{ sourceTotal }} 个书源</span>
        <button class="btn btn-gold btn-sm" @click="importModal.open = true">+ 导入书源</button>
      </div>

      <div v-if="sourcesLoading" class="spinner-wrap"><div class="spinner"></div></div>
      <div v-else-if="sources.length === 0" class="empty-state">
        <div class="empty-icon">📖</div>
        <p>暂无书源，点击「导入书源」开始</p>
      </div>
      <div v-else class="source-list">
        <div v-for="s in sources" :key="s.id" class="source-card" :class="{ disabled: !s.enabled }">
          <div class="source-info">
            <span class="source-name">{{ s.sourceName }}</span>
            <span class="source-url">{{ s.sourceUrl }}</span>
            <span v-if="testResults[s.id]" class="test-result" :class="testResults[s.id].accessible ? 'ok' : 'fail'">
              {{ testResults[s.id].accessible ? '✓' : '✗' }}
              {{ testResults[s.id].accessible ? `${testResults[s.id].responseMs}ms` : (testResults[s.id].error || `HTTP ${testResults[s.id].statusCode}`) }}
            </span>
          </div>
          <div class="source-actions">
            <span class="status-dot" :class="s.enabled ? 'on' : 'off'">
              {{ s.enabled ? '启用' : '禁用' }}
            </span>
            <button class="icon-btn" :title="testingId === s.id ? '测试中…' : '测试可访问性'" @click="doTestSource(s)" :disabled="testingId === s.id">
              {{ testingId === s.id ? '⏳' : '🔍' }}
            </button>
            <button class="icon-btn" :title="s.enabled ? '禁用' : '启用'" @click="toggleSource(s)">
              {{ s.enabled ? '🔕' : '🔔' }}
            </button>
            <button class="icon-btn danger" title="删除" @click="deleteSource(s)">🗑</button>
          </div>
        </div>
      </div>

      <div v-if="sourceTotalPages > 1" class="pagination mt-6">
        <button
          v-for="p in sourceTotalPages"
          :key="p"
          class="page-btn"
          :class="{ active: p === sourcePage }"
          @click="loadSources(p)"
        >{{ p }}</button>
      </div>

    </div>
  </main>

  <!-- 导入书源弹窗 -->
  <Teleport to="body">
    <div v-if="importModal.open" class="modal-overlay" @click.self="importModal.open = false">
      <div class="modal">
        <div class="modal-header">
          <h3>导入书源</h3>
          <button class="modal-close" @click="importModal.open = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="import-tabs">
            <button class="import-tab" :class="{ active: importModal.mode === 'url' }" @click="importModal.mode = 'url'">远端 URL</button>
            <button class="import-tab" :class="{ active: importModal.mode === 'json' }" @click="importModal.mode = 'json'">粘贴 JSON</button>
          </div>
          <div v-if="importModal.mode === 'url'">
            <label class="form-label">书源文件 URL</label>
            <input v-model="importModal.url" class="form-input" placeholder="https://example.com/sources.json" />
          </div>
          <div v-else>
            <label class="form-label">JSON 内容（粘贴书源数组）</label>
            <textarea v-model="importModal.json" class="form-textarea" rows="8" placeholder='[{"bookSourceName":"...","bookSourceUrl":"..."}]'></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="importModal.open = false">取消</button>
          <button class="btn btn-gold btn-sm" :disabled="importModal.loading" @click="doImport">
            {{ importModal.loading ? '导入中…' : '确认导入' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useToast } from '@/composables/useToast'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { apiListSources, apiToggleSource, apiDeleteSource, apiImportByUrl, apiImportByJson, apiTestSource } from '@/api/bookSource'

const { show } = useToast()
const { confirm } = useConfirmDialog()

const sources = ref([])
const sourcesLoading = ref(false)
const sourcePage = ref(1)
const sourceTotal = ref(0)
const sourceTotalPages = ref(1)

async function loadSources(page = 1) {
  sourcesLoading.value = true
  sourcePage.value = page
  try {
    const res = await apiListSources(page, 20)
    sources.value = res?.records ?? []
    sourceTotal.value = res?.total ?? 0
    sourceTotalPages.value = res?.pages ?? 1
  } catch (e) {
    show(e.message)
  } finally {
    sourcesLoading.value = false
  }
}

async function toggleSource(s) {
  try {
    await apiToggleSource(s.id)
    s.enabled = !s.enabled
  } catch (e) {
    show(e.message)
  }
}

const testResults = ref({})
const testingId = ref(null)

async function doTestSource(s) {
  testingId.value = s.id
  try {
    const res = await apiTestSource(s.id)
    testResults.value = { ...testResults.value, [s.id]: res }
  } catch (e) {
    testResults.value = { ...testResults.value, [s.id]: { accessible: false, statusCode: 0, responseMs: 0, error: e.message } }
  } finally {
    testingId.value = null
  }
}

async function deleteSource(s) {
  if (!await confirm({
    title: '删除书源',
    message: `确定删除书源「${s.sourceName}」吗？该书源下的作品将无法继续检索。`,
    confirmText: '删除书源',
    tone: 'danger'
  })) return
  try {
    await apiDeleteSource(s.id)
    await loadSources(sourcePage.value)
    show('已删除')
  } catch (e) {
    show(e.message)
  }
}

const importModal = ref({ open: false, mode: 'url', url: '', json: '', loading: false })

async function doImport() {
  importModal.value.loading = true
  try {
    let res
    if (importModal.value.mode === 'url') {
      if (!importModal.value.url.trim()) { show('请输入 URL'); return }
      res = await apiImportByUrl(importModal.value.url.trim())
    } else {
      if (!importModal.value.json.trim()) { show('请粘贴 JSON'); return }
      res = await apiImportByJson(importModal.value.json.trim())
    }
    show(`成功导入 ${res?.imported ?? 0} 个书源`)
    importModal.value.open = false
    importModal.value.url = ''
    importModal.value.json = ''
    await loadSources(1)
  } catch (e) {
    show(e.message)
  } finally {
    importModal.value.loading = false
  }
}

onMounted(() => loadSources(1))
</script>

<style scoped>
.page-header { padding: var(--space-8) 0 var(--space-4); }
.page-title  { font-family: var(--font-serif); font-size: 1.75rem; color: var(--ink-0); margin-bottom: var(--space-2); }
.page-desc   { font-size: 0.9rem; color: var(--ink-3); }

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}
.count-badge { font-size: 0.875rem; color: var(--ink-3); }

.source-list { display: flex; flex-direction: column; gap: var(--space-3); }
.source-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  transition: opacity var(--transition-fast);
}
.source-card.disabled { opacity: 0.5; }
.source-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.source-name { font-weight: 600; font-size: 0.9375rem; color: var(--ink-0); }
.source-url  { font-size: 0.8125rem; color: var(--ink-4); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 400px; }
.source-actions { display: flex; align-items: center; gap: var(--space-3); flex-shrink: 0; }
.test-result { font-size: 0.75rem; padding: 2px 6px; border-radius: var(--radius-full); white-space: nowrap; }
.test-result.ok   { background: #d1fae5; color: #065f46; }
.test-result.fail { background: #fee2e2; color: #991b1b; }
.status-dot { font-size: 0.75rem; padding: 2px 8px; border-radius: var(--radius-full); }
.status-dot.on  { background: #d1fae5; color: #065f46; }
.status-dot.off { background: var(--paper-2); color: var(--ink-4); }
.icon-btn { border: none; background: transparent; font-size: 1rem; cursor: pointer; padding: 4px; border-radius: var(--radius-sm); transition: background var(--transition-fast); }
.icon-btn:hover { background: var(--paper-2); }
.icon-btn.danger:hover { background: #fee2e2; }

.spinner-wrap { display: flex; justify-content: center; padding: var(--space-12) 0; }
.spinner { width: 32px; height: 32px; border: 3px solid var(--paper-3); border-top-color: var(--gold-0); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state { text-align: center; padding: var(--space-16) 0; color: var(--ink-4); }
.empty-icon  { font-size: 3rem; margin-bottom: var(--space-4); }

.pagination { display: flex; justify-content: center; gap: var(--space-2); }
.mt-6 { margin-top: var(--space-6); }
.page-btn { width: 36px; height: 36px; border: 1.5px solid var(--paper-3); border-radius: var(--radius-md); background: transparent; font-size: 0.875rem; color: var(--ink-3); cursor: pointer; transition: all var(--transition-fast); }
.page-btn:hover { background: var(--paper-2); }
.page-btn.active { background: var(--ink-0); border-color: var(--ink-0); color: var(--paper-0); }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 300; display: flex; align-items: center; justify-content: center; padding: var(--space-4); }
.modal { background: var(--paper-0); border-radius: var(--radius-xl); width: 100%; max-width: 520px; box-shadow: var(--shadow-lg); }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: var(--space-5) var(--space-6); border-bottom: 1px solid var(--paper-3); }
.modal-header h3 { font-family: var(--font-serif); font-size: 1.125rem; color: var(--ink-0); }
.modal-close { border: none; background: transparent; font-size: 1rem; cursor: pointer; color: var(--ink-3); }
.modal-body  { padding: var(--space-5) var(--space-6); }
.modal-footer { display: flex; justify-content: flex-end; gap: var(--space-3); padding: var(--space-4) var(--space-6); border-top: 1px solid var(--paper-3); }

.import-tabs { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.import-tab { padding: var(--space-2) var(--space-4); border: 1.5px solid var(--paper-3); border-radius: var(--radius-full); background: transparent; font-size: 0.875rem; color: var(--ink-3); cursor: pointer; transition: all var(--transition-fast); }
.import-tab.active { background: var(--ink-0); border-color: var(--ink-0); color: var(--paper-0); }

.form-label { display: block; font-size: 0.875rem; color: var(--ink-2); margin-bottom: var(--space-2); }
.form-input  { width: 100%; padding: var(--space-3) var(--space-4); border: 1.5px solid var(--paper-3); border-radius: var(--radius-md); font-size: 0.875rem; color: var(--ink-1); background: var(--paper-0); outline: none; box-sizing: border-box; transition: border-color var(--transition-fast); }
.form-input:focus { border-color: var(--gold-1); }
.form-textarea { width: 100%; padding: var(--space-3) var(--space-4); border: 1.5px solid var(--paper-3); border-radius: var(--radius-md); font-size: 0.8125rem; color: var(--ink-1); background: var(--paper-0); outline: none; resize: vertical; box-sizing: border-box; font-family: 'Courier New', monospace; transition: border-color var(--transition-fast); }
.form-textarea:focus { border-color: var(--gold-1); }

@media (max-width: 600px) { .source-url { max-width: 180px; } }
</style>
