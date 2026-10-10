<template>
  <div style="padding:16px">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:700">智能助手配置</span>
          <div>
            <el-button size="small" @click="load">刷新</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="save">保存并生效</el-button>
          </div>
        </div>
      </template>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="配置助手在空会话/问候时的开场白、闲聊回复使用的人设提示词，以及首页与聊天中推荐的预设问题。保存后即时生效。" />

      <el-form label-position="top">
        <el-card shadow="never" style="margin-bottom:16px">
          <template #header><b>开场白</b></template>
          <el-form-item label="新会话空状态展示，以及用户向你问候（如“你好”）时提示词输出节点回复的内容">
            <el-input v-model="openingMessage" type="textarea" :rows="4" resize="none" placeholder="请输入开场白" />
          </el-form-item>
        </el-card>

        <el-card shadow="never" style="margin-bottom:16px">
          <template #header><b>提示词设计（人设 / 系统提示词）</b></template>
          <el-form-item label="闲聊与无关指令直接交给大模型回复时使用的人设提示词">
            <el-input v-model="personaPrompt" type="textarea" :rows="6" resize="none" placeholder="请输入人设提示词" />
          </el-form-item>
        </el-card>

        <el-card shadow="never">
          <template #header><b>预设问题</b></template>
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <el-input v-model="newPreset" size="small" placeholder="输入预设问题后回车添加" clearable @keyup.enter="addPreset" />
            <el-button size="small" type="primary" @click="addPreset">添加</el-button>
          </div>
          <el-empty v-if="!presets.length" description="暂无预设问题" :image-size="60" />
          <el-table v-else :data="presetsTable" size="small" border>
            <el-table-column label="#" type="index" width="50" />
            <el-table-column label="预设问题" prop="text" min-width="300" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button size="small" type="danger" text @click="removePreset(row.text)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const openingMessage = ref('')
const personaPrompt = ref('')
const presets = ref<string[]>([])
const newPreset = ref('')
const saving = ref(false)

const presetsTable = computed(() => presets.value.map((t) => ({ text: t })))

const DEFAULT = {
  openingMessage: '你好呀，我是标准化智能助手小鑫，如果你有电商经营数据相关的问题，都可以随时告诉我哦！',
  personaPrompt: '你是标准化智能助手「小鑫」，负责帮助用户查询和分析电商经营数据。请用自然、友好、简洁的语气回复用户，不要编造数据。',
  presets: ['查询本季度销售额最高的三个店铺', '买家ID为xxx的历史订单和消费汇总', '统计6月各商品类别的销售件数与GMV'],
}

async function load() {
  try {
    const res: any = await http.get('/api/agent-config')
    openingMessage.value = res?.openingMessage ?? DEFAULT.openingMessage
    personaPrompt.value = res?.personaPrompt ?? DEFAULT.personaPrompt
    presets.value = Array.isArray(res?.presets) && res.presets.length ? res.presets : [...DEFAULT.presets]
  } catch {
    openingMessage.value = DEFAULT.openingMessage
    personaPrompt.value = DEFAULT.personaPrompt
    presets.value = [...DEFAULT.presets]
  }
}

async function save() {
  saving.value = true
  try {
    await http.put('/api/agent-config', {
      openingMessage: openingMessage.value,
      personaPrompt: personaPrompt.value,
      presets: presets.value,
    })
    ElMessage.success('已保存并生效')
  } finally {
    saving.value = false
  }
}

function addPreset() {
  const t = newPreset.value.trim()
  if (!t) return
  if (!presets.value.includes(t)) presets.value.push(t)
  newPreset.value = ''
}

function removePreset(text: string) {
  presets.value = presets.value.filter((x) => x !== text)
}

onMounted(load)
</script>