<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const rows = ref<any[]>([])
const loading = ref(false)
const renameDialog = ref(false)
const editing = reactive<{ from: string; to: string }>({ from: '', to: '' })

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/api/kb/tags')
    rows.value = res || []
  } finally {
    loading.value = false
  }
}

function openRename(tag: string) {
  editing.from = tag
  editing.to = ''
  renameDialog.value = true
}

async function rename() {
  if (!editing.from || !editing.to) return
  const res: any = await http.put('/api/kb/tags/rename', { from: editing.from, to: editing.to })
  ElMessage.success('已重命名，共更新 ' + (res?.updated ?? 0) + ' 条')
  renameDialog.value = false
  load()
}

async function removeTag(tag: string) {
  await ElMessageBox.confirm(`确定删除标签「${tag}」吗？将从所有条目中移除。`, '提示', { type: 'warning' })
  const res: any = await http.delete('/api/kb/tags', { data: { tag } })
  ElMessage.success('已删除，共更新 ' + (res?.updated ?? 0) + ' 条')
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:700">标签管理</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </template>
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="标签来自知识库条目（逗号分隔）。可统一重命名或删除，删除/重命名会同步更新所有关联条目。" />

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="tag" label="标签" />
      <el-table-column prop="count" label="关联条目数" width="140" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openRename(row.tag)">重命名</el-button>
          <el-button size="small" type="danger" @click="removeTag(row.tag)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="renameDialog" title="重命名标签" width="480px">
      <el-form label-width="80px">
        <el-form-item label="原标签"><el-input v-model="editing.from" disabled /></el-form-item>
        <el-form-item label="新标签"><el-input v-model="editing.to" placeholder="输入新标签名" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" @click="rename">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>