<template>
  <div>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:700">业务表模块（ecommerce_ops）</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <p style="color:#64748b;margin:0 0 12px">
        电商经营问答所覆盖的业务表，每张表的字段已向量化入库（<code>pgvector</code>），
        在 <strong>Schema 召回 / 表关系 / SQL 生成</strong> 阶段被语义检索。
      </p>

      <el-row :gutter="14">
        <el-col v-for="t in tables" :key="t.name" :md="12" :lg="8">
          <el-card shadow="hover" class="biz-card">
            <div class="biz-head">
              <el-icon style="font-size:20px;color:var(--brand)"><component :is="tableIcon(t.name)" /></el-icon>
              <b class="biz-title">{{ t.name }}</b>
              <el-tag size="small" type="info">字段 {{ t.columns.length }}</el-tag>
            </div>
            <p class="biz-desc">{{ t.description }}</p>
            <el-divider style="margin:8px 0" />
            <table class="mini-col">
              <tr v-for="c in t.columns" :key="c.name">
                <td class="mini-col__name">{{ c.name }}<el-icon v-if="c.is_primary_key" class="pk"><Key /></el-icon></td>
                <td class="mini-col__type">{{ c.type }}</td>
                <td class="mini-col__desc">{{ c.description }}</td>
              </tr>
            </table>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import { Key, Goods, User, ShoppingCart, Box, Van, CreditCard, Sell, DocumentCopy, Present, Tickets } from '@element-plus/icons-vue'

const loading = ref(false)
const tables = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    tables.value = (await http.get('/api/asset/tables')) || []
  } finally {
    loading.value = false
  }
}

const iconMap: Record<string, any> = {
  shops: Sell, buyers: User, users: User, products: Goods, orders: ShoppingCart,
  order_items: DocumentCopy, inventory: Box, refunds: Van, activities: Present, activity_records: Tickets,
}
function tableIcon(name: string) { return iconMap[name] || Goods }
onMounted(load)
</script>

<style scoped>
.biz-card { margin-bottom: 14px; }
.biz-head { display: flex; align-items: center; gap: 8px; }
.biz-title { font-size: 15px; color: var(--ink); }
.biz-desc { font-size: 12.5px; line-height: 1.7; color: var(--ink-2); margin: 8px 0 4px; min-height: 34px; }
.mini-col { width: 100%; border-collapse: collapse; font-size: 12px; }
.mini-col td { padding: 3px 4px; border-bottom: 1px dashed #f1f5f9; vertical-align: top; }
.mini-col__name { white-space: nowrap; font-weight: 600; color: #0f172a; }
.mini-col__type { color: var(--ink-2); width: 60px; white-space: nowrap; }
.mini-col__desc { color: var(--ink-3); }
.pk { color: var(--brand); margin-left: 3px; vertical-align: -2px; }
</style>

