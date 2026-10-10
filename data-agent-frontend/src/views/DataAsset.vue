<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const router = useRouter()
const loading = ref(false)
const summary = ref<any>({})

const modules = [
  { key: 'business', path: '/asset/business', icon: 'Collection', title: '业务表', desc: '电商十张业务表的表结构、字段与主键', color: '#2563eb' },
  { key: 'schema', path: '/asset/schema', icon: 'Grid', title: 'Schema 字段', desc: '全部字段元数据，供 Schema 召回语义检索', color: '#3b82f6' },
  { key: 'relation', path: '/asset/relation', icon: 'Connection', title: '表关系', desc: '外键关系，供表关系节点的 JOIN 推理', color: '#1d4ed8' },
  { key: 'knowledge', path: '/knowledge', icon: 'Reading', title: '知识库', desc: '问答示例 / 业务词表 / 指标口径 / 权限规则 / 运营知识', color: '#60a5fa' },
]

async function load() {
  loading.value = true
  try { summary.value = (await http.get('/api/asset/summary')) || {} } finally { loading.value = false }
}

async function reindex() {
  loading.value = true
  try {
    const res: any = await http.post('/api/asset/reindex')
    ElMessage.success(res?.message || '重新入库召回完成')
  } catch (e: any) {
    ElMessage.error(e?.message || e?.msg || '重建失败')
  } finally {
    loading.value = false
  }
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:700">数据资产中心（数据问答元数据总览）</span>
          <el-button type="primary" :loading="loading" @click="reindex">重新入库召回</el-button>
        </div>
      </template>

      <el-row :gutter="12" style="margin-bottom:16px">
        <el-col :span="4"><el-statistic title="业务表" :value="summary.tableCount ?? 0" /></el-col>
        <el-col :span="4"><el-statistic title="字段" :value="summary.columnCount ?? 0" /></el-col>
        <el-col :span="4"><el-statistic title="表关系" :value="summary.relationCount ?? 0" /></el-col>
        <el-col :span="4"><el-statistic title="问答证据" :value="summary.evidenceCount ?? 0" /></el-col>
        <el-col :span="4"><el-statistic title="业务词表" :value="summary.glossaryCount ?? 0" /></el-col>
        <el-col :span="4"><el-statistic title="数据源" :value="summary.databaseId || 'ecommerce_ops'" /></el-col>
      </el-row>

      <el-row :gutter="14">
        <el-col v-for="m in modules" :key="m.key" :span="6">
          <el-card shadow="hover" class="mod-card" @click="router.push(m.path)">
            <div class="mod-icon" :style="{ background: m.color + '1a' }"><el-icon :size="26" :color="m.color"><component :is="m.icon" /></el-icon></div>
            <h3>{{ m.title }}</h3>
            <p>{{ m.desc }}</p>
            <span class="mod-go" :style="{ color: m.color }">进入模块 →</span>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.mod-card { cursor: pointer; text-align: center; padding: 6px 8px; transition: transform .15s, box-shadow .15s; }
.mod-card:hover { transform: translateY(-3px); box-shadow: 0 10px 24px rgba(15,23,42,.08); }
.mod-icon { width: 56px; height: 56px; display: flex; align-items: center; justify-content: center; border-radius: 14px; margin: 0 auto 10px; }
.mod-card h3 { margin: 6px 0; font-size: 15px; color: var(--ink); }
.mod-card p { font-size: 12px; color: var(--ink-2); line-height: 1.6; min-height: 40px; margin: 0 0 8px; }
.mod-go { font-size: 12px; font-weight: 600; }
</style>
