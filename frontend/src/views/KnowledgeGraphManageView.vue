<template>
  <main class="graph-manage page">
    <div class="graph-shell">
      <header class="graph-head">
        <div><span>LIGHTRAG 知识资产</span><h1>知识图谱管理</h1><p>在独立页面查看自己书架中每部作品的构建范围、共享状态和维护操作。</p></div>
        <router-link to="/agent?tab=insights">返回书籍洞察</router-link>
      </header>
      <section v-if="loading" class="graph-empty">正在读取你的知识图谱……</section>
      <section v-else-if="error" class="graph-empty error">{{ error }}</section>
      <section v-else-if="!items.length" class="graph-empty"><h2>书架里还没有可管理的作品</h2><p>先把作品加入书架，再从书籍洞察中创建知识图谱。</p></section>
      <section v-else class="graph-list">
        <article v-for="item in items" :key="item.book.canonicalBookId" class="graph-row">
          <img v-if="item.book.coverUrl" :src="item.book.coverUrl" :alt="item.book.bookName" />
          <div class="graph-book"><span>{{ statusLabel(item.status?.status) }}</span><h2>《{{ item.book.bookName }}》</h2><p>{{ item.book.author || '作者信息待补充' }} · 已读至第 {{ Number(item.book.lastChapterIndex || 0) + 1 }} 章</p></div>
          <div class="graph-range"><small>知识覆盖</small><strong>{{ coverageLabel(item.status) }}</strong><i><b :style="{ width: `${coveragePercent(item.status)}%` }" /></i></div>
          <div class="graph-sharing"><small>可见范围</small><strong>{{ sharingLabel(item.status) }}</strong></div>
          <div class="graph-actions">
            <button type="button" @click="openInsight(item.book)">查看</button>
            <button v-if="canManageSharing(item.status)" type="button" @click="toggleSharing(item)">{{ item.status.isPublic ? '转为私有' : '公开共享' }}</button>
            <button v-if="canDeleteGraph(item.status)" type="button" class="danger" @click="removeGraph(item)">删除图谱</button>
          </div>
        </article>
      </section>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { apiGetMyShelf } from '@/api/bookshelf'
import { apiDeleteOwnedBookKnowledge, apiPrepareBookKnowledgeBuild, apiUpdateBookKnowledgeSharing } from '@/api/agent'

const router = useRouter()
const toast = useToast()
const loading = ref(true)
const error = ref('')
const items = ref([])

function statusLabel(status) { return ({ READY: '已构建', RUNNING: '构建中', QUEUED: '等待构建', FAILED: '构建失败' })[status] || '尚未构建' }
function sharingLabel(status) {
  if (!status || status.status === 'NOT_BUILT') return '未创建'
  if (status.status === 'QUEUED' || status.status === 'RUNNING') return '构建中不可共享'
  if (status.status === 'FAILED') return '构建失败'
  return status.isPublic ? '公开共享' : status.isOwner ? '仅自己可见' : '公共图谱'
}
function canManageSharing(status) { return status?.isOwner && status.status === 'READY' }
function canDeleteGraph(status) { return status?.isOwner && ['READY', 'FAILED'].includes(status.status) }
function coverageLabel(status) {
  if (!status?.coveredChapters) return '暂无覆盖'
  return `第 ${Number(status.startChapter || 1)} 至 ${Number(status.endChapter || status.coveredChapters)} 章`
}
function coveragePercent(status) {
  const covered = Number(status?.coveredChapters || 0)
  const target = Math.max(covered, Number(status?.endChapter || 0), 1)
  return Math.min(100, Math.round(covered / target * 100))
}
function openInsight(book) { router.push({ path: '/agent', query: { tab: 'insights', bookId: book.canonicalBookId } }) }
async function toggleSharing(item) {
  const next = !item.status.isPublic
  try { await apiUpdateBookKnowledgeSharing(item.book.canonicalBookId, next); item.status.isPublic = next; toast.success(next ? '图谱已公开共享' : '图谱已设为仅自己可见') }
  catch (e) { toast.error(e.message || '共享设置更新失败') }
}
async function removeGraph(item) {
  if (!window.confirm(`确定删除《${item.book.bookName}》的全部知识资产吗？这会清除已构建的文档、向量、实体、关系、线索和事件数据。`)) return
  try { await apiDeleteOwnedBookKnowledge(item.book.canonicalBookId); item.status = { ...item.status, status: 'NOT_BUILT', isPublic: false, coveredChapters: 0 }; toast.success('该作品的全部知识资产已删除') }
  catch (e) { toast.error(e.message || '知识图谱删除失败') }
}
onMounted(async () => {
  try {
    const shelf = await apiGetMyShelf(1, 100)
    const books = (shelf?.records || []).filter(book => book.canonicalBookId)
    const statuses = await Promise.allSettled(books.map(book => apiPrepareBookKnowledgeBuild(
      book.canonicalBookId,
      { startChapter: 1, endChapter: Math.max(1, Number(book.lastChapterIndex || 0) + 1) }
    )))
    items.value = books.map((book, index) => ({
      book,
      status: statuses[index]?.status === 'fulfilled' ? statuses[index].value : {}
    }))
  } catch (e) { error.value = e.message || '知识图谱列表加载失败' }
  finally { loading.value = false }
})
</script>

<style scoped>
.graph-manage{min-height:calc(100vh - 64px);background:#f7f3ea;color:#253332}.graph-shell{max-width:1220px;margin:0 auto;padding:42px 28px 70px}.graph-head{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;padding-bottom:26px;border-bottom:1px solid #d9d2c5}.graph-head span{color:#a85d40;font-size:.7rem;font-weight:800;letter-spacing:.15em}.graph-head h1{margin:8px 0;color:#243331;font-family:var(--font-serif);font-size:clamp(2.4rem,5vw,4.5rem);letter-spacing:-.06em}.graph-head p{margin:0;color:#66716f}.graph-head a{border:1px solid #cfc6b7;border-radius:10px;padding:10px 14px;color:#42504e;background:#fffdf8;text-decoration:none}.graph-list{display:grid;margin-top:22px;border-top:1px solid #ddd5c8}.graph-row{display:grid;grid-template-columns:62px minmax(210px,1.25fr) minmax(180px,.8fr) minmax(120px,.55fr) auto;gap:20px;align-items:center;padding:20px 8px;border-bottom:1px solid #ddd5c8}.graph-row>img{width:58px;height:78px;border-radius:8px;object-fit:cover;background:#ebe5da}.graph-book span{color:#a85d40;font-size:.65rem;font-weight:800}.graph-book h2{margin:5px 0;color:#243331;font-family:var(--font-serif);font-size:1.25rem}.graph-book p,.graph-range small,.graph-sharing small{margin:0;color:#73807d;font-size:.7rem}.graph-range,.graph-sharing{display:grid;gap:7px}.graph-range strong,.graph-sharing strong{color:#344441;font-size:.85rem}.graph-range i{height:5px;overflow:hidden;border-radius:99px;background:#e3ddd2}.graph-range b{display:block;height:100%;background:#bd795c}.graph-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:6px}.graph-actions button{border:1px solid #cfc6b7;border-radius:8px;padding:7px 9px;color:#42504e;background:#fffdf8;cursor:pointer}.graph-actions .danger{color:#a44735}.graph-empty{margin-top:28px;border:1px solid #ddd5c8;border-radius:14px;padding:42px;color:#66716f;background:#fffdf8;text-align:center}.graph-empty.error{color:#9f3d31}@media(max-width:850px){.graph-row{grid-template-columns:52px 1fr}.graph-range,.graph-sharing,.graph-actions{grid-column:2}.graph-actions{justify-content:flex-start}.graph-head{align-items:flex-start;flex-direction:column}}@media(max-width:560px){.graph-shell{padding:26px 16px 50px}.graph-row{gap:12px;padding:18px 0}.graph-head h1{font-size:2.6rem}}
</style>
