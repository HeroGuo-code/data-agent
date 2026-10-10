<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import * as echarts from 'echarts'

const props = defineProps<{ text: string }>()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedHtml = ref('')
const chartMap = new Map<string, echarts.ECharts>()

function hashStr(s: string): string {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0).toString(36)
}

function renderMarkdown(src: string) {
  const re = /```echarts\s*\n([\s\S]*?)```/g
  const wanted = new Map<string, unknown>()
  let html = ''
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    html += md.render(src.slice(last, m.index))
    const raw = (m[1] ?? '').trim()
    let option: unknown = null
    try {
      option = JSON.parse(raw)
    } catch {
      /* incomplete while streaming, render as plain code */
    }
    const id = 'chart-' + hashStr(raw)
    if (option && typeof option === 'object') {
      wanted.set(id, option)
      html += `<div class="report-chart" data-chart="${id}"></div>`
    } else {
      html += `<pre class="report-invalid-chart">${md.utils.escapeHtml(raw)}</pre>`
    }
    last = m.index + m[0].length
  }
  html += md.render(src.slice(last))
  renderedHtml.value = html

  chartMap.forEach((inst, id) => {
    if (!wanted.has(id)) {
      inst.dispose()
      chartMap.delete(id)
    }
  })

  nextTick(() => {
    wanted.forEach((option, id) => {
      if (chartMap.has(id)) return
      const el = document.querySelector(`[data-chart="${id}"]`) as HTMLElement | null
      if (!el) return
      try {
        const inst = echarts.init(el)
        inst.setOption(option as any)
        chartMap.set(id, inst)
      } catch {
        /* ignore invalid option */
      }
    })
  })
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(
  () => props.text,
  (v) => {
    clearTimeout(timer)
    timer = setTimeout(() => renderMarkdown(v || ''), 150)
  },
  { immediate: true },
)

function onResize() {
  chartMap.forEach((inst) => inst.resize())
}
window.addEventListener('resize', onResize)

onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('resize', onResize)
  chartMap.forEach((inst) => inst.dispose())
  chartMap.clear()
})
</script>

<template>
  <div class="report" v-html="renderedHtml"></div>
</template>

<style scoped>
.report {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ink);
  word-break: break-word;
}
.report :deep(h1),
.report :deep(h2),
.report :deep(h3),
.report :deep(h4) {
  margin: 14px 0 8px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
}
.report :deep(h1) { font-size: 18px; }
.report :deep(h2) { font-size: 16px; }
.report :deep(h3) { font-size: 15px; }
.report :deep(h4) { font-size: 14px; }
.report :deep(p) { margin: 8px 0; }
.report :deep(ul),
.report :deep(ol) { margin: 8px 0; padding-left: 22px; }
.report :deep(li) { margin: 3px 0; }
.report :deep(strong) { font-weight: 700; color: #0f172a; }
.report :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}
.report :deep(th),
.report :deep(td) {
  border: 1px solid var(--line);
  padding: 7px 10px;
  text-align: left;
}
.report :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #0f172a;
}
.report :deep(tr:nth-child(even) td) { background: #fafbfd; }
.report :deep(code) {
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--brand);
}
.report :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 10px 12px;
  border-radius: 8px;
  overflow: auto;
  font-size: 12px;
}
.report :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.report-chart {
  width: 100%;
  height: 320px;
  margin: 12px 0;
}
.report-invalid-chart {
  background: #f8fafc !important;
  color: #64748b;
  border: 1px dashed #cbd5e1;
  white-space: pre-wrap;
}
.report :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  margin: 8px 0;
  padding: 2px 12px;
  color: #64748b;
  background: #f8fafc;
  border-radius: 4px;
}
.report :deep(hr) { border: none; border-top: 1px dashed #e2e8f0; margin: 14px 0; }
</style>