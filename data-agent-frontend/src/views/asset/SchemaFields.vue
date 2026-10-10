<template>
  <el-card shadow="never" v-loading="loading">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:700">Schema 字段模块</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </template>

    <p style="color:#64748b;margin:0 0 12px">
      <strong>Schema 召回</strong> 节点按语义检索这些字段元数据，用于把自然语言问题映射到具体的表与列。
    </p>

    <div style="display:flex;gap:10px;margin-bottom:12px;flex-wrap:wrap">
      <el-select v-model="tableFilter" placeholder="按表过滤" clearable style="width:200px" @change="applyFilter">
        <el-option v-for="t in tableNames" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input v-model="keyword" placeholder="字段名 / 说明 关键词" clearable style="width:260px" @input="applyFilter" />
      <el-tag type="info">共 {{ allColumns.length }} 个字段 / {{ tables.length }} 张表</el-tag>
    </div>

    <el-table :data="filtered" border stripe size="small" max-height="600">
      <el-table-column prop="table" label="所属表" width="160">
        <template #default="{ row }"><el-tag size="small">{{ row.table }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="name" label="字段" width="180">
        <template #default="{ row }">
          {{ row.name }}<el-icon v-if="row.is_primary_key" style="color:var(--brand);margin-left:4px" ><Key /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="110" />
      <el-table-column prop="description" label="说明" min-width="260" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '@/api/http'
import { Key } from '@element-plus/icons-vue'

const loading = ref(false)
const tables = ref<any[]>([])
const allColumns = ref<any[]>([])
const tableFilter = ref('')
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    tables.value = (await http.get('/api/asset/tables')) || []
    allColumns.value = tables.value.flatMap((t: any) =>
      (t.columns || []).map((c: any) => ({ ...c, table: t.name })))
  } finally {
    loading.value = false
  }
}

const tableNames = computed(() => tables.value.map((t: any) => t.name))

const filtered = computed(() => {
  let cols = allColumns.value
  if (tableFilter.value) cols = cols.filter((c) => c.table === tableFilter.value)
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    cols = cols.filter((c) => (c.name || '').toLowerCase().includes(k) || (c.description || '').toLowerCase().includes(k))
  }
  return cols
})

function applyFilter() { /* reactive */ }
onMounted(load)
</script>
