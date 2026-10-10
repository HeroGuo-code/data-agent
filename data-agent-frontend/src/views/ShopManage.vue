<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'shop_id', label: '店铺ID', width: 90 },
  { field: 'shop_name', label: '店铺名称', width: 200, editable: true },
  { field: 'owner_id', label: '店主ID', width: 100, editable: true, formType: 'number' },
  { field: 'category', label: '主营类目', width: 110, editable: true, formType: 'select', options: ['服装','数码','食品','家居','美妆','运动'] },
  { field: 'created_at', label: '创建时间', width: 140, editable: true },
]
const searchFields = [
  { field: 'shopName', label: '店铺名称', type: 'text' },
  { field: 'category', label: '主营类目', type: 'select', options: ['服装','数码','食品','家居','美妆','运动'] },
]
</script>
<template>
  <MallCrud title="店铺管理" api-path="/api/mall/shops" :columns="columns" :search-fields="searchFields" id-field="shop_id" />
</template>
