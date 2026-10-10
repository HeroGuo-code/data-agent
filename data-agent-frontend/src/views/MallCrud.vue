<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

interface Col {
  field: string
  label: string
  width?: number
  editable?: boolean
  formType?: string
  options?: string[]
}
interface SearchField {
  field: string
  label: string
  type?: string
  options?: string[]
}

const props = defineProps<{
  title: string
  apiPath: string
  columns: Col[]
  searchFields?: SearchField[]
  idField?: string
}>()

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editing = ref<Record<string, any>>({})
const isEdit = ref(false)

const query = reactive<any>({ page: 1, size: 10 })
const formRef = ref()

function buildParams() {
  const p: Record<string, any> = { page: query.page, size: query.size }
  for (const f of props.searchFields || []) {
    if (query[f.field] !== undefined && query[f.field] !== null && query[f.field] !== '') {
      p[f.field] = query[f.field]
    }
  }
  return p
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get(props.apiPath, { params: buildParams() })
    rows.value = res.records || []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function resetPage() {
  query.page = 1
  load()
}

function openCreate() {
  isEdit.value = false
  editing.value = {}
  dialogVisible.value = true
}
function openEdit(row: any) {
  isEdit.value = true
  editing.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  const id = editing.value[props.idField || 'id']
  try {
    if (isEdit.value) {
      await http.put(`${props.apiPath}/${id}`, editing.value)
    } else {
      await http.post(props.apiPath, editing.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.msg || '保存失败')
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除这条记录吗？', '提示', { type: 'warning' })
  await http.delete(`${props.apiPath}/${row[props.idField || 'id']}`)
  ElMessage.success('删除成功')
  load()
}

const editableCols = () => props.columns.filter((c) => c.editable)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span style="font-weight:700">{{ title }}</span>
        <el-button type="primary" @click="openCreate">新增</el-button>
      </div>
    </template>

    <el-form inline style="margin-bottom:12px" @submit.prevent>
      <template v-for="f in searchFields || []" :key="f.field">
        <el-form-item :label="f.label">
          <el-select v-if="f.type === 'select'" v-model="query[f.field]" clearable :placeholder="'全部'">
            <el-option v-for="o in f.options" :key="o" :label="o" :value="o" />
          </el-select>
          <el-input v-else v-model="query[f.field]" :placeholder="'输入' + f.label" clearable />
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" @click="resetPage">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column v-for="col in columns" :key="col.field" :prop="col.field" :label="col.label" :width="col.width">
        <template #default="{ row }">
          <el-tag v-if="col.formType === 'select'" size="small" type="info">{{ row[col.field] }}</el-tag>
          <span v-else>{{ row[col.field] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:14px;justify-content:flex-end"
      layout="total, prev, pager, next, sizes"
      :total="total"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :page-sizes="[10, 20, 50]"
      @current-change="load"
      @size-change="resetPage"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '新增'" width="520px">
      <el-form ref="formRef" label-width="110px">
        <el-form-item v-for="col in editableCols()" :key="col.field" :label="col.label">
          <el-select v-if="col.formType === 'select'" v-model="editing[col.field]" :placeholder="'请选择'">
            <el-option v-for="o in col.options" :key="o" :label="o" :value="o" />
          </el-select>
          <el-input-number v-else-if="col.formType === 'number'" v-model="editing[col.field]" :controls="false" style="width:100%" />
          <el-input v-else v-model="editing[col.field]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>