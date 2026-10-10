<script setup lang="ts">
import { Client, ClientFactory } from '@a2a-js/sdk/client'
import { type AgentCard } from '@a2a-js/sdk'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import ReportView from '@/components/ReportView.vue'
import EvidenceRecallNodeCard from '@/components/evidence-recall-node-card.vue'
import SchemeRecallNodeCard from '@/components/scheme-recall-node-card.vue'
import TableRelationNodeCard from '@/components/table-relation-node-card.vue'
import FeasibilityAssessmentNodeCard from '@/components/feasibility-assessment-node-card.vue'
import PlannerNodeCard from '@/components/planner-node-card.vue'
import HumanFeedbackNodeCard from '@/components/human-feedback-node-card.vue'
import PlanExecutionNodeCard from '@/components/plan-execution-node-card.vue'
import SqlGenerationNodeCard from '@/components/sql-generation-node-card.vue'
import SqlExecutionNodeCard from '@/components/sql-execution-node-card.vue'
import PythonGenerationNodeCard from '@/components/python-generation-node-card.vue'
import PythonExecutionNodeCard from '@/components/python-execution-node-card.vue'
import PythonAnalysisNodeCard from '@/components/python-analysis-node-card.vue'
import ReportGenerationNodeCard from '@/components/report-generation-node-card.vue'
import {
  DATA_AGENT_ARTIFACT_OUTPUT,
  DATA_AGENT_GRAPH_NODE,
  DATA_AGENT_MESSAGE_METADATA,
  DATA_AGENT_NODE_ORDER,
  DATA_AGENT_STATE_KEY_EXECUTION,
  DATA_AGENT_STATE_KEY_PLANNING,
} from '@/constants/data-agent-graph-spec'
import { api } from '@/utils/api-instance.ts'
import { request } from '@/utils/request'
import { ElMessage } from 'element-plus'

interface GraphStep {
  id: string
  name: string
  content: string
  data?: Record<string, unknown>
  status: 'pending' | 'success'
}
interface ChatMsg {
  id: string
  role: 'user' | 'assistant'
  text: string
  steps: GraphStep[]
  feedback?: QaFeedback
  streaming?: boolean
  interrupted?: boolean
}
interface QaFeedback {
  liked: boolean
  likeCount: number
  disliked: boolean
  dislikeCount: number
  promoted: boolean
  demoted: boolean
  sending?: boolean
}
interface FeedbackResult {
  message?: string
  likeCount?: number
  dislikeCount?: number
  promoted?: boolean
  demoted?: boolean
  deduped?: boolean
  dupOf?: string
  threshold?: number
  demoteThreshold?: number
}
interface StoredSession {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  taskId?: string
  contextId?: string
  hitl?: boolean
  messages: ChatMsg[]
}

const NODE_LABELS: Record<string, string> = {
  [DATA_AGENT_GRAPH_NODE.EVIDENCE_RECALL]: '证据召回',
  [DATA_AGENT_GRAPH_NODE.SCHEMA_RECALL]: 'Schema 召回',
  [DATA_AGENT_GRAPH_NODE.TABLE_RELATION]: '表关系',
  [DATA_AGENT_GRAPH_NODE.FEASIBILITY_ASSESSMENT]: '可行性评估',
  [DATA_AGENT_GRAPH_NODE.PLANNER]: '规划器',
  [DATA_AGENT_GRAPH_NODE.HUMAN_FEEDBACK]: '人工确认',
  [DATA_AGENT_GRAPH_NODE.PLAN_EXECUTION]: '计划执行',
  [DATA_AGENT_GRAPH_NODE.SQL_GENERATION]: 'SQL 生成',
  [DATA_AGENT_GRAPH_NODE.SQL_EXECUTION]: 'SQL 执行',
  [DATA_AGENT_GRAPH_NODE.PYTHON_GENERATION]: 'Python 生成',
  [DATA_AGENT_GRAPH_NODE.PYTHON_EXECUTION]: 'Python 执行',
  [DATA_AGENT_GRAPH_NODE.PYTHON_ANALYSIS]: 'Python 分析',
  [DATA_AGENT_GRAPH_NODE.REPORT_GENERATION]: '报告生成',
  [DATA_AGENT_GRAPH_NODE.PROMPT_OUTPUT]: '提示词输出',
}

const NODE_CARD_MAP: Record<string, any> = {
  [DATA_AGENT_GRAPH_NODE.EVIDENCE_RECALL]: EvidenceRecallNodeCard,
  [DATA_AGENT_GRAPH_NODE.SCHEMA_RECALL]: SchemeRecallNodeCard,
  [DATA_AGENT_GRAPH_NODE.TABLE_RELATION]: TableRelationNodeCard,
  [DATA_AGENT_GRAPH_NODE.FEASIBILITY_ASSESSMENT]: FeasibilityAssessmentNodeCard,
  [DATA_AGENT_GRAPH_NODE.PLANNER]: PlannerNodeCard,
  [DATA_AGENT_GRAPH_NODE.HUMAN_FEEDBACK]: HumanFeedbackNodeCard,
  [DATA_AGENT_GRAPH_NODE.PLAN_EXECUTION]: PlanExecutionNodeCard,
  [DATA_AGENT_GRAPH_NODE.SQL_GENERATION]: SqlGenerationNodeCard,
  [DATA_AGENT_GRAPH_NODE.SQL_EXECUTION]: SqlExecutionNodeCard,
  [DATA_AGENT_GRAPH_NODE.PYTHON_GENERATION]: PythonGenerationNodeCard,
  [DATA_AGENT_GRAPH_NODE.PYTHON_EXECUTION]: PythonExecutionNodeCard,
  [DATA_AGENT_GRAPH_NODE.PYTHON_ANALYSIS]: PythonAnalysisNodeCard,
  [DATA_AGENT_GRAPH_NODE.REPORT_GENERATION]: ReportGenerationNodeCard,
}

const DEFAULT_PRESETS = [
  '本季度销售额最高的三个店铺',
  '统计6月各商品类别的销售件数与GMV',
  '近30天退款率最高的商品TOP5',
  '最近3个月月度GMV变化趋势',
  '消费金额最高的10位买家及偏好品类',
]
const DEFAULT_OPENING = '你好，我是数据智能助手小鑫。我能理解你的自然语言问题，自动生成SQL查询电商经营数据，并生成可视化分析报告。试试下面的问题，或直接输入你想了解的数据吧！'
const DEFAULT_PERSONA = '你是标准化智能助手「小鑫」，负责帮助用户查询和分析电商经营数据。请用自然、友好、简洁的语气回复用户，不要编造数据。'
const agentConfig = reactive({
  openingMessage: DEFAULT_OPENING,
  personaPrompt: DEFAULT_PERSONA,
  presets: [...DEFAULT_PRESETS],
})
const loadAgentConfig = async () => {
  try {
    const cfg: any = await request({ url: '/agent-config', method: 'GET' })
    agentConfig.openingMessage = cfg?.openingMessage ?? DEFAULT_OPENING
    agentConfig.personaPrompt = cfg?.personaPrompt ?? DEFAULT_PERSONA
    agentConfig.presets = Array.isArray(cfg?.presets) && cfg.presets.length ? cfg.presets : [...DEFAULT_PRESETS]
  } catch {
    /* 保持默认配置 */
  }
}
const DATABASE_OPTIONS = ['ecommerce_ops'] as const
const DATABASE_LABELS: Record<string, string> = { ecommerce_ops: '电商经营数据' }

const factory = new ClientFactory()
const client = shallowRef<Client | undefined>()
const messages = ref<ChatMsg[]>([])
const steps = reactive<GraphStep[]>([])
const input = ref('')
const selectedDatabase = ref<(typeof DATABASE_OPTIONS)[number]>('ecommerce_ops')

/* 反馈来源与幂等身份：operator 标识当前操作人，随反馈一起上报（demo 默认 demo-user，可改为真实账号）*/
const operator = ref(localStorage.getItem('dataAgent.operator.v1') || 'demo-user')
watch(operator, (v) => { try { localStorage.setItem('dataAgent.operator.v1', v || '') } catch {} })
const currentTaskId = ref<string>()
const currentContextId = ref<string>()
const awaitingConfirmation = ref(false)
const confirmationFeedback = ref('用户确认继续执行')
const HITL_KEY = 'dataAgent.hitl.v1'
const humanInTheLoop = ref(localStorage.getItem(HITL_KEY) === '1')
watch(humanInTheLoop, (v) => { try { localStorage.setItem(HITL_KEY, v ? "1" : "0") } catch {} })
const isRunning = ref(false)
// 当前进行中的流控制器：切换页面组件卸载时中止本地流，避免孤儿流继续双写本地状态
let activeAbort: AbortController | undefined
onUnmounted(() => {
  activeAbort?.abort()
})

// 业务流超时保障：长时间没有接收到 SSE 事件时，强制中断流并提示用户，避免气泡加载动画无限显示。
const STREAM_IDLE_TIMEOUT_MS = 360000
const REPORT_SETTLE_MS = 20000

/* ---------------- 设置面板 ---------------- */
const settingsOpen = ref(localStorage.getItem('dataAgent.settings.open.v1') !== '0')
watch(settingsOpen, (v) => { try { localStorage.setItem('dataAgent.settings.open.v1', v ? '1' : '0') } catch {} })
const SHOW_SQL_KEY = 'dataAgent.showSql.v1'
const showSqlResult = ref(localStorage.getItem(SHOW_SQL_KEY) !== '0')
watch(showSqlResult, (v) => { try { localStorage.setItem(SHOW_SQL_KEY, v ? '1' : '0') } catch {} })
const PAGE_SIZE_KEY = 'dataAgent.pageSize.v1'
const pageSize = ref(Number(localStorage.getItem(PAGE_SIZE_KEY)) || 20)
watch(pageSize, (v) => { try { localStorage.setItem(PAGE_SIZE_KEY, String(Math.max(1, Math.min(200, Number(v) || 20)))) } catch {} })
const ANSWER_MODE_KEY = 'dataAgent.answerMode.v1'
type AnswerMode = 'concise' | 'detailed'
const answerMode = ref<AnswerMode>((localStorage.getItem(ANSWER_MODE_KEY) as AnswerMode) || 'concise')
watch(answerMode, (v) => { try { localStorage.setItem(ANSWER_MODE_KEY, v) } catch {} })

const isConcise = computed(() => answerMode.value === 'concise')
const scrollRef = ref<HTMLElement>()
const elapsedSec = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | undefined
watch(isRunning, (v) => {
  if (v) {
    elapsedSec.value = 0
    if (elapsedTimer) clearInterval(elapsedTimer)
    elapsedTimer = setInterval(() => { elapsedSec.value++ }, 1000)
  } else if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = undefined
  }
})
const fmtElapsed = (s: number) => {
  if (s < 60) return s + ' 秒'
  const m = Math.floor(s / 60)
  return m + ' 分' + (s % 60) + ' 秒'
}

/* ---------------- 会话本地持久化 ---------------- */
const STORAGE_KEY = 'dataAgent.sessions.v1'
const sessions = ref<StoredSession[]>(loadSessions())
const currentSessionId = ref<string>()
let persistTimer: ReturnType<typeof setTimeout> | undefined

function loadSessions(): StoredSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
function saveSessions() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
  } catch {
    /* ignore quota errors */
  }
}
const cloneMsg = (m: ChatMsg): ChatMsg => ({
  ...m,
  steps: m.steps.map((s) => ({ ...s, data: s.data ? { ...s.data } : undefined })),
  feedback: m.feedback ? { ...m.feedback } : undefined,
})
function persistCurrent() {
  if (!currentSessionId.value) return
  const s = sessions.value.find((x) => x.id === currentSessionId.value)
  if (!s) return
  s.messages = messages.value.map(cloneMsg)
  s.taskId = currentTaskId.value
  s.contextId = currentContextId.value
  s.hitl = awaitingConfirmation.value
  s.updatedAt = Date.now()
  if (!s.title || s.title === '新对话') {
    const first = s.messages.find((m) => m.role === 'user')?.text
    if (first) s.title = first.length > 16 ? first.slice(0, 16) + '…' : first
  }
  saveSessions()
}
function activateSession(id: string) {
  if (isRunning.value || !id || id === currentSessionId.value) return
  persistCurrent()
  const s = sessions.value.find((x) => x.id === id)
  currentSessionId.value = id
  messages.value = s ? s.messages.map(cloneMsg) : []
  currentTaskId.value = s?.taskId
  currentContextId.value = s?.contextId
  steps.splice(0, steps.length)
  awaitingConfirmation.value = Boolean(s?.hitl)
  nextTick(scrollToBottom)
}
const newChat = () => {
  if (isRunning.value) return
  persistCurrent()
  const s: StoredSession = {
    id: crypto.randomUUID(),
    title: '新对话',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    messages: [],
  }
  sessions.value.unshift(s)
  currentSessionId.value = s.id
  messages.value = []
  steps.splice(0, steps.length)
  awaitingConfirmation.value = false
  currentTaskId.value = undefined
  currentContextId.value = undefined
}
const deleteSession = (id: string) => {
  if (isRunning.value) return
  const idx = sessions.value.findIndex((x) => x.id === id)
  if (idx < 0) return
  sessions.value.splice(idx, 1)
  saveSessions()
  if (currentSessionId.value === id) {
    const next = sessions.value[0]
    if (next) activateSession(next.id)
    else {
      currentSessionId.value = undefined
      messages.value = []
      steps.splice(0, steps.length)
      awaitingConfirmation.value = false
    }
  }
}
const renameId = ref<string>()
const renameValue = ref('')
const beginRename = (s: StoredSession) => {
  if (isRunning.value) return
  renameId.value = s.id
  renameValue.value = s.title === '新对话' ? '' : s.title
}
const commitRename = (id: string) => {
  if (renameId.value !== id) return
  const s = sessions.value.find((x) => x.id === id)
  if (s) {
    const t = (renameValue.value || '').trim()
    s.title = t || '新对话'
    s.updatedAt = Date.now()
    saveSessions()
  }
  renameId.value = undefined
}
const copyReport = async (m: ChatMsg) => {
  try {
    await navigator.clipboard.writeText(m.text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
const downloadReport = (m: ChatMsg) => {
  const blob = new Blob([m.text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '数据分析报告.md'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
const sortedSessions = computed(() => [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt))
const fmtTime = (t: number) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(
  [messages, currentTaskId, currentContextId],
  () => {
    clearTimeout(persistTimer)
    persistTimer = setTimeout(persistCurrent, 600)
  },
  { deep: true },
)

/* ---------------- 处理步骤 ---------------- */
const upsertStep = (name: string, chunk: string, status: GraphStep['status'], data: Record<string, unknown> = {}) => {
  const existing = steps.find((step) => step.name === name)
  if (existing) {
    existing.content += chunk
    existing.status = status
    existing.data = { ...existing.data, ...data }
    return
  }
  steps.push({ id: crypto.randomUUID(), name, data, content: chunk, status })
}

const orderedSteps = computed(() =>
  DATA_AGENT_NODE_ORDER.map((name) => steps.find((s) => s.name === name)).filter(
    (s): s is GraphStep => Boolean(s),
  ),
)

const cloneOrderedSteps = () =>
  orderedSteps.value.map((s) => ({ ...s, data: s.data ? { ...s.data } : undefined }))

const resetSteps = () => {
  steps.splice(0, steps.length)
  currentTaskId.value = undefined
  currentContextId.value = undefined
  awaitingConfirmation.value = false
  confirmationFeedback.value = '用户确认继续执行'
}

const scrollToBottom = async () => {
  await nextTick()
  scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' })
}

const lastAssistant = () => messages.value.filter((m) => m.role === 'assistant').at(-1)

const currentReport = () => {
  const step = steps.find(
    (s) => s.name === DATA_AGENT_GRAPH_NODE.REPORT_GENERATION || s.name === DATA_AGENT_GRAPH_NODE.PROMPT_OUTPUT,
  )
  return step?.content?.trim() || ''
}

const runningMessage = computed(() => (isRunning.value ? lastAssistant() : undefined))
const runningText = computed(() => {
  if (awaitingConfirmation.value) return 'AI 已生成执行方案，等待你的确认…'
  const pending = orderedSteps.value.filter((s) => s.status === 'pending').at(-1)
  if (pending) return `正在执行：${NODE_LABELS[pending.name] || pending.name} …`
  const last = orderedSteps.value.at(-1)
  if (last) return `正在${NODE_LABELS[last.name] || last.name} …`
  return '正在理解并规划你的问题，涉及多步骤检索与计算，可能需要 1-2 分钟，请稍候…'
})

const buildContextSummary = () => {
  const recent = messages.value
    .filter((m) => m.role === 'user' || (m.role === 'assistant' && m.text))
    .slice(-6)
    .map((m) => `${m.role === 'user' ? '??' : '??'}: ${m.text}`)
    .join('\n')
  return recent
}

// 统一消费任务事件流（发送/重订阅共用），并驱动每条助手消息的气泡状态机：
// streaming=true  -> 气泡旁显示旋转的加载图标（处理中）
// interrupted=true -> 流被超时/断开，图标变为可点击的“重试”，点击后重订阅该任务恢复进度
const runTaskStream = async (stream: AsyncGenerator<any>, controller: AbortController, target?: ChatMsg) => {
  const m = target
  if (m) {
    m.streaming = true
    m.interrupted = false
  }
  let idleTimer: ReturnType<typeof setTimeout> | undefined
  const hasReportContent = () => { const r = currentReport(); return !!r && !!r.trim() }
  const resetIdleWatchdog = () => {
    if (idleTimer) clearTimeout(idleTimer)
    // 报告已有内容后即视为已生成：若长时间无新事件则自动收尾，避免连接悬挂时一直卡在“报告生成中”
    const settleMs = hasReportContent() ? REPORT_SETTLE_MS : STREAM_IDLE_TIMEOUT_MS
    idleTimer = setTimeout(() => {
      idleTimer = undefined
      controller.abort()
    }, settleMs)
  }
  const clearIdleWatchdog = () => {
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = undefined
  }
  const markTerminal = () => {
    if (!m) return
    m.streaming = false
    m.interrupted = false
  }
  const markInterrupted = () => {
    if (!m) return
    m.streaming = false
    m.interrupted = true
  }
  // 报告内容已生成但未收到终态事件（连接被服务器悬挂/终态事件丢失）：直接收尾为已完成
  const settleIfReportReady = () => {
    if (!m) return false
    if (!hasReportContent()) return false
    for (const sdt of steps) if (sdt.status === 'pending') sdt.status = 'success'
    markTerminal()
    return true
  }
  let gotTerminal = false
  try {
    resetIdleWatchdog()
    for await (const event of stream) {
      resetIdleWatchdog()
      currentTaskId.value = 'taskId' in event ? event.taskId : currentTaskId.value
      currentContextId.value = 'contextId' in event ? event.contextId : currentContextId.value

      if (event.kind === 'artifact-update') {
        const artifact = event.artifact
        const artifactName = artifact.name
        const textPart = artifact.parts.find((p: any) => p.kind === 'text')?.text || ''
        const dataPart = artifact.parts.find((p: any) => p.kind === 'data')?.data
        if (artifactName) {
          const outputType = String(artifact.metadata?.outputType ?? '')
          const status: GraphStep['status'] =
            outputType === DATA_AGENT_ARTIFACT_OUTPUT.GRAPH_NODE_FINISHED ? 'success' : 'pending'
          upsertStep(artifactName, textPart, status, dataPart)
        }
        const asst = lastAssistant()
        if (asst) {
          asst.steps = cloneOrderedSteps()
          const report = currentReport()
          if (report) asst.text = report
        }
      }
      if (event.kind === 'status-update') {
        if (event.status.state === 'completed') {
          gotTerminal = true
          awaitingConfirmation.value = false
          currentTaskId.value = undefined
          currentContextId.value = undefined
          for (const step of steps) if (step.status === 'pending') step.status = 'success'
          markTerminal()
          finalized()
          break
        } else if (event.status.state === 'failed') {
          gotTerminal = true
          awaitingConfirmation.value = false
          markTerminal()
        } else if (event.status.state === 'input-required') {
          awaitingConfirmation.value = true
        }
      }
      await scrollToBottom()
    }
    // 流正常结束但未收到终态事件（连接被断/服务端提早关流）：按“可重试的中断”处理，避免气泡永远卡在“进行中”
    if (!gotTerminal) {
      if (awaitingConfirmation.value) {
        if (m) m.streaming = false
      } else if (!settleIfReportReady()) {
        markInterrupted()
      }
    }
  } catch {
    // 超时主动中止/连接出错等场景：报告已生成则收尾为完成，否则交给气泡旁的旋转图标提示用户点击重试
    if (awaitingConfirmation.value) {
      if (m) m.streaming = false
    } else if (!settleIfReportReady()) {
      markInterrupted()
    }
  } finally {
    clearIdleWatchdog()
    isRunning.value = false
    finalized()
    if (activeAbort === controller) activeAbort = undefined
  }
}

// 点击气泡旁图标：用已保存的 taskId 重新订阅同一个任务，恢复执行进度
const resubscribeMessage = async (m: ChatMsg) => {
  if (!client.value || isRunning.value || !m || m.streaming || m !== lastAssistant()) return
  const taskId = currentTaskId.value
  if (!taskId) {
    ElMessage.warning('任务上下文已失效，请重新发起提问')
    m.streaming = false
    m.interrupted = false
    return
  }
  isRunning.value = true
  const controller = new AbortController()
  activeAbort = controller
  try {
    const stream = client.value.resubscribeTask({ id: taskId }, { signal: controller.signal })
    await runTaskStream(stream, controller, m)
  } catch {
    ElMessage.warning('任务已不在服务端，请重新发起提问')
    m.streaming = false
    m.interrupted = false
    isRunning.value = false
    if (activeAbort === controller) activeAbort = undefined
  }
}

const showSpin = (m: ChatMsg) => m.role === 'assistant' && (m.streaming || m.interrupted)
const onSpinClick = async (m: ChatMsg) => {
  // 旋转中 = 还在运行，不可点；停滞 = 流断了，点击重订阅恢复任务
  if (m.streaming) return
  await resubscribeMessage(m)
}

const streamMessage = async (
  text: string,
  keepSteps = false,
  metadata?: Record<string, unknown>,
  target?: ChatMsg,
) => {
  if (!client.value || !text || isRunning.value) return
  if (!keepSteps) resetSteps()
  isRunning.value = true
  const controller = new AbortController()
  activeAbort = controller
  const stream = client.value.sendMessageStream(
    {
      message: {
        messageId: crypto.randomUUID(),
        role: 'user',
        kind: 'message',
        taskId: currentTaskId.value,
        contextId: currentContextId.value,
        metadata: {
          [DATA_AGENT_MESSAGE_METADATA.DATABASE_ID]: selectedDatabase.value,
          [DATA_AGENT_MESSAGE_METADATA.HUMAN_IN_THE_LOOP]: humanInTheLoop.value,
          [DATA_AGENT_MESSAGE_METADATA.MULTI_TURN_CONTEXT]: buildContextSummary(),
          [DATA_AGENT_MESSAGE_METADATA.ANSWER_MODE]: answerMode.value,
          ...(metadata ?? {}),
        },
        parts: [{ kind: 'text', text }],
      },
    },
    { signal: controller.signal },
  )
  await runTaskStream(stream, controller, target)
}

const extractFeasibilityClarify = (): string => {
  const fe = steps.find((s) => s.name === DATA_AGENT_GRAPH_NODE.FEASIBILITY_ASSESSMENT)
  if (!fe || !fe.data) return ''
  const raw = fe.data[DATA_AGENT_STATE_KEY_EXECUTION.FEASIBILITY_RESULT]
  if (typeof raw !== 'string') return ''
  if (!raw.includes('需要澄清') && !raw.includes('需求澄清')) return ''
  const idx = raw.indexOf('【需求内容】：')
  if (idx >= 0) {
    const q = raw.slice(idx + '【需求内容】：'.length).trim()
    if (q) return q
  }
  return raw.trim()
}


/* ---------------- HITL 确认气泡辅助：把问题与执行计划带上 ---------------- */
interface PlanStepView { step?: number; tool?: string; instruction?: string; sql?: string; summary?: string }
const lastUserText = (): string => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'user') return messages.value[i]?.text || ''
  }
  return ''
}
const hitlPlanSummary = (): { thought: string; steps: PlanStepView[]; raw: string } => {
  const pl = steps.find((x) => x.name === DATA_AGENT_GRAPH_NODE.PLANNER)
  const rawValue = pl?.data?.[DATA_AGENT_STATE_KEY_PLANNING.PLAN]
  let raw = ''
  if (typeof rawValue === 'string') raw = rawValue
  else if (rawValue && typeof rawValue === 'object') raw = JSON.stringify(rawValue)
  const out = { thought: '', steps: [] as PlanStepView[], raw }
  if (!raw.trim()) return out
  let parsed: any
  try { parsed = JSON.parse(raw) } catch { return out }
  if (!parsed || typeof parsed !== 'object') return out
  out.thought = typeof parsed.thought_process === 'string' ? parsed.thought_process : ''
  if (Array.isArray(parsed.execution_plan)) {
    out.steps = parsed.execution_plan.map((p: any) => ({
      step: p?.step,
      tool: p?.tool_to_use || '',
      instruction: p?.tool_parameters?.instruction || '',
      sql: p?.tool_parameters?.sql_query || '',
      summary: p?.tool_parameters?.summary_and_recommendations || '',
    }))
  }
  return out
}
const hitlPlan = hitlPlanSummary

const finalized = () => {
  // ??????????????????? pending ??????? success
  if (!awaitingConfirmation.value) {
    for (const s of steps) if (s.status === 'pending') s.status = 'success'
  }
  const asst = lastAssistant()
  if (!asst) return
  const report = currentReport()
  if (report) asst.text = report
  else if (!asst.text) {
    const clarify = extractFeasibilityClarify()
    if (clarify) {
      asst.text = '在继续分析前，需要先跟你确认一下：\n' + clarify
    } else {
      const sqlStep = steps.find((s) => s.name === DATA_AGENT_GRAPH_NODE.SQL_EXECUTION)
      asst.text = sqlStep ? '已查询到结果，请在上方处理过程中查看。' : '本次查询已完成。'
    }
  }
  asst.steps = cloneOrderedSteps()
  persistCurrent()
  scrollToBottom()
}

const questionFor = (m: ChatMsg): string => {
  const idx = messages.value.indexOf(m)
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'user') return messages.value[i]?.text || ''
  }
  return ''
}
const feedbackPayload = (question: string, answer: string) => ({
  question,
  answer,
  databaseId: selectedDatabase.value,
  operator: operator.value,
  sessionId: currentSessionId.value || '',
  source: 'chat',
  anonId: 'web-anon',
})

const initFeedback = () => ({ liked: false, likeCount: 0, disliked: false, dislikeCount: 0, promoted: false, demoted: false, sending: false })

const applyFeedback = (m: ChatMsg, data: FeedbackResult, type: 'like' | 'dislike') => {
  if (!m.feedback) return
  if (type === 'like') {
    m.feedback.liked = !data.demoted
    m.feedback.likeCount = Number(data?.likeCount ?? 0)
    m.feedback.promoted = Boolean(data?.promoted)
    m.feedback.demoted = Boolean(data?.demoted)
  } else {
    m.feedback.disliked = true
    m.feedback.dislikeCount = Number(data?.dislikeCount ?? 0)
    m.feedback.promoted = Boolean(data?.promoted)
    m.feedback.demoted = Boolean(data?.demoted)
  }
  const msg = data?.message
  if (msg) {
    if (data.deduped) ElMessage.info(msg)
    else ElMessage[type === 'like' ? 'success' : 'info'](msg)
  } else if (type === 'like') {
    if (data.promoted) ElMessage.success('已被多人肯定，已沉淀为证据')
    else ElMessage.success('已收到你的肯定（' + m.feedback.likeCount + '/' + data.threshold + '）')
  } else {
    if (data.demoted) ElMessage.info('负面反馈已达阈值，已撤回该条证据')
    else ElMessage.info('已收到你的负反馈：' + m.feedback.dislikeCount + '/' + data.demoteThreshold + '）')
  }
  persistCurrent()
}

const likeAnswer = async (m: ChatMsg) => {
  if (m.feedback?.sending) return
  if (m.feedback?.liked) {
    ElMessage.info(m.feedback.promoted ? '该回答已被多人肯定并沉淀为证据' : '你已肯定过这条回答')
    return
  }
  const question = questionFor(m) || m.text.slice(0, 120)
  if (!m.feedback) m.feedback = initFeedback()
  m.feedback.sending = true
  try {
    const data = (await request({
      url: '/qa-feedback/like',
      method: 'POST',
      data: feedbackPayload(question, m.text),
    })) as FeedbackResult
    applyFeedback(m, data, 'like')
  } catch (e) {
    ElMessage.error('点赞失败，请稍后重试')
  } finally {
    if (m.feedback) m.feedback.sending = false
  }
}

const dislikeAnswer = async (m: ChatMsg) => {
  if (m.feedback?.sending) return
  if (m.feedback?.disliked) {
    ElMessage.info(m.feedback.demoted ? '该条证据已被撤回' : '你已给出过负反馈')
    return
  }
  const question = questionFor(m) || m.text.slice(0, 120)
  if (!m.feedback) m.feedback = initFeedback()
  m.feedback.sending = true
  try {
    const data = (await request({
      url: '/qa-feedback/dislike',
      method: 'POST',
      data: feedbackPayload(question, m.text),
    })) as FeedbackResult
    applyFeedback(m, data, 'dislike')
  } catch (e) {
    ElMessage.error('负反馈提交失败，请稍后重试')
  } finally {
    if (m.feedback) m.feedback.sending = false
  }
}
const usePreset = (ex: string) => {
  input.value = ex
  send()
}

const send = async () => {
  const text = input.value?.trim() ?? ''
  if (!text) return
  messages.value.push({ id: crypto.randomUUID(), role: 'user', text, steps: [] })
  const asst: ChatMsg = {
    id: crypto.randomUUID(),
    role: 'assistant',
    text: '',
    steps: [],
    streaming: true,
    interrupted: false,
  }
  messages.value.push(asst)
  input.value = ''
  await streamMessage(text, false, undefined, asst)
}

const submitConfirmation = async (approved: boolean) => {
  const feedback = (confirmationFeedback.value || '').trim()
  if (!approved && !feedback) {
    ElMessage.warning('请先补充需要修改的内容，AI 会据此重新规划')
    return
  }
  awaitingConfirmation.value = false
  await streamMessage(feedback || '确认继续', true, {
    [DATA_AGENT_MESSAGE_METADATA.CONFIRMATION_APPROVED]: approved,
    [DATA_AGENT_MESSAGE_METADATA.CONFIRMATION_FEEDBACK]: feedback || '用户确认继续执行',
  }, lastAssistant())
}


const SQL_STEPS = new Set<string>([DATA_AGENT_GRAPH_NODE.SQL_GENERATION, DATA_AGENT_GRAPH_NODE.SQL_EXECUTION])
const stepSummary = (step: GraphStep) => {
  const label = NODE_LABELS[step.name] || step.name
  if (SQL_STEPS.has(step.name) && !showSqlResult.value) {
    return label + '：（已按设置隐藏 SQL 详情）'
  }
  if (step.data && Object.keys(step.data).length) {
    return label + '：' + safeData(step.data)
  }
  if (step.content) return label + '：' + step.content
  return label
}
const safeData = (d: Record<string, unknown>) => {
  try {
    const s = JSON.stringify(d)
    return s.length > 120 ? s.slice(0, 120) + '…' : s
  } catch {
    return ''
  }
}

onMounted(async () => {
  loadAgentConfig()
  if (!sessions.value.length) {
    sessions.value.push({
      id: crypto.randomUUID(),
      title: '新对话',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      messages: [],
    })
  }
  const first = sessions.value[0]
  if (first) {
    currentSessionId.value = first.id
    messages.value = first.messages.map(cloneMsg)
    // 恢复历史消息时：一旦报告节点已有流式输出内容，就标记为完成，避免口载品网站卡在“报告生成中”
    messages.value.forEach((__m) => {
      if (__m.role !== 'assistant' || __m.streaming || __m.interrupted) return
      const __rs = __m.steps.find((st) => st.name === DATA_AGENT_GRAPH_NODE.REPORT_GENERATION)
      if (__rs && __rs.content && __rs.status !== 'success') __rs.status = 'success'
    })
    currentTaskId.value = first.taskId
    currentContextId.value = first.contextId
  }
  // card.url 来自后端 A2A_PUBLIC_URL（可能是 localhost），生产域名下会失效
  // 统一覆写为同源相对路径：开发走 Vite 代理、Docker 走容器 nginx 代理，均剥离 /api 前缀转发后端
  const cardData = (await api.a2acontroller.agentJson()) as AgentCard
  const sameOriginUrl = '/api/a2a/jsonrpc'
  cardData.url = sameOriginUrl
  if (Array.isArray(cardData.additionalInterfaces)) {
    cardData.additionalInterfaces = cardData.additionalInterfaces.map((i) => ({ ...i, url: sameOriginUrl }))
  }
  client.value = await factory.createFromAgentCard(cardData)
  awaitingConfirmation.value = Boolean(sessions.value.find((x) => x.id === currentSessionId.value)?.hitl)
  nextTick(scrollToBottom)
  // 切到别的模块再切回 / 刷新页面后：上一会话若有未完结的任务，自动重订阅恢复进度，保证“切回来是完好的”
  const active = lastAssistant()
  if (active && (active.streaming || active.interrupted) && currentTaskId.value) {
    resubscribeMessage(active)
  }
})
</script>

<template>
  <div class="chat-page">
    <aside class="session-side">
      <div class="session-side__head">
        <span class="session-side__title">会话记录</span>
        <el-button size="small" type="primary" text :disabled="isRunning" @click="newChat">＋ 新对话</el-button>
      </div>
      <div class="session-side__list">
        <div
          v-for="s in sortedSessions"
          :key="s.id"
          :class="['session-item', { 'is-active': s.id === currentSessionId }]"
          @click="activateSession(s.id)"
        >
          <div v-if="renameId === s.id" class="session-item__rename">
            <el-input
              v-model="renameValue"
              size="small"
              autofocus
              placeholder="会话名称"
              @click.stop
              @keyup.enter="commitRename(s.id)"
              @keydown.esc="renameId = undefined"
              @blur="commitRename(s.id)"
            />
          </div>
          <div v-else class="session-item__main" @click="activateSession(s.id)">
            <div class="session-item__title">{{ s.title }}</div>
            <div class="session-item__time">{{ fmtTime(s.updatedAt) }}</div>
            <div class="session-item__ops">
              <el-button size="small" text class="session-item__btn" :disabled="isRunning" @click.stop="beginRename(s)" title="重命名"><el-icon><EditPen /></el-icon></el-button>
              <el-button size="small" text type="danger" class="session-item__btn" :disabled="isRunning" @click.stop="deleteSession(s.id)" title="删除"><el-icon><Close /></el-icon></el-button>
            </div>
          </div>
        </div>
        <div v-if="!sortedSessions.length" class="session-side__empty">暂无会话，点击上方新建</div>
      </div>
    </aside>

    <section class="chat-main">
      <header class="chat-header">
        <div class="chat-header__title">智能数据问答</div>
        <div class="chat-header__tools">
          <span class="chat-header__db">数据源</span>
          <el-select v-model="selectedDatabase" size="small" style="width: 150px">
            <el-option v-for="d in DATABASE_OPTIONS" :key="d" :label="DATABASE_LABELS[d] || d" :value="d" />
          </el-select>
          <el-button size="small" text :class="['settings-toggle', { 'is-active': settingsOpen }]" @click="settingsOpen = !settingsOpen">
            设置
          </el-button>
        </div>
      </header>

      <div v-show="settingsOpen" class="settings-panel">
        <div class="settings-panel__grid">
          <div class="settings-item">
            <div class="settings-item__head">
              <span class="settings-item__label">人工反馈（HITL）</span>
              <el-switch v-model="humanInTheLoop" size="small" />
            </div>
            <div class="settings-item__desc">开启后每次执行前都会要求人工确认方案，默认关闭</div>
          </div>

          <div class="settings-item">
            <div class="settings-item__head">
              <span class="settings-item__label">显示 SQL 结果</span>
              <el-switch v-model="showSqlResult" size="small" />
            </div>
            <div class="settings-item__desc">在处理过程中直接展示生成的 SQL 与查询结果表格</div>
          </div>

          <div class="settings-item">
            <div class="settings-item__head">
              <span class="settings-item__label">每页数量</span>
              <el-input-number v-model="pageSize" :min="1" :max="200" :step="10" size="small" controls-position="right" />
            </div>
            <div class="settings-item__desc">结果较多时分页展示，每页显示条数（{{ pageSize }}）</div>
          </div>

          <div class="settings-item">
            <div class="settings-item__head">
              <span class="settings-item__label">回答模式</span>
              <el-radio-group v-model="answerMode" size="small">
                <el-radio-button label="concise">简洁</el-radio-button>
                <el-radio-button label="detailed">复杂</el-radio-button>
              </el-radio-group>
            </div>
            <div class="settings-item__desc">简洁：只给核心结论与关键指标；复杂：展开明细、解读与图表建议</div>
          </div>
        </div>

      </div>

      <div v-if="isRunning" class="status-bar">
        <span class="status-bar__dot"></span>
        <span class="status-bar__text">{{ runningText }}</span>
        <span class="status-bar__elapsed">已等待 {{ fmtElapsed(elapsedSec) }}</span>
      </div>

      <main ref="scrollRef" class="chat-body">
        <div v-if="!messages.length" class="chat-body__empty">
          <div class="hp__brand">
            <div class="hp__logo">智</div>
            <div>
              <div class="hp__title">你的数据智能助手</div>
              <div class="hp__subtitle">Data Agent · 电商经营分析</div>
            </div>
          </div>

          <div class="hp__greet">{{ agentConfig.openingMessage }}</div>

          <div class="hp__caps">
            <span class="hp__cap">语义检索</span>
            <span class="hp__cap">多表关联分析</span>
            <span class="hp__cap">指标计算</span>
            <span class="hp__cap">可视化报告</span>
          </div>
        </div>

        <template v-for="m in messages" :key="m.id">
          <div :class="['msg', m.role === 'user' ? 'msg--user' : 'msg--ai']">
            <button
              v-if="showSpin(m)"
              type="button"
              class="msg__spin"
              :class="{ 'is-stalled': m.interrupted && !m.streaming }"
              :disabled="m.streaming"
              :title="m.interrupted && !m.streaming ? '连接中断，点击重新加载任务' : '任务处理中…'"
              @click="onSpinClick(m)"
            >
              <span class="msg__spin-icon"></span>
              <span v-if="m.interrupted && !m.streaming" class="msg__spin-hint">重试</span>
            </button>
            <div :class="['bubble', m.role === 'user' ? 'bubble--user' : 'bubble--ai']">
              <div v-if="m.role === 'user' && m.text" class="bubble__text" v-text="m.text"></div>
              <ReportView v-else-if="m.role === 'assistant' && m.text" :text="m.text" />
              <div v-if="m.role === 'assistant' && m.text && !isRunning" class="bubble__actions">
                <el-button size="small" text class="bubble__act" @click="copyReport(m)">复制</el-button>
                <el-button size="small" text class="bubble__act" @click="downloadReport(m)">下载 .md</el-button>
                <el-button
                  size="small"
                  text
                  :loading="m.feedback?.sending"
                  :class="['bubble__act', m.feedback?.liked ? 'is-liked' : '']"
                  @click="likeAnswer(m)"
                >
                  {{ m.feedback?.liked ? (m.feedback.promoted ? '已沉淀为证据' : '已肯定') : '肯定' }}<template v-if="m.feedback?.likeCount">&nbsp;({{ m.feedback.likeCount }})</template>
                </el-button>
                <el-button
                  size="small"
                  text
                  :loading="m.feedback?.sending"
                  :class="['bubble__act', m.feedback?.disliked ? 'is-disliked' : '']"
                  @click="dislikeAnswer(m)"
                >
                  {{ m.feedback?.disliked ? (m.feedback.demoted ? '↩ 已撤回' : '已负反馈') : '负反馈' }}<template v-if="m.feedback?.dislikeCount">&nbsp;({{ m.feedback?.dislikeCount }})</template>
                </el-button>
              </div>
              <div v-if="m.role === 'assistant' && m.streaming && !m.text" class="bubble__typing">
                <span></span><span></span><span></span>
              </div>
              <div v-if="m.steps.length" class="node-steps">
                <template v-if="isConcise">
                  <div class="flow-compact">
                    <div
                      v-for="s in m.steps"
                      :key="s.id"
                      class="flow-compact__chip"
                      :class="s.status === 'success' ? 'is-done' : 'is-pending'"
                    >
                      <span class="flow-compact__dot"></span>
                      <span class="flow-compact__label">{{ NODE_LABELS[s.name] || s.name }}</span>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <component
                    :is="NODE_CARD_MAP[s.name] || 'div'"
                    v-for="s in m.steps"
                    :key="s.id"
                    :name="s.name"
                    :content="s.content"
                    :data="s.data"
                    :status="s.status"
                    class="node-steps__item"
                  />
                </template>
              </div>
            </div>
          </div>

          <div v-if="m.role === 'assistant' && awaitingConfirmation && m === lastAssistant()" class="msg msg--ai">
            <div class="bubble bubble--ai bubble--confirm">
              <div class="confirm-title">需要你确认后才会继续</div>
              <p class="confirm-desc">AI 已根据你的问题生成下面的<strong>执行方案与统计口径</strong>。请核对是否符合你的真实意图，尤其注意<strong>时间范围、店铺/商品/买家、统计口径</strong>是否正确。</p>
              <p class="confirm-ask">需要你确认的：能否按下列口径执行？如有偏差，请在下方补充修正，AI 会重新规划后再执行。</p>

              <div v-if="lastUserText()" class="confirm-q">
                <div class="confirm-label">用户问题</div>
                <div class="confirm-text">{{ lastUserText() }}</div>
              </div>

              <div v-if="hitlPlan().thought" class="confirm-q">
                <div class="confirm-label">分析思路（口径依据）</div>
                <div class="confirm-text">{{ hitlPlan().thought }}</div>
              </div>
              <div v-if="hitlPlan().steps.length" class="confirm-q">
                <div class="confirm-label">执行步骤</div>
                <ol class="confirm-steps">
                  <li v-for="(st, i) in hitlPlan().steps" :key="i">
                    <div class="confirm-step-head">步骤 {{ st.step ?? i + 1 }} · {{ st.tool || '无工具' }}</div>
                    <div v-if="st.instruction" class="confirm-step-desc">指令：{{ st.instruction }}</div>
                    <div v-if="st.sql" class="confirm-step-sql"><pre>{{ st.sql }}</pre></div>
                    <div v-if="st.summary" class="confirm-step-desc">说明：{{ st.summary }}</div>
                  </li>
                </ol>
              </div>

              <el-input v-model="confirmationFeedback" type="textarea" :rows="3" resize="none" placeholder="如需修改，请在这里补充：如时间改为近 30 天 / 店铺ID 5 / 只统计上架商品 / 按实付金额口径统计…（直接按方案执行可不填）" />
              <div class="confirm-actions">
                <el-button type="primary" size="small" :loading="isRunning" @click="submitConfirmation(true)">按此方案执行</el-button>
                <el-button size="small" :disabled="isRunning" @click="submitConfirmation(false)">需要修改，重新规划</el-button>
              </div>
            </div>
          </div>
</template>
      </main>

      <footer class="composer">
        <div class="composer__examples">
          <span class="composer__examples-label">试试：</span>
          <button
            v-for="ex in agentConfig.presets"
            :key="ex"
            type="button"
            class="composer__example"
            :disabled="isRunning"
            @click="input = ex; send()"
          >{{ ex }}</button>
        </div>
        <div class="composer__bar">
          <el-input
            v-model="input"
            :rows="1"
            type="textarea"
            autosize
            resize="none"
            placeholder="输入你的数据问题，Enter 发送"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="isRunning" @click="send" class="composer__send">发送</el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: row;
  height: calc(100vh - 48px);
  min-width: 1180px;
  background: var(--bg);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-2);
}
.session-side {
  width: 250px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--card);
  color: var(--ink-2);
  border-right: 1px solid var(--line);
  padding: 14px;
  gap: 10px;
}
.session-side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px;
}
.session-side__title { font-weight: 700; color: var(--ink); font-size: 15px; }
.session-side__list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 2px;
}
.session-side__empty { color: var(--ink-3); font-size: 12px; text-align: center; padding-top: 24px; }
.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-ctl);
  cursor: pointer;
  transition: background 0.12s;
}
.session-item:hover { background: #f3f6fc; }
.session-item.is-active { background: var(--brand-weak); }
.session-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 22%;
  bottom: 22%;
  width: 3px;
  border-radius: 999px;
  background: var(--brand);
}
.session-item__main { flex: 1; min-width: 0; }
.session-item__title {
  font-size: 13px;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item.is-active .session-item__title { color: var(--brand); font-weight: 600; }
.session-item__time { font-size: 11px; color: var(--ink-3); margin-top: 3px; }
.session-item__del { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.session-item__ops { display: flex; gap: 2px; flex-shrink: 0; }
.session-item__btn { margin: 0; padding: 3px 4px; opacity: 0; font-size: 13px; color: var(--ink-3); transition: opacity 0.15s; }
.session-item:hover .session-item__btn { opacity: 1; }
.session-item.is-active .session-item__btn { opacity: 1; }
.session-item__btn:hover { color: var(--brand); }
.session-item__rename { flex: 1; min-width: 0; padding: 2px 0; }
.session-item__rename :deep(.el-input__wrapper) { background: #fff; box-shadow: 0 0 0 1px var(--line) inset; }
.bubble__actions { display: flex; gap: 4px; margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--line); }
.bubble__act { margin: 0; padding: 2px 6px; font-size: 12px; color: var(--ink-3); }
.bubble__act:hover { color: var(--brand); }
.bubble__act.is-liked { color: var(--success); font-weight: 600; }
.bubble__act.is-disliked { color: var(--danger); font-weight: 600; }
.session-item:hover .session-item__del { opacity: 1; }
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
}
.chat-header__title { font-weight: 700; color: var(--ink); font-size: 16px; }
.chat-header__tools { display: flex; align-items: center; gap: 10px; }
.chat-header__db { font-size: 13px; color: var(--ink-2); }
.settings-toggle { font-size: 13px; }
.settings-toggle.is-active { color: var(--brand); font-weight: 600; }
.settings-panel {
  padding: 14px 24px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
}
.settings-panel__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}
.settings-item {
  border: 1px solid var(--line);
  border-radius: var(--radius-ctl);
  padding: 10px 14px;
  background: #fff;
  min-width: 0;
}
.settings-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.settings-item__label { flex: 1; min-width: 0; font-size: 13px; font-weight: 600; color: var(--ink); line-height: 1.5; }
.settings-item__head :deep(.el-switch) { flex-shrink: 0; }
.settings-item__desc { margin-top: 4px; font-size: 12px; color: var(--ink-3); line-height: 1.6; word-break: break-word; }
.settings-presets { margin-top: 12px; }
.settings-presets__label { font-size: 13px; font-weight: 600; color: var(--ink); margin-right: 8px; }
.settings-presets__tags { display: flex; flex-wrap: wrap; gap: 6px; }
.preset-tag { cursor: pointer; }
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 24px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  border-bottom: 1px solid var(--line);
}
.status-bar__text { flex: 1; }
.status-bar__elapsed {
  margin-left: 8px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--line);
  color: #64748b;
  border-radius: 999px;
  padding: 1px 10px;
  font-size: 12px;
}
.status-bar__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
  animation: blink 1.1s infinite;
}
.chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 首页：开场白 + 预设问题 + 能力标签 */
.chat-body__empty { margin: 48px auto 0; max-width: 600px; text-align: center; }
.hp__brand { display: flex; align-items: center; justify-content: center; gap: 12px; }
.hp__logo {
  width: 46px; height: 46px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; background: var(--brand-gradient); color: #fff; box-shadow: 0 4px 16px -4px rgba(37,99,235,.4);
}
.hp__title { font-size: 20px; font-weight: 700; color: var(--ink); text-align: left; }
.hp__subtitle { font-size: 12px; color: var(--ink-3); text-align: left; letter-spacing: .04em; }
.hp__greet {
  margin: 22px auto 0; max-width: 560px; text-align: left; line-height: 1.9; color: var(--ink-2);
  background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-card);
  padding: 16px 20px; font-size: 14px; box-shadow: var(--shadow-1);
}
.hp__caps { margin-top: 30px; display: flex; justify-content: center; flex-wrap: wrap; gap: 8px; }
.hp__cap {
  font-size: 12px; color: var(--ink-2); background: #f8fafc; border: 1px solid var(--line);
  border-radius: 999px; padding: 6px 14px;
}
.chat-body__examples { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.example-tag { cursor: pointer; font-size: 13px; }
.msg { display: flex; }
.msg--user { justify-content: flex-end; }
.msg--ai { justify-content: flex-start; }
.msg__spin {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  margin-top: 14px;
  padding: 0;
  background: none;
  border: none;
  cursor: pointer;
}
.msg__spin:disabled { cursor: default; }
.msg--ai .msg__spin { margin-right: 8px; }
.msg__spin-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid rgba(148, 163, 184, 0.45);
  border-top-color: var(--brand);
  animation: msg-spin 0.9s linear infinite;
  box-sizing: border-box;
}
.msg__spin.is-stalled .msg__spin-icon {
  border-color: rgba(220, 38, 38, 0.35);
  border-top-color: #dc2626;
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.10);
}
.msg__spin-hint { font-size: 11px; color: #dc2626; line-height: 1; }
@keyframes msg-spin { to { transform: rotate(360deg); } }
.bubble {
  max-width: 82%;
  padding: 14px 16px;
  border-radius: var(--radius-card);
  line-height: 1.75;
  font-size: 14px;
  white-space: normal;
  word-break: break-word;
}
.bubble__text { white-space: pre-wrap; }
.bubble--user { background: var(--brand-gradient); color: #fff; border-bottom-right-radius: 4px; }
.bubble--ai { background: var(--card); border: 1px solid var(--line); border-bottom-left-radius: 4px; color: var(--ink); box-shadow: var(--shadow-1); }
.bubble__typing { display: flex; gap: 5px; padding: 6px 2px; }
.bubble__typing span { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; animation: blink 1.2s infinite; }
.bubble__typing span:nth-child(2) { animation-delay: 0.2s; }
.bubble__typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.2; } 40% { opacity: 1; } }
.node-steps { margin-top: 10px; border-top: 1px dashed var(--line); padding-top: 8px; display: flex; flex-direction: column; gap: 10px; }
.steps { margin-top: 10px; border-top: 1px dashed var(--line); padding-top: 8px; }
.steps__summary { cursor: pointer; color: var(--ink-2); font-size: 12px; user-select: none; }
.steps__live { color: var(--brand); margin-left: 6px; }
.steps__item { padding: 6px 0; border-bottom: 1px solid #f1f5f9; }
.steps__row { display: flex; align-items: center; gap: 8px; }
.steps__status { font-size: 11px; color: var(--ink-3); }
.steps__content { margin-top: 4px; font-size: 12px; color: var(--ink-2); white-space: pre-wrap; }
.bubble--confirm { border-left: 4px solid var(--brand); }
.confirm-title { font-weight: 700; color: #0f172a; }
.confirm-desc { margin: 6px 0 10px; color: #64748b; }
.bubble--confirm { max-width: 86%; }
.confirm-desc { font-size: 13px; color: #64748b; line-height: 1.8; }
.confirm-q { margin-top: 10px; border: 1px solid var(--line); border-radius: var(--radius-ctl); padding: 10px 12px; background: #f8fafc; }
.confirm-label { font-size: 11px; font-weight: 700; letter-spacing: .05em; color: var(--brand); margin-bottom: 4px; }
.confirm-text { font-size: 13px; color: var(--ink); line-height: 1.6; white-space: pre-wrap; }
.confirm-ask { margin: 0 0 4px; font-size: 13px; font-weight: 600; color: #64748b; line-height: 1.7; }
.confirm-steps { margin: 0; padding-left: 20px; }
.confirm-steps li { margin: 6px 0; }
.confirm-step-head { font-size: 13px; font-weight: 600; color: var(--ink-2); }
.confirm-step-tool { font-size: 13px; font-weight: 600; color: var(--ink-2); }
.confirm-step-desc { display: block; font-size: 12px; color: var(--ink-2); margin-top: 2px; }
.confirm-step-sql { margin-top: 4px; }
.confirm-step-sql pre { margin: 0; padding: 6px 8px; background: #0f172a; color: #bfdbfe; border-radius: 6px; font-size: 11px; line-height: 1.5; overflow: auto; max-height: 160px; white-space: pre-wrap; word-break: break-all; }
.bubble--confirm .el-textarea { margin: 6px 8px 0 0; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 10px; }
.composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
  padding: 12px 24px 18px;
  background: var(--card);
  border-top: 1px solid var(--line);
}
.composer__examples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.composer__examples-label { font-size: 12px; color: var(--ink-3); flex-shrink: 0; }
.composer__example {
  cursor: pointer;
  font-size: 12px;
  color: var(--ink-2);
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 5px 12px;
  line-height: 1.5;
  transition: color .15s, border-color .15s, background .15s;
}
.composer__example:hover:not(:disabled) {
  color: var(--brand);
  border-color: var(--brand-border);
  background: var(--brand-soft, #eff6ff);
}
.composer__example:disabled { opacity: .55; cursor: not-allowed; }
.composer__bar {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.composer .el-textarea { flex: 1; }
.composer :deep(.el-textarea__inner) {
  border-radius: var(--radius-ctl);
  box-shadow: 0 0 0 1px var(--line) inset;
  padding: 10px 14px;
  font-size: 14px;
  transition: box-shadow 0.15s;
}
.composer :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px var(--brand) inset; }
.composer__send {
  height: 38px;
  padding: 0 24px;
  border: none;
  background: var(--brand-gradient);
  font-weight: 600;
}
.composer__send:hover { opacity: 0.92; }
/* 简洁模式：紧凑流程 */
.flow-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.flow-compact__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: #f8fafc;
  color: var(--ink-3);
}
.flow-compact__chip.is-done {
  border-color: var(--brand-border);
  background: var(--brand-weak);
  color: var(--brand);
  font-weight: 600;
}
.flow-compact__chip.is-pending {
  border-color: var(--line);
  background: #f8fafc;
  color: var(--ink-3);
}
.flow-compact__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
}
.flow-compact__chip.is-done .flow-compact__dot { background: var(--brand); }
.flow-compact__chip.is-pending .flow-compact__dot { background: #cbd5e1; }

.settings-item--wide { grid-column: 1 / -1; }
.settings-item--wide .el-textarea { margin-top: 6px; }
.settings-presets__add { display: flex; gap: 8px; margin-top: 8px; }
.settings-presets__tags { margin-top: 10px; }

</style>
