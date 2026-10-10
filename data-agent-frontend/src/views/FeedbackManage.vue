<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const rows = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/api/qa-feedback/items')
    rows.value = res || []
  } finally {
    loading.value = false
  }
}

function statusTag(r: any): { type: 'success' | 'danger' | 'info'; text: string } {
  if (r.promoted) return { type: 'success', text: '已沉淀为证据' }
  if (r.demoted) return { type: 'danger', text: '已撤回' }
  return { type: 'info', text: '待沉淀' }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:700">反馈闭环</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </template>
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="点赞/负反馈闭环：不同身份达到点赞阈值会自动沉淀为问答证据，负反馈达到阈值会自动撤回，保证知识库只保留被认可的答案。" />

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="answer" label="回答（来源）" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTag(row).type" size="small">{{ statusTag(row).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="like_count" label="点赞" width="90" align="center" />
      <el-table-column prop="dislike_count" label="负反馈" width="90" align="center" />
      <el-table-column label="来源" width="150">
        <template #default="{ row }">
          <div>{{ row.source || '聊天' }}</div>
          <div style="font-size:12px;color:var(--ink-3)">{{ row.operator || '-' }}<template v-if="row.session_id">&nbsp;·&nbsp;{{ String(row.session_id).slice(0, 8) }}</template></div>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="160" />
    </el-table>
  </el-card>
</template>