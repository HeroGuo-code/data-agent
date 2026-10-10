<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const modules = ref<string[]>([])
const activeModule = ref('问答示例')
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editing = ref<Record<string, any>>({})
const query = reactive({ page: 1, size: 10 })

async function loadModules() {
  const res: any = await http.get('/api/kb/modules')
  modules.value = res || []
  const first = modules.value[0]
  if (first) activeModule.value = first
}

async function load() {
  loading.value = true
  try {
    const params: any = { page: query.page, size: query.size, module: activeModule.value }
    if (keyword.value) params.keyword = keyword.value
    const res: any = await http.get('/api/kb/items', { params })
    rows.value = res.records || []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

function onModuleChange() {
  query.page = 1
  load()
}

function search() {
  query.page = 1
  load()
}

function openCreate() {
  isEdit.value = false
  editing.value = { module: activeModule.value }
  dialogVisible.value = true
}
function openEdit(row: any) {
  isEdit.value = true
  editing.value = { ...row }
  dialogVisible.value = true
}
async function save() {
  try {
    if (isEdit.value) await http.put(`/api/kb/items/${editing.value.id}`, editing.value)
    else await http.post('/api/kb/items', editing.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.msg || '保存失败')
  }
}
async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该条知识吗？', '提示', { type: 'warning' })
  await http.delete(`/api/kb/items/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  await loadModules()
  await load()
})
</script>

<template>
  <el-card shadow="never">
    <template #header><span style="font-weight:700">知识库管理（预分模块）</span></template>
    <el-tabs v-model="activeModule" @tab-change="onModuleChange">
      <el-tab-pane v-for="m in modules" :key="m" :label="m" :name="m" />
    </el-tabs>

    <div style="display:flex;gap:10px;margin-bottom:12px">
      <el-input v-model="keyword" placeholder="标题/内容/标签 关键词" clearable style="width:280px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button type="primary" @click="openCreate">新增知识</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="title" label="标题" width="220" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="tags" label="标签" width="160" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:14px;justify-content:flex-end"
      layout="total, prev, pager, next"
      :total="total"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识' : '新增知识'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="模块">
          <el-select v-model="editing.module" style="width:100%">
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="editing.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="editing.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="editing.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>