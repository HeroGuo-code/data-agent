<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'inventory_id', label: '库存ID', width: 90 },
  { field: 'product_id', label: '商品ID', width: 90 },
  { field: 'shop_id', label: '店铺ID', width: 90 },
  { field: 'quantity', label: '库存数量', width: 110, editable: true, formType: 'number' },
  { field: 'updated_at', label: '更新时间', width: 130, editable: true },
]
const searchFields = [
  { field: 'shopId', label: '店铺ID', type: 'text' },
  { field: 'minQty', label: '库存≤', type: 'text' },
]
</script>
<template>
  <MallCrud title="库存管理" api-path="/api/mall/inventory" :columns="columns" :search-fields="searchFields" id-field="inventory_id" />
</template>