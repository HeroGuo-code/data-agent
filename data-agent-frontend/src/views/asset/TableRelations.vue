<template>
  <div>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span style="font-weight:700">表关系模块（外键 / JOIN 推理）</span>
      </template>
      <p style="color:#64748b;margin:0 0 12px">
        <strong>表关系</strong> 节点根据这些外键做 JOIN 推理，决定多表查询的关联路径。
      </p>
      <el-table :data="relations" border stripe size="small">
        <el-table-column prop="src_table" label="源表" width="180">
          <template #default="{ row }"><el-tag size="small" type="primary">{{ row.src_table }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="src_col" label="源列" width="160" />
        <el-table-column label="关联" width="70" align="center">
          <template #default><span style="color:var(--brand);font-weight:800">⟶</span></template>
        </el-table-column>
        <el-table-column prop="tgt_table" label="目标表" width="180">
          <template #default="{ row }"><el-tag size="small" type="success">{{ row.tgt_table }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="tgt_col" label="目标列" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const loading = ref(false)
const relations = ref<any[]>([])
async function load() {
  loading.value = true
  try { relations.value = (await http.get('/api/asset/relations')) || [] } finally { loading.value = false }
}
onMounted(load)
</script>
