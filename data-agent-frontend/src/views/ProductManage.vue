<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'product_id', label: '商品ID', width: 90 },
  { field: 'shop_id', label: '店铺ID', width: 90, editable: true, formType: 'number' },
  { field: 'name', label: '商品名称', width: 200, editable: true },
  { field: 'category', label: '类目', width: 100, editable: true, formType: 'select', options: ['服装','数码','食品','家居','美妆','运动'] },
  { field: 'price', label: '售价', width: 100, editable: true, formType: 'number' },
  { field: 'cost', label: '成本', width: 100, editable: true, formType: 'number' },
  { field: 'status', label: '状态', width: 90, editable: true, formType: 'select', options: ['上架','下架','缺货'] },
  { field: 'sale_count', label: '销量', width: 90, editable: true, formType: 'number' },
]
const searchFields = [
  { field: 'name', label: '商品名称', type: 'text' },
  { field: 'category', label: '类目', type: 'select', options: ['服装','数码','食品','家居','美妆','运动'] },
  { field: 'status', label: '状态', type: 'select', options: ['上架','下架','缺货'] },
]
</script>
<template>
  <MallCrud title="商品管理" api-path="/api/mall/products" :columns="columns" :search-fields="searchFields" id-field="product_id" />
</template>