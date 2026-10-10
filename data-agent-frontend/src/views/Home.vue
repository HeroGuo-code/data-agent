<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
const router = useRouter()
const route = useRoute()
function go(where: string) { router.push(where) }

interface NavLink { path: string; icon: string; label: string }
type NavEntry = ({ type: 'link' } & NavLink) | { type: 'group'; icon: string; label: string; children: NavLink[] }

const BIZ_NAV: NavLink[] = [
  { path: '/orders', icon: 'Tickets', label: '订单管理' },
  { path: '/products', icon: 'Goods', label: '商品管理' },
  { path: '/shops', icon: 'OfficeBuilding', label: '店铺管理' },
  { path: '/inventory', icon: 'Box', label: '库存管理' },
  { path: '/refunds', icon: 'RefreshLeft', label: '退款管理' },
  { path: '/users', icon: 'User', label: '用户管理' },
]

const NAV: NavEntry[] = [
  { type: 'link', path: '/', icon: 'HomeFilled', label: '首页' },
  { type: 'link', path: '/chat', icon: 'ChatDotRound', label: '数据问答' },
  { type: 'group', icon: 'ShoppingCart', label: '业务管理', children: BIZ_NAV },
  { type: 'link', path: '/asset', icon: 'DataBoard', label: '数据资产' },
  { type: 'link', path: '/knowledge', icon: 'Collection', label: '知识库' },
  { type: 'link', path: '/debug', icon: 'Search', label: '调试中心' },
  { type: 'link', path: '/agent-config', icon: 'MagicStick', label: '智能助手配置' },
]

interface Info { title: string; desc: string; node?: string; route?: string }
interface Group { key: string; label: string; color: string; items: Info[] }

const techGroups: Group[] = [
  {
    key: 'front', label: '前端', color: '#2563eb',
    items: [
      { title: 'Vue 3 + TypeScript', desc: '组合式 API，类型安全' },
      { title: 'Vite', desc: '秒级热更新开发体验' },
      { title: 'Element Plus', desc: '企业级组件库' },
      { title: 'ECharts', desc: '报告与流程图渲染' },
    ],
  },
  {
    key: 'back', label: '后端', color: '#059669',
    items: [
      { title: 'Spring Boot 3.5', desc: '轻量微服务底座' },
      { title: 'Java 21', desc: '虚拟线程与现代化特性' },
      { title: 'Spring AI', desc: 'LLM 对话与流式输出' },
      { title: 'A2A 协议（SSE）', desc: 'Agent 间标准通信接口' },
    ],
  },
  {
    key: 'graph', label: '图编排与意图', color: '#1d4ed8',
    items: [
      { title: 'StateGraph', desc: '有状态的多节点图编排引擎' },
      { title: '意图识别节点', desc: '前置过滤问候/闲聊/无关' },
      { title: '提示词输出节点', desc: '开场白与人设回复' },
      { title: 'NL2SQL 规划器', desc: '语义解析到可执行计划' },
    ],
  },
  {
    key: 'data', label: '数据与检索', color: '#60a5fa',
    items: [
      { title: 'PostgreSQL + pgvector', desc: '向量化语义检索' },
      { title: 'FastEmbed e5-large', desc: '多语言 embedding 服务' },
      { title: '知识库', desc: '文档/词汇库向量召回' },
      { title: '证据召回 + Schema', desc: '表结构/关系语义召回' },
    ],
  },
  {
    key: 'ops', label: '部署与配置', color: '#64748b',
    items: [
      { title: 'Docker Compose', desc: 'Postgres/Redis/Embedding 容器化' },
      { title: 'Redis 缓存', desc: 'SQL 结果一致性缓存' },
      { title: '权限隔离 + 脱敏', desc: '角色越权拦截与敏感数据打码' },
    ],
  },
]

const highlights: Info[] = [
  { title: '意图识别先过滤', desc: '先判断问候/闲聊/无关与数据请求：问候直接回开场白，闲聊交大模型，只有数据请求才走 NL2SQL，省时省钱。', node: 'INTENT' },
  { title: '开场白与预设问题', desc: '可配置开场白、人设提示词与预设问题，空会话即展示，问候节点直接流式输出。', node: 'PROMPT' },
  { title: 'NL2SQL 规划执行', desc: '规划器将自然语言拆解为多步计划，SQL 生成→安全校验→只读执行，支持 Python 沙箱分析。', node: 'PLANNER' },
  { title: '权限隔离与脱敏', desc: '运营仅限本店数据，坐席仅查询单买家，敏感字段脱敏，越权拦截并记录日志。', node: 'SQL_EXEC' },
  { title: '人工确认 HITL', desc: '可开关的人工确认节点，执行前停下来展示方案，等你点“提交”并补充条件后继续。', node: 'HUMAN' },
  { title: '多轮上下文继承', desc: '追问自动带上历史对话，筛选条件按需继承或重置。', node: 'EVIDENCE' },
  { title: '简洁/复杂回答模式', desc: '一键切换：复杂展开明细与图表，简洁只给核心结论。', node: 'REPORT' },
  { title: '调试管理', desc: '调用树 + 节点输入输出 + 耗时排行，全程可追踪。', route: '/debug' },
]

const stats = [
  { num: '17', label: '图节点' },
  { num: '9', label: '数据表' },
  { num: '400+', label: '模拟订单' },
  { num: '实时', label: '调度追踪' },
]

/* ---------------- 图拓扑（节点 + 所有可能边） ---------------- */
const CAT_COLOR: Record<string, string> = {
  gate: '#94a3b8', recall: '#3b82f6', assess: '#60a5fa', plan: '#1d4ed8',
  human: '#93c5fd', exec: '#059669', sql: '#1e40af', python: '#475569', report: '#2563eb',
  intent: '#64748b', prompt: '#cbd5e1',
}
const CAT_NAME: Record<string, string> = {
  gate: '起止', recall: '召回', assess: '评估', plan: '规划',
  human: '人工', exec: '执行', sql: 'SQL', python: 'Python', report: '报告',
  intent: '意图', prompt: '输出',
}
type GNode = { name: string; label: string; cat: keyof typeof CAT_COLOR; x: number; y: number; size?: number }
type GEdge = { source: string; target: string; label?: string; kind: 'solid' | 'cond' }

const graphNodes: GNode[] = [
  { name: 'START', label: '开始', cat: 'gate', x: 60, y: 240, size: 20 },
  { name: 'INTENT', label: '意图识别', cat: 'intent', x: 190, y: 240 },
  { name: 'PROMPT', label: '提示词输出', cat: 'prompt', x: 190, y: 100 },
  { name: 'EVIDENCE', label: '证据召回', cat: 'recall', x: 320, y: 240 },
  { name: 'SCHEMA', label: 'Schema 召回', cat: 'recall', x: 450, y: 240 },
  { name: 'TABLE_REL', label: '表关系', cat: 'recall', x: 580, y: 240 },
  { name: 'FEASIBILITY', label: '可行性评估', cat: 'assess', x: 710, y: 240 },
  { name: 'REPORT', label: '报告生成', cat: 'report', x: 850, y: 100 },
  { name: 'END', label: '结束', cat: 'gate', x: 1020, y: 100, size: 20 },
  { name: 'PLANNER', label: '规划器', cat: 'plan', x: 850, y: 330 },
  { name: 'HUMAN', label: '人工确认', cat: 'human', x: 850, y: 440 },
  { name: 'PLAN_EXEC', label: '计划执行', cat: 'exec', x: 850, y: 550 },
  { name: 'SQL_GEN', label: 'SQL 生成', cat: 'sql', x: 600, y: 640 },
  { name: 'SQL_EXEC', label: 'SQL 执行', cat: 'sql', x: 480, y: 480 },
  { name: 'PY_GEN', label: 'Python 生成', cat: 'python', x: 1060, y: 640 },
  { name: 'PY_EXEC', label: 'Python 执行', cat: 'python', x: 1060, y: 480 },
  { name: 'PY_ANALY', label: 'Python 分析', cat: 'python', x: 1060, y: 320 },
]

const graphEdges: GEdge[] = [
  { source: 'START', target: 'INTENT', kind: 'solid' },
  { source: 'INTENT', target: 'EVIDENCE', label: '数据分析', kind: 'cond' },
  { source: 'INTENT', target: 'PROMPT', label: '问候/闲聊/无关', kind: 'cond' },
  { source: 'PROMPT', target: 'END', kind: 'solid' },
  { source: 'EVIDENCE', target: 'SCHEMA', kind: 'solid' },
  { source: 'SCHEMA', target: 'TABLE_REL', kind: 'solid' },
  { source: 'TABLE_REL', target: 'FEASIBILITY', kind: 'solid' },
  { source: 'FEASIBILITY', target: 'PLANNER', label: '数据分析', kind: 'cond' },
  { source: 'FEASIBILITY', target: 'REPORT', label: '需澄清/追问', kind: 'cond' },
  { source: 'FEASIBILITY', target: 'END', label: '业务咨询', kind: 'cond' },
  { source: 'PLANNER', target: 'PLAN_EXEC', label: '清晰直接执行', kind: 'solid' },
  { source: 'PLANNER', target: 'HUMAN', label: '仅口径存疑', kind: 'cond' },
  { source: 'HUMAN', target: 'PLAN_EXEC', label: '确认继续', kind: 'cond' },
  { source: 'HUMAN', target: 'PLANNER', label: '退回补充', kind: 'cond' },
  { source: 'HUMAN', target: 'END', label: '重试≥3', kind: 'cond' },
  { source: 'PLAN_EXEC', target: 'SQL_GEN', label: 'SQL 步骤', kind: 'cond' },
  { source: 'PLAN_EXEC', target: 'PY_GEN', label: 'Python 步骤', kind: 'cond' },
  { source: 'PLAN_EXEC', target: 'REPORT', label: '计划完成', kind: 'cond' },
  { source: 'PLAN_EXEC', target: 'END', label: '无可执行', kind: 'cond' },
  { source: 'SQL_GEN', target: 'SQL_EXEC', kind: 'solid' },
  { source: 'SQL_EXEC', target: 'PLAN_EXEC', label: '下一环节', kind: 'cond' },
  { source: 'PY_GEN', target: 'PY_EXEC', kind: 'solid' },
  { source: 'PY_EXEC', target: 'PY_ANALY', kind: 'solid' },
  { source: 'PY_ANALY', target: 'PLAN_EXEC', label: '下一环节', kind: 'cond' },
  { source: 'REPORT', target: 'END', kind: 'solid' },
]

const NODE_INFO: Record<string, { cat: string; role: string; input: string; output: string }> = {
  START: { cat: '起止', role: '会话起点：接收用户自然语言问题，初始化问答上下文（操作人、会话ID、数据库、回答模式等）。', input: '用户提问 + 会话元信息', output: '标准化提问与上下文' },
  INTENT: { cat: '意图', role: '意图识别：在证据召回前先判断请求类型——问候→直接回开场白；闲聊/无关→交给大模型自然回复；数据请求→进入检索链路。过滤闲聊，省去无谓的 NL2SQL 开销。', input: '用户提问', output: '意图类型（问候/闲聊/数据）' },
  PROMPT: { cat: '输出', role: '提示词输出：问候/闲聊/无关不进入数据分析链路，按配置的开场白（问候）或人设提示词（闲聊）直接回复用户并流式返回。', input: '意图类型 + 用户原话', output: '开场白 / 人设回复（流式）' },
  EVIDENCE: { cat: '召回', role: '证据召回：从向量知识库检索与该问题最相似的历史沉淀证据（含点赞沉淀项），作为后续规划与生成的参考依据。', input: '问题向量', output: 'Top-K 相似证据（含相似度）' },
  SCHEMA: { cat: '召回', role: 'Schema 召回：召回候选数据表的字段结构、注释与类型定义，让模型知道有哪些可用数据资源与字段含义。', input: '意图关键词', output: '相关表结构定义' },
  TABLE_REL: { cat: '召回', role: '表关系：召回表与表之间的关联关系（外键 / 业务关联），保证生成正确的 JOIN 与关联查询。', input: '涉及的表集合', output: '表关系图谱 / 关联说明' },
  FEASIBILITY: { cat: '评估', role: '可行性评估：判断问题能否用现有数据回答，识别需求类型（数据分析 / 需澄清 / 业务咨询）。只有需求需澄清 / 口径不明确时才需要人工介入。', input: '问题 + 召回结果', output: '需求类型判定 + 澄清问题/可执行结论' },
  REPORT: { cat: '报告', role: '报告生成：汇总各环节执行结果，按简洁/复杂模式生成最终分析报告 / 表格 / 图表并流式返回给用户。', input: '各步骤执行结果', output: '结构化报告 / 结论' },
  END: { cat: '起止', role: '结束：任务终止节点，输出最终状态（成功 / 需澄清 / 重试达上限 / 非数据咨询直接返回回复）。', input: '上游最终结果', output: '最终状态与返回内容' },
  PLANNER: { cat: '规划', role: '规划器：把需求拆解为可执行的步骤计划（SQL / Python）。清晰的需求直接进入计划执行；仅当口径存疑时才流转到人工确认。', input: '需求类型 + 筛选上下文', output: '多步执行计划' },
  HUMAN: { cat: '人工', role: '人工确认 HITL（可开关）：仅在需求需澄清 / 口径不明确时才介入，暂停并展示待确认问题，等待补充说明；清晰的数据分析问题不经过人工。', input: '待确认问题（仅口径存疑时）', output: '补充后继续 / 退回规划 / 结束' },
  PLAN_EXEC: { cat: '执行', role: '计划执行：按计划逐一编排执行各步骤，收集每步输出并驱动后续节点流转。', input: '多步执行计划', output: '每步执行结果' },
  SQL_GEN: { cat: 'SQL', role: 'SQL 生成：依据 Schema 与表关系为 SQL 步骤生成只读 SQL，并做安全规则校验。', input: 'SQL 步骤描述', output: '通过校验的只读 SQL' },
  SQL_EXEC: { cat: 'SQL', role: 'SQL 执行：先做安全校验（仅 SELECT、禁止 DROP/ALTER/DELETE、防大批量全表扫描），再在只读沙箱执行；结果带一致性缓存——业务表更新时按表精准淘汰，另以数据文件指纹兜底，重复查询直接命中缓存。', input: '只读 SQL', output: '查询结果集 / 脱敏数据（可命中缓存）' },
  PY_GEN: { cat: 'Python', role: 'Python 生成：为数据分析步骤生成可执行的 Python 分析脚本。', input: 'Python 步骤描述', output: '分析脚本' },
  PY_EXEC: { cat: 'Python', role: 'Python 执行：在隔离沙箱中执行分析脚本，限制网络与资源，输出执行结果。', input: '分析脚本', output: '脚本执行输出' },
  PY_ANALY: { cat: 'Python', role: 'Python 分析：对执行结果做进一步聚合、统计与洞察，产出结构化分析结论与图表数据。', input: '脚本执行输出', output: '分析结论 / 图表数据' },
}

interface SelNode { name: string; label: string; cat: string; role: string; input: string; output: string }
const selected = ref<SelNode>()
function selectedNodeCat(): string {
  const n = selected.value
  if (!n) return ''
  const node = graphNodes.find((g) => g.name === n.name)
  return node ? node.cat : ''
}
const graphEl = ref<HTMLDivElement>()
const graphSection = ref<HTMLElement>()
function openHighlight(h: Info) {
  if (h.route) { router.push(h.route); return }
  if (h.node) focusNode(h.node)
}
function focusNode(name: string) {
  const node = graphNodes.find((n) => n.name === name)
  const detail = NODE_INFO[name]
  if (node && detail) {
    selected.value = { name, label: node.label, cat: detail.cat, role: detail.role, input: detail.input, output: detail.output }
  }
  graphSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
let chart: echarts.ECharts | undefined
let resizeGuard: ReturnType<typeof setTimeout> | undefined

function renderGraph() {
  if (!graphEl.value) return
  chart = echarts.init(graphEl.value, undefined, { renderer: 'canvas' })
  const colorPalette: Record<string, string> = CAT_COLOR
  const nodes = graphNodes.map((n) => ({
    id: n.name,
    name: n.label,
    x: n.x,
    y: n.y,
    symbolSize: n.size ?? 58,
    category: CAT_NAME[n.cat] || n.cat,
    itemStyle: { color: colorPalette[n.cat] },
    label: { show: true, position: 'bottom', color: '#334155', fontSize: 13, fontWeight: 600 },
  }))
  const links = graphEdges.map((e) => ({
    source: e.source,
    target: e.target,
    lineStyle: e.kind === 'cond'
      ? { color: '#2563eb', width: 2.5, type: 'dashed', curveness: 0.1 }
      : { color: '#94a3b8', width: 2.2, type: 'solid', curveness: 0.04 },
    label: { show: !!e.label, formatter: e.label || '', color: '#1d4ed8', fontSize: 12, fontWeight: 600, backgroundColor: '#eff6ff', borderColor: '#bfdbfe', borderWidth: 1, borderRadius: 4, padding: [2, 5] },
  }))
  const cats = Object.entries(CAT_COLOR).map(([k, c]) => ({ name: CAT_NAME[k] || k, itemStyle: { color: c } }))
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { top: 4, textStyle: { color: '#475569' } },
    series: [{
      type: 'graph', layout: 'none', roam: true, draggable: true,
      data: nodes, links, categories: cats,
      lineStyle: { color: '#94a3b8', width: 2, curveness: 0.08 },
      edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [8, 10],
      emphasis: { focus: 'adjacency', lineStyle: { width: 4 } },
    }],
  })
  chart.on('click', (p: any) => {
    if (p.dataType !== 'node' || !p.data) return
    const name = p.data.id
    const node = graphNodes.find((n) => n.name === name)
    const detail = NODE_INFO[name]
    selected.value = node && detail ? { name, label: node.label, cat: detail.cat, role: detail.role, input: detail.input, output: detail.output } : undefined
  })
}

function handleResize() { chart?.resize() }

onMounted(() => {
  renderGraph()
  window.addEventListener('resize', handleResize)
  resizeGuard = setTimeout(() => { try { chart?.resize() } catch { /* ignore */ } }, 400)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeGuard) clearTimeout(resizeGuard)
  chart?.dispose()
  chart = undefined
})
</script>

<template>
  <div class="home">
    <nav class="topnav">
      <div class="topnav__inner">
        <span class="topnav__brand" @click="go('/')">Data&nbsp;Agent</span>
        <div class="topnav__links">
          <template v-for="n in NAV" :key="n.label">
            <a
              v-if="n.type === 'link'"
              class="topnav__link"
              :class="{ 'is-active': route.path === n.path }"
              @click="go(n.path)"
            ><el-icon class="topnav__icon"><component :is="n.icon" /></el-icon>{{ n.label }}</a>
            <div v-else class="topnav__group">
              <a
                class="topnav__link"
                :class="{ 'is-active': n.children.some((c) => route.path === c.path) }"
              ><el-icon class="topnav__icon"><component :is="n.icon" /></el-icon>{{ n.label }}<span class="topnav__caret">▾</span></a>
              <div class="topnav__dropdown">
                <a
                  v-for="c in n.children"
                  :key="c.path"
                  class="topnav__item"
                  :class="{ 'is-active': route.path === c.path }"
                  @click="go(c.path)"
                ><el-icon class="topnav__icon"><component :is="c.icon" /></el-icon>{{ c.label }}</a>
              </div>
            </div>
          </template>
        </div>
      </div>
    </nav>
    <!-- Hero -->
    <section class="hero">
      <div class="hero__inner">
        <div class="hero__tag">Data Agent · 电商经营数据智能问答</div>
        <h1 class="hero__title">让数据开口说话，<br />一句话洞察经营</h1>
        <p class="hero__sub">
          面向店铺运营与客服坐席的自然语言数据问答系统：语义解析、NL2SQL、权限隔离、多轮上下文、
          人工确认与全链路调试追踪，开箱即用。
        </p>
        <div class="hero__actions">
          <el-button type="primary" size="large" class="hero__btn" @click="go('/chat')">开始问答</el-button>
          <el-button size="large" class="hero__btn hero__btn--ghost" @click="go('/debug')">查看调试</el-button>
            <el-button size="large" class="hero__btn hero__btn--ghost" @click="go('/agent-config')">智能助手配置</el-button>
        </div>
        <div class="hero__stats">
          <div v-for="s in stats" :key="s.label" class="hstat">
            <b>{{ s.num }}</b><span>{{ s.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 图拓扑 -->
    <section ref="graphSection" class="block">
      <div class="block__head">
        <h2>执行图拓扑</h2>
        <p>StateGraph 全部节点与可能路径，可拖动 / 滚轮缩放；蓝色虚线为条件分支</p>
      </div>
      <div class="graph-wrap">
        <div ref="graphEl" class="graph-canvas"></div>
        <div class="graph-legend-tip">
          <span class="tip-line">&nbsp;&nbsp;&nbsp;&nbsp;固定流转</span>
          <span class="tip-line tip-line--cond">&nbsp;&nbsp;&nbsp;&nbsp;条件分支</span>
        </div>
        <div v-if="selected" class="graph-info" :style="'--nc:' + (CAT_COLOR[selectedNodeCat()] || '#64748b')">
          <div class="graph-info__head">
            <span class="graph-info__badge" :style="{ background: CAT_COLOR[selectedNodeCat()] || '#64748b' }">{{ selected.cat }}</span>
            <h3>{{ selected.label }}</h3>
            <button class="graph-info__close" @click="selected = undefined">×</button>
          </div>
          <p class="graph-info__role">{{ selected.role }}</p>
          <div class="graph-info__io">
            <div class="graph-info__io-col"><span class="graph-info__io-tag in">输入</span><span>{{ selected.input }}</span></div>
            <div class="graph-info__io-col"><span class="graph-info__io-tag out">输出</span><span>{{ selected.output }}</span></div>
          </div>
        </div>
      </div>
    </section>

    <!-- 功能亮点 -->
    <section class="block">
      <div class="block__head">
        <h2>项目亮点</h2>
        <p>从“能答”到“答得准、答得安全、答得可追溯”</p>
      </div>
      <div class="card-grid four">
        <div v-for="h in highlights" :key="h.title" class="feature" @click="openHighlight(h)" title="点击在图中定位对应节点">
          <h3>{{ h.title }}</h3>
          <p>{{ h.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="block">
      <div class="block__head">
        <h2>技术栈</h2>
        <p>面向生产的多层技术选型</p>
      </div>
      <div class="stack-grid">
        <div v-for="g in techGroups" :key="g.key" class="stack-card">
          <div class="stack-card__head" :style="{ borderColor: g.color }">
            <span class="stack-card__dot" :style="{ background: g.color }"></span>
            <h3>{{ g.label }}</h3>
          </div>
          <ul class="stack-card__list">
            <li v-for="it in g.items" :key="it.title">
              <span class="stack-name">{{ it.title }}</span>
              <span class="stack-desc">{{ it.desc }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home { max-width: 1180px; margin: 0 auto; padding-bottom: 40px; }
.hero { background: linear-gradient(135deg, #eff6ff 0%, #f6f8fb 45%, #ffffff 100%); border-radius: var(--radius-card); padding: 56px 48px; margin-bottom: 40px; box-shadow: 0 8px 30px rgba(37,99,235,.08); }
.hero__tag { display: inline-block; background: #fff; border: 1px solid var(--brand-border); color: var(--brand); font-size: 12px; font-weight: 600; letter-spacing: .04em; padding: 5px 12px; border-radius: 999px; margin-bottom: 18px; }
.hero__title { font-size: 40px; line-height: 1.25; font-weight: 800; color: var(--ink); margin: 0 0 14px; }
.hero__sub { color: var(--ink-2); font-size: 15px; line-height: 1.8; max-width: 600px; margin: 0 0 24px; }
.hero__actions { display: flex; gap: 14px; margin-bottom: 28px; }
.hero__btn { border-radius: 12px; padding: 22px 24px; font-size: 15px; }
.hero__btn--ghost { background: #fff; }
.hero__stats { display: flex; gap: 40px; }
.hstat b { display: block; font-size: 26px; color: var(--brand); font-weight: 800; }
.hstat span { font-size: 12px; color: var(--ink-3); }

.block { margin-bottom: 40px; }
.block__head { text-align: center; margin-bottom: 24px; }
.block__head h2 { font-size: 26px; font-weight: 800; color: var(--ink); margin: 0 0 8px; }
.block__head p { color: var(--ink-3); font-size: 14px; margin: 0; }

.card-grid { display: grid; gap: 16px; }
.card-grid.four { grid-template-columns: repeat(4, 1fr); }
.feature { cursor: pointer; background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-card); padding: 20px; transition: transform .15s, box-shadow .15s; }
.feature:hover { transform: translateY(-3px); box-shadow: var(--shadow-2); }
.feature h3 { display: flex; align-items: center; gap: 8px; font-size: 15px; color: var(--ink); margin: 0 0 6px; }
.feature h3::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: var(--brand); flex-shrink: 0; }
.feature p { font-size: 12.5px; line-height: 1.7; color: var(--ink-2); margin: 0; }

.stack-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stack-card { background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-card); padding: 18px; }
.stack-card__head { display: flex; align-items: center; gap: 8px; border-left: 3px solid transparent; padding-left: 8px; margin-bottom: 12px; }
.stack-card__head h3 { font-size: 15px; margin: 0; color: var(--ink); }
.stack-card__dot { width: 8px; height: 8px; border-radius: 50%; }
.stack-card__list { list-style: none; margin: 0; padding: 0; }
.stack-card__list li { padding: 7px 0; border-bottom: 1px dashed #f1f5f9; }
.stack-card__list li:last-child { border-bottom: none; }
.stack-name { display: flex; align-items: center; gap: 7px; font-size: 13px; color: #0f172a; font-weight: 600; }
.stack-name::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: var(--brand); flex-shrink: 0; }
.stack-desc { display: block; font-size: 12px; color: #94a3b8; margin-top: 2px; }

.graph-wrap { background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-card); padding: 8px; }
.graph-canvas { width: 100%; height: 560px; }
.graph-legend-tip { display: flex; gap: 24px; justify-content: center; padding: 8px 0 6px; font-size: 12px; color: #94a3b8; }
.tip-line { border-top: 2px solid #94a3b8; padding-top: 2px; }
.tip-line--cond { border-top: 2px dashed var(--brand); }

.graph-info { border: 1px solid var(--nc, #64748b); border-radius: var(--radius-card); padding: 16px 18px; margin-top: 12px; background: linear-gradient(135deg, #f8fafc 0%, #ffffff 100%); }
.graph-info__head { display: flex; align-items: center; gap: 10px; }
.graph-info__badge { color: #fff; font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.graph-info__head h3 { margin: 0; font-size: 17px; color: var(--ink); font-weight: 800; flex: 1; }
.graph-info__close { border: none; background: var(--line); color: var(--ink-2); width: 26px; height: 26px; border-radius: 8px; cursor: pointer; font-size: 18px; line-height: 1; }
.graph-info__close:hover { background: #e2e8f0; color: var(--ink); }
.graph-info__role { font-size: 13.5px; line-height: 1.75; color: var(--ink-2); margin: 10px 0 12px; }
.graph-info__io { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.graph-info__io-col { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--ink-2); background: var(--card); border: 1px dashed var(--line); border-radius: 10px; padding: 8px 12px; }
.graph-info__io-tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 6px; flex-shrink: 0; }
.graph-info__io-tag.in { background: var(--success-weak); color: var(--success); }
.graph-info__io-tag.out { background: var(--brand-weak); color: #1d4ed8; }
@media (max-width: 640px) { .graph-info__io { grid-template-columns: 1fr; } }

@media (max-width: 1024px) {
  .card-grid.four, .stack-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .card-grid.four, .stack-grid { grid-template-columns: 1fr; }
  .hero__title { font-size: 30px; }
  .hero { padding: 36px 24px; }
  .graph-canvas { height: 440px; }
}
.topnav { position: sticky; top: 0; z-index: 20; background: rgba(255,255,255,.82); backdrop-filter: blur(10px); border-bottom: 1px solid var(--line); }
.topnav__inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; gap: 16px; padding: 0 20px; height: 56px; }
.topnav__brand { font-weight: 800; font-size: 15px; color: #1e293b; cursor: pointer; letter-spacing: .02em; flex-shrink: 0; }
.topnav__links { display: flex; align-items: center; gap: 4px; flex: 1; min-width: 0; }
.topnav__link { white-space: nowrap; flex-shrink: 0; font-size: 13px; color: #475569; padding: 7px 10px; border-radius: 8px; cursor: pointer; transition: background .15s, color .15s; }
.topnav__link:hover { background: #f1f5f9; color: #0f172a; }
.topnav__link.is-active { background: var(--brand-weak); color: var(--brand); font-weight: 600; }
.topnav__icon { margin-right: 4px; vertical-align: -3px; }
.topnav__group { position: relative; }
.topnav__caret { font-size: 10px; color: #94a3b8; margin-left: 4px; }
.topnav__dropdown { position: absolute; top: calc(100% + 6px); left: 0; min-width: 150px; background: #fff; border: 1px solid var(--line); border-radius: var(--radius-card); box-shadow: var(--shadow-2); padding: 6px; display: none; flex-direction: column; gap: 2px; z-index: 30; }
.topnav__dropdown::before { content: ''; position: absolute; top: -8px; left: 0; right: 0; height: 8px; }
.topnav__group:hover .topnav__dropdown, .topnav__group:focus-within .topnav__dropdown { display: flex; }
.topnav__item { display: block; white-space: nowrap; font-size: 13px; color: #475569; padding: 8px 12px; border-radius: 8px; cursor: pointer; transition: background .15s, color .15s; }
.topnav__item:hover { background: #f1f5f9; color: #0f172a; }
.topnav__item.is-active { background: var(--brand-weak); color: var(--brand); font-weight: 600; }
</style>
