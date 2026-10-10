<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import http from '@/api/http'

interface Frame {
  id: string
  nodeName: string
  status: string
  startMs: number
  durationMs: number
  parentId?: string | null
  detail?: string
  inputDetail?: string
  outputDetail?: string
}
interface Task {
  taskId: string
  question: string
  role: string
  startTime: string
  endTime: string
  totalMs: number
  nodeCount: number
  status: string
  frames?: Frame[]
}

const tasks = ref<Task[]>([])
const current = ref<Task | null>(null)
const loading = ref(false)
const autoRefresh = ref(true)
const searchKeyword = ref('')
const statusFilter = ref('')
const selectedFrame = ref<Frame | null>(null)
let timer: any

const statusMap: Record<string, { label: string; type: string }> = {
  SUCCESS: { label: '完成', type: 'success' },
  COMPLETED: { label: '完成', type: 'success' },
  '完成': { label: '完成', type: 'success' },
  '成功': { label: '完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  ERROR: { label: '失败', type: 'danger' },
  RUNNING: { label: '运行中', type: 'warning' },
  '运行中': { label: '运行中', type: 'warning' },
  INTERRUPTED: { label: '待确认', type: 'primary' },
  '等待人工输入': { label: '待确认', type: 'primary' },
  '失败': { label: '失败', type: 'danger' },
  '出错': { label: '失败', type: 'danger' },
}
function statusInfo(s: string) {
  return statusMap[s] || { label: s || '未知', type: 'info' }
}

const filteredTasks = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  return tasks.value.filter((t) => {
    if (statusFilter.value && statusInfo(t.status).label !== statusFilter.value) return false
    if (kw && (t.question + '\n' + (t.role || '') + '\n' + t.taskId).toLowerCase().includes(kw) === false) return false
    return true
  })
})

const stats = computed(() => {
  const all = tasks.value
  const done = all.filter((t) => statusInfo(t.status).type === 'success')
  const running = all.filter((t) => statusInfo(t.status).type === 'warning')
  const avg = done.length ? Math.round(done.reduce((s, t) => s + (t.totalMs || 0), 0) / done.length) : 0
  return { total: all.length, done: done.length, running: running.length, avg }
})

async function loadTasks() {
  try {
    const res: any = await http.get('/api/trace/tasks')
    tasks.value = (res || []).slice().sort((a: Task, b: Task) => (b.startTime || '').localeCompare(a.startTime || ''))
  } catch (e) { console.error(e) }
}
async function loadDetail(taskId: string) {
  try {
    const res: any = await http.get('/api/trace/tasks/' + taskId)
    current.value = res
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}
function onSelect(row: Task) {
  if (current.value && current.value.taskId === row.taskId) return
  loading.value = true
  selectedFrame.value = null
  loadDetail(row.taskId)
}
function refreshAll() {
  loadTasks()
  if (current.value && current.value.taskId) loadDetail(current.value.taskId)
}
onMounted(() => {
  loadTasks()
  timer = setInterval(() => { if (autoRefresh.value) refreshAll() }, 5000)
})
onBeforeUnmount(() => clearInterval(timer))

function fmtDuration(ms: number | undefined): string {
  if (ms == null || Number.isNaN(ms)) return '-'
  if (ms < 1000) return Math.round(ms) + 'ms'
  const s = ms / 1000
  if (s < 60) return s.toFixed(1) + 's'
  const m = Math.floor(s / 60)
  return m + 'm ' + Math.round(s % 60) + 's'
}
function fmtTime(iso?: any): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}
function frameType(s: string) {
  if (s === 'success' || s === 'SUCCESS' || s === '成功') return 'success'
  if (s === 'failed' || s === 'FAILED' || s === 'ERROR' || s === 'error' || s === '失败') return 'danger'
  if (s === 'running' || s === 'RUNNING' || s === '运行中') return 'warning'
  return 'info'
}
function frameLabel(s: string) {
  if (s === 'success' || s === 'SUCCESS' || s === '成功') return '成功'
  if (s === 'failed' || s === 'FAILED' || s === 'ERROR' || s === 'error' || s === '失败') return '失败'
  if (s === 'running' || s === 'RUNNING' || s === '运行中') return '执行中'
  return s || '未知'
}

const NODE_LABELS: Record<string, string> = {
  EVIDENCE_RECALL_NODE: '证据召回',
  SCHEME_RECALL_NODE: 'Schema 召回',
  TABLE_RELATION_NODE: '表关系',
  FEASIBILITY_ASSESSMENT_NODE: '可行性评估',
  PLANNER_NODE: '规划器',
  HUMAN_FEEDBACK_NODE: '人工确认',
  PLAN_EXECUTE_NODE: '计划执行',
  SQL_GENERATE_NODE: 'SQL 生成',
  SQL_EXECUTE_NODE: 'SQL 执行',
  PYTHON_GENERATE_NODE: 'Python 生成',
  PYTHON_EXECUTE_NODE: 'Python 执行',
  PYTHON_ANALYZE_NODE: 'Python 分析',
  REPORT_GENERATOR_NODE: '报告生成',
}
function labelFor(f: Frame, frames: Frame[]) {
  const base = NODE_LABELS[f.nodeName] || f.nodeName
  const same = frames.filter((x) => x.nodeName === f.nodeName)
  if (same.length <= 1) return base
  const idx = same.findIndex((x) => x.id === f.id) + 1
  return base + ' #' + idx
}
const selLabel = computed(() => {
  const f = selectedFrame.value
  if (!f || !current.value?.frames) return ''
  return labelFor(f, current.value.frames)
})

const NODE_COLORS: Record<string, string> = {}
const PALETTE = ['#2563eb', '#1d4ed8', '#1e40af', '#3b82f6', '#60a5fa', '#475569', '#64748b', '#94a3b8', '#0f172a']
let curColor = 0
function colorFor(name: string) {
  if (!NODE_COLORS[name]) { NODE_COLORS[name] = PALETTE[curColor % PALETTE.length] || '#64748b'; curColor++ }
  return NODE_COLORS[name]
}
interface Bar { id: string; nodeName: string; status: string; startMs: number; durationMs: number; depth: number; left: number; width: number; color: string; label: string }

const flame = computed<{ levels: Bar[][]; height: number }>(() => {
  const frames = current.value?.frames || []
  if (!frames.length) return { levels: [], height: 0 }
  const depthMap = new Map<string, number>()
  const dep = (f: Frame): number => {
    if (depthMap.has(f.id)) return depthMap.get(f.id)!
    const pa = f.parentId ? frames.find((x) => x.id === f.parentId) : undefined
    const d = pa ? dep(pa) + 1 : 0
    depthMap.set(f.id, d)
    return d
  }
  const total = Math.max(current.value?.totalMs || 1, 1)
  const start = Math.min(...frames.map((f) => f.startMs))
  const bars: Bar[] = frames.map((f) => ({
    ...f,
    depth: dep(f),
    left: ((f.startMs - start) / total) * 100,
    width: Math.max((f.durationMs / total) * 100, 0.3),
    color: colorFor(f.nodeName),
    label: labelFor(f, frames),
  }))
  const maxDepth = Math.max(...bars.map((b) => b.depth))
  const levels: Bar[][] = Array.from({ length: maxDepth + 1 }, () => [])
  bars.forEach((b) => levels[b.depth]?.push(b))
  return { levels, height: levels.length * 30 + 4 }
})
const flameView = reactive({ zoom: 1, panFrac: 0 })
const flameBody = ref<HTMLElement>()
let dragging = false
let dragStartX = 0
let dragStartStart = 0
function clampNum(v: number, lo: number, hi: number) { return Math.min(hi, Math.max(lo, v)) }
function flameWindowStart() {
  const winWidth = 100 / flameView.zoom
  return (100 - winWidth) * flameView.panFrac
}
function barStyle(b: Bar) {
  const z = flameView.zoom
  const ws = flameWindowStart()
  return { left: (b.left - ws) * z + '%', width: b.width * z + '%', background: b.color }
}
function onFlameWheel(e: WheelEvent) {
  const el = flameBody.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const mxFrac = clampNum((e.clientX - rect.left) / rect.width, 0, 1)
  const oldZ = flameView.zoom
  const newZ = clampNum(oldZ * (e.deltaY < 0 ? 1.25 : 0.8), 1, 40)
  const abs = flameWindowStart() + mxFrac * (100 / oldZ)
  const newWin = 100 / newZ
  let newStart = abs - mxFrac * newWin
  newStart = clampNum(newStart, 0, Math.max(0, 100 - newWin))
  flameView.zoom = newZ
  flameView.panFrac = (100 - newWin) <= 0 ? 0 : newStart / (100 - newWin)
  e.preventDefault()
}
function onFlameDown(e: PointerEvent) {
  dragging = true
  dragStartX = e.clientX
  dragStartStart = flameWindowStart()
}
function onFlameMove(e: PointerEvent) {
  if (!dragging || !flameBody.value) return
  const rect = flameBody.value.getBoundingClientRect()
  const winWidth = 100 / flameView.zoom
  const dxAbs = ((e.clientX - dragStartX) / rect.width) * winWidth
  let newStart = dragStartStart - dxAbs
  newStart = clampNum(newStart, 0, Math.max(0, 100 - winWidth))
  flameView.panFrac = (100 - winWidth) <= 0 ? 0 : newStart / (100 - winWidth)
}
function onFlameUp() { dragging = false }
function zoomBy(factor: number) {
  const oldZ = flameView.zoom
  const newZ = clampNum(oldZ * factor, 1, 40)
  const abs = flameWindowStart() + 0.5 * (100 / oldZ)
  const newWin = 100 / newZ
  let newStart = abs - 0.5 * newWin
  newStart = clampNum(newStart, 0, Math.max(0, 100 - newWin))
  flameView.zoom = newZ
  flameView.panFrac = (100 - newWin) <= 0 ? 0 : newStart / (100 - newWin)
}
function resetFlame() { flameView.zoom = 1; flameView.panFrac = 0 }
const flameZoomPct = computed(() => Math.round(flameView.zoom * 100) + '%')

function childrenOf(f: Frame): Frame[] {
  if (!current.value?.frames) return []
  return current.value.frames.filter((x) => x.parentId === f.id)
}
function selectFrame(f: Frame | null) {
  selectedFrame.value = f && selectedFrame.value && selectedFrame.value.id === f.id ? null : f
}

interface TreeNode extends Frame { label: string; children: TreeNode[] }
const treeData = computed<TreeNode[]>(() => {
  const frames = current.value?.frames || []
  const map = new Map<string, TreeNode>()
  frames.forEach((f) => map.set(f.id, { ...f, label: labelFor(f, frames), children: [] }))
  const roots: TreeNode[] = []
  frames.forEach((f) => {
    const node = map.get(f.id)!
    const pa = f.parentId ? map.get(f.parentId) : undefined
    if (pa) pa.children.push(node); else roots.push(node)
  })
  return roots
})
const maxDur = computed(() => Math.max(1, ...(current.value?.frames || []).map((f) => f.durationMs || 0)))
const showTree = ref(true)
const expandAll = ref(true)

const topSlow = computed(() => {
  const frames = current.value?.frames || []
  return frames.slice().sort((a, b) => (b.durationMs || 0) - (a.durationMs || 0)).slice(0, 8)
    .map((f) => ({ ...f, label: labelFor(f, frames) }))
})
const cs = computed(() => {
  const f = current.value?.frames || []
  if (!f.length) return null
  const sum = f.reduce((s, x) => s + (x.durationMs || 0), 0)
  const max = Math.max(...f.map((x) => x.durationMs || 0))
  const failed = f.filter((x) => frameType(x.status) === 'danger').length
  return { totalMs: current.value?.totalMs || sum, nodes: current.value?.nodeCount || f.length, sum, max, failed }
})

function clickBar(b: Bar) {
  const f = current.value?.frames?.find((x) => x.id === b.id) || null
  selectFrame(f)
}
function rowClass({ row }: { row: Task }) {
  return current.value && current.value.taskId === row.taskId ? 'current-row' : ''
}
function treeRowClass({ row }: { row: Frame }) {
  return selectedFrame.value && selectedFrame.value.id === row.id ? 'is-current' : ''
}
</script>

<template>
  <div class="debug-page">
    <el-aside width="430px" class="task-aside">
      <el-card shadow="never" class="aside-card">
        <template #header>
          <div class="aside-head">
            <span class="aside-title">调试任务</span>
            <div class="aside-actions">
              <el-switch v-model="autoRefresh" active-text="自动刷新" size="small" />
              <el-tooltip content="立即刷新" placement="top"><el-button circle size="small" @click="refreshAll"><el-icon><Refresh /></el-icon></el-button></el-tooltip>
            </div>
          </div>
        </template>
        <div class="stat-grid">
          <div class="stat-cell"><b>{{ stats.total }}</b><span>总任务</span></div>
          <div class="stat-cell good"><b>{{ stats.done }}</b><span>已完成</span></div>
          <div class="stat-cell warn"><b>{{ stats.running }}</b><span>运行中</span></div>
          <div class="stat-cell"><b>{{ fmtDuration(stats.avg) }}</b><span>平均耗时</span></div>
        </div>
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索问题 / 角色 / ID" clearable size="small" prefix-icon="Search" />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width:110px;flex:none">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="v.label" />
          </el-select>
        </div>
        <el-table :data="filteredTasks" highlight-current-row class="task-table" @row-click="onSelect"
                  :row-class-name="rowClass">
          <el-table-column label="耗时" width="76" align="right">
            <template #default="{ row }"><span class="dur">{{ fmtDuration(row.totalMs) }}</span></template>
          </el-table-column>
          <el-table-column prop="question" label="问题" show-overflow-tooltip />
          <el-table-column label="状态" width="74" align="center">
            <template #default="{ row }"><el-tag :type="statusInfo(row.status).type" size="small" effect="light">{{ statusInfo(row.status).label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="时间" width="106">
            <template #default="{ row }"><span class="time">{{ fmtTime(row.startTime) }}</span></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!filteredTasks.length" description="暂无任务" :image-size="60" />
      </el-card>
    </el-aside>

    <div class="detail-wrap">
      <el-card shadow="never" v-loading="loading" class="detail-card">
        <template #header>
          <div class="detail-head">
            <span class="detail-title">调试详情</span>
            <template v-if="current">
              <div class="detail-meta">
                <el-tag :type="statusInfo(current.status).type" effect="dark" size="small">{{ statusInfo(current.status).label }}</el-tag>
                <span class="meta-role">{{ current.role }}</span>
              </div>
            </template>
          </div>
        </template>

        <template v-if="current">
          <div class="q-bar">
            <el-icon color="#2563eb"><ChatDotRound /></el-icon>
            <span class="q-text">{{ current.question }}</span>
            <span class="q-time">{{ fmtTime(current.startTime) }} ~ {{ fmtTime(current.endTime) }}</span>
          </div>

          <div v-if="cs" class="stat-grid detail-stats">
            <div class="stat-cell"><b>{{ fmtDuration(cs.totalMs) }}</b><span>总耗时</span></div>
            <div class="stat-cell"><b>{{ cs.nodes }}</b><span>节点数</span></div>
            <div class="stat-cell"><b>{{ fmtDuration(cs.max) }}</b><span>最长节点</span></div>
            <div class="stat-cell" :class="{ bad: cs.failed > 0 }"><b>{{ cs.failed }}</b><span>失败节点</span></div>
          </div>

          <div class="section">
            <div class="section-head">
              <span class="section-title">火焰图</span>
              <span class="section-hint">按节点层级与耗时占比展示 · 点击节点查看输入输出 · 滚轮缩放 · 拖拽平移</span>
            </div>
            <div class="flame-toolbar">
              <div class="flame-zoom">
                <el-tooltip content="放大"><el-button circle size="small" @click="zoomBy(1.5)"><el-icon><ZoomIn /></el-icon></el-button></el-tooltip>
                <el-tooltip content="缩小"><el-button circle size="small" @click="zoomBy(0.8)"><el-icon><ZoomOut /></el-icon></el-button></el-tooltip>
                <el-button size="small" @click="resetFlame">复位</el-button>
                <span class="zoom-pct">{{ flameZoomPct }}</span>
              </div>
              <span class="section-hint">滚轮缩放 · 拖拽平移</span>
            </div>
            <div class="flame-wrap" :class="{ dragging }" v-if="flame.levels.length" :style="{ height: flame.height + 'px', maxHeight: '62vh' }"
                 ref="flameBody"
                 @wheel="onFlameWheel"
                 @pointerdown="onFlameDown" @pointermove="onFlameMove" @pointerup="onFlameUp" @pointercancel="onFlameUp">
              <div class="level" v-for="(row, li) in flame.levels" :key="li" :style="{ top: li * 30 + 'px' }">
                <div v-for="b in row" :key="b.id" class="flame__bar"
                     :class="{ active: selectedFrame && selectedFrame.id === b.id }"
                     :style="barStyle(b)"
                     @click="clickBar(b)"
                     :title="b.label + ' · ' + fmtDuration(b.durationMs) + ' · ' + frameLabel(b.status)">
                  <span v-if="b.width * flameView.zoom > 5">{{ b.label }} {{ fmtDuration(b.durationMs) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无可绘制的火焰图数据" :image-size="60" />
          </div>

          <div v-if="selectedFrame" class="node-panel">
            <div class="node-panel-head">
              <span class="node-name">{{ selLabel }}</span>
              <el-tag :type="frameType(selectedFrame.status)" size="small">{{ frameLabel(selectedFrame.status) }}</el-tag>
              <span class="node-dur">{{ fmtDuration(selectedFrame.durationMs) }}</span>
              <el-button text size="small" @click="selectedFrame = null"><el-icon><Close /></el-icon></el-button>
            </div>
            <div class="node-kv">
              <div><span>耗时</span><b>{{ fmtDuration(selectedFrame.durationMs) }}</b></div>
              <div><span>起始偏移</span><b>{{ fmtDuration(selectedFrame.startMs) }}</b></div>
              <div><span>子节点</span><b>{{ childrenOf(selectedFrame).length }}</b></div>
              <div><span>节点ID</span><b class="mono">{{ selectedFrame.id }}</b></div>
            </div>
            <div class="io-block">
              <div class="io-row">
                <span class="io-label">输入</span>
                <pre class="io-content">{{ selectedFrame.inputDetail || '（暂无输入记录）' }}</pre>
              </div>
              <div class="io-row">
                <span class="io-label">输出</span>
                <pre class="io-content" :class="{ err: frameType(selectedFrame.status) === 'danger' }">{{ selectedFrame.outputDetail || '（暂无输出记录）' }}</pre>
              </div>
            </div>
          </div>

          <div class="section">
            <div class="section-head">
              <span class="section-title">调用树</span>
              <div class="section-actions">
                <el-switch v-model="expandAll" active-text="展开" size="small" style="margin-right:12px" />
                <el-switch v-model="showTree" active-text="显示调用树" size="small" />
              </div>
            </div>
            <template v-if="showTree">
              <el-table :data="treeData" :key="String(expandAll)" row-key="id" :default-expand-all="expandAll"
                        class="tree-table" :tree-props="{ children: 'children' }" size="small"
                        :row-class-name="treeRowClass" @row-click="selectFrame">
                <el-table-column label="节点" min-width="260">
                  <template #default="{ row }">
                    <div class="tree-node-name"><el-icon color="#94a3b8" size="13"><Connection /></el-icon><span>{{ row.label }}</span></div>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="84" align="center">
                  <template #default="{ row }"><el-tag :type="frameType(row.status)" size="small" effect="light">{{ frameLabel(row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="耗时" width="170">
                  <template #default="{ row }">
                    <div class="dur-cell">
                      <div class="dur-bar"><div class="dur-fill" :style="{ width: ((row.durationMs || 0) / maxDur * 100) + '%', background: colorFor(row.nodeName) }"></div></div>
                      <span class="dur-txt">{{ fmtDuration(row.durationMs) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="输出预览" min-width="220" show-overflow-tooltip>
                  <template #default="{ row }"><span class="detail-txt">{{ row.outputDetail || '-' }}</span></template>
                </el-table-column>
              </el-table>
            </template>
            <el-empty v-else description="已隐藏调用树" :image-size="50" />
          </div>

          <div class="section" v-if="topSlow.length">
            <div class="section-head">
              <span class="section-title">耗时排行榜</span>
              <span class="section-hint">定位最耗时的节点</span>
            </div>
            <div class="top-slow">
              <div v-for="(f, i) in topSlow" :key="f.id" class="slow-row" @click="selectFrame(f)">
                <span class="rank" :class="'rk-' + (i + 1)">{{ i + 1 }}</span>
                <span class="slow-name">{{ f.label }}</span>
                <div class="dur-bar slow"><div class="dur-fill" :style="{ width: ((f.durationMs || 0) / maxDur * 100) + '%', background: '#2563eb' }"></div></div>
                <span class="slow-dur">{{ fmtDuration(f.durationMs) }}</span>
              </div>
            </div>
          </div>
        </template>

        <el-empty v-else-if="!loading" description="从左侧选择一条任务，查看火焰图与调用树" :image-size="90" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.debug-page { display: flex; gap: 16px; height: calc(100vh - 76px); padding: 4px 4px 0; }
.task-aside { display: flex; }
.aside-card { width: 100%; display: flex; flex-direction: column; }
.aside-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; overflow: hidden; padding-top: 12px; }
.aside-head { display: flex; justify-content: space-between; align-items: center; }
.aside-title { font-weight: 700; font-size: 15px; }
.aside-actions { display: flex; align-items: center; gap: 10px; }
.filter-bar { display: flex; gap: 8px; margin: 12px 0; }
.filter-bar .el-input { flex: 1; }
.task-table { flex: 1; }
.task-table :deep(.current-row) { background: var(--brand-weak); }
.task-table :deep(.el-table__row) { cursor: pointer; }
.dur { font-variant-numeric: tabular-nums; color: #0f172a; font-weight: 600; font-size: 12px; }
.time { color: #94a3b8; font-size: 12px; }

.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.stat-cell { background: #f8fafc; border: 1px solid #eef2f7; border-radius: 10px; padding: 8px 6px; text-align: center; }
.stat-cell b { display: block; font-size: 15px; color: #0f172a; font-variant-numeric: tabular-nums; }
.stat-cell span { font-size: 11px; color: #94a3b8; }
.stat-cell.good b { color: var(--success); }
.stat-cell.warn b { color: var(--brand); }
.stat-cell.bad b { color: var(--danger); }

.detail-wrap { flex: 1; min-width: 0; display: flex; }
.detail-card { flex: 1; }
.detail-card :deep(.el-card__body) { overflow-y: auto; max-height: calc(100vh - 140px); }
.detail-head { display: flex; justify-content: space-between; align-items: center; }
.detail-title { font-weight: 700; font-size: 15px; }
.detail-meta { display: flex; align-items: center; gap: 8px; }
.meta-role { color: #64748b; font-size: 12px; background: #f1f5f9; padding: 2px 8px; border-radius: 6px; }

.q-bar { display: flex; align-items: center; gap: 8px; background: #f8fafc; border: 1px solid var(--line); border-radius: 10px; padding: 8px 12px; margin-bottom: 12px; }
.q-text { font-size: 14px; color: #0f172a; font-weight: 500; }
.q-time { margin-left: auto; color: var(--ink-3); font-size: 12px; white-space: nowrap; }
.detail-stats { margin-bottom: 16px; }

.section { margin-top: 16px; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.section-title { font-weight: 700; font-size: 14px; color: #0f172a; }
.section-hint { color: #94a3b8; font-size: 12px; }
.section-actions { display: flex; align-items: center; }

.flame-wrap { position: relative; background: #f8fafc; border: 1px solid #eef2f7; border-radius: 10px; overflow-y: auto; overflow-x: hidden; padding: 2px; cursor: grab; touch-action: none; user-select: none; }
.flame-wrap.dragging { cursor: grabbing; }
.level { position: absolute; left: 0; right: 0; height: 28px; padding: 0 2px; }
.flame__bar { position: absolute; top: 2px; height: 24px; border-radius: 5px; color: #fff; font-size: 11px; line-height: 24px; padding-left: 6px; cursor: pointer; white-space: nowrap; overflow: hidden; box-shadow: inset 0 0 0 1px rgba(255,255,255,.25); transition: filter .12s, box-shadow .12s; }
.flame__bar:hover { filter: brightness(1.12); }
.flame__bar.active { box-shadow: 0 0 0 2px #0f172a; z-index: 5; }

.flame-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.flame-zoom { display: flex; align-items: center; gap: 6px; }
.flame-zoom .el-button { margin-left: 0; }
.zoom-pct { font-size: 12px; color: #64748b; font-variant-numeric: tabular-nums; min-width: 48px; text-align: center; }

.node-panel { border: 1px solid #e2e8f0; background: #f8fafc; border-radius: 10px; padding: 10px 12px; margin-top: 10px; }
.node-panel-head { display: flex; align-items: center; gap: 8px; }
.node-name { font-weight: 700; font-size: 14px; }
.node-dur { color: var(--brand); font-size: 12px; font-variant-numeric: tabular-nums; margin-left: auto; }
.node-panel-head .el-button { margin-left: 4px; }
.node-kv { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 10px; }
.node-kv div { background: #fff; border: 1px solid #eef2f7; border-radius: 8px; padding: 6px 8px; }
.node-kv span { display: block; font-size: 11px; color: #94a3b8; }
.node-kv b { font-size: 13px; color: #0f172a; font-variant-numeric: tabular-nums; }
.node-kv .mono { font-family: Consolas, monospace; font-size: 11px; word-break: break-all; }
.io-block { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.io-row { display: flex; gap: 8px; }
.io-label { flex: none; width: 34px; text-align: center; font-size: 12px; color: #fff; background: #64748b; border-radius: 6px; line-height: 20px; height: 20px; }
.io-content { flex: 1; margin: 0; background: #0b1220; color: #d3e0ff; font-family: Consolas, 'Courier New', monospace; font-size: 12px; line-height: 1.5; padding: 8px 10px; border-radius: 8px; max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.io-content.err { color: #ffb3ab; background: #2a1016; }

.tree-table { width: 100%; }
.tree-table :deep(.is-current) > td { background: var(--brand-weak); }
.tree-table :deep(.el-table__row) { cursor: pointer; }
.tree-node-name { display: flex; align-items: center; gap: 6px; }
.dur-cell { display: flex; align-items: center; gap: 8px; }
.dur-bar { flex: 1; max-width: 120px; height: 8px; background: #eef2f7; border-radius: 4px; overflow: hidden; }
.dur-fill { height: 100%; border-radius: 4px; }
.dur-bar.slow { max-width: none; }
.dur-txt { font-size: 12px; color: #475569; font-variant-numeric: tabular-nums; white-space: nowrap; }
.detail-txt { color: #64748b; font-size: 12px; }

.top-slow { border: 1px solid #eef2f7; border-radius: 10px; overflow: hidden; }
.slow-row { display: flex; align-items: center; gap: 10px; padding: 7px 12px; cursor: pointer; border-bottom: 1px solid #f1f5f9; transition: background .12s; }
.slow-row:last-child { border-bottom: none; }
.slow-row:hover { background: #f3f6fc; }
.rank { width: 20px; height: 20px; border-radius: 50%; background: #e2e8f0; color: #475569; font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; flex: none; }
.rk-1 { background: var(--brand); color: #fff; }
.rk-2 { background: #64748b; color: #fff; }
.rk-3 { background: #94a3b8; color: #fff; }
.slow-name { width: 220px; font-size: 13px; color: #0f172a; flex: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slow-dur { width: 80px; text-align: right; font-variant-numeric: tabular-nums; color: #0f172a; font-weight: 600; font-size: 13px; flex: none; }
</style>
