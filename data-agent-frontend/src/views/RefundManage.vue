<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'refund_id', label: '退款ID', width: 90 },
  { field: 'order_id', label: '订单ID', width: 90 },
  { field: 'buyer_id', label: '买家ID', width: 90 },
  { field: 'shop_id', label: '店铺ID', width: 90 },
  { field: 'reason', label: '退款原因', width: 140, editable: true },
  { field: 'amount', label: '退款金额', width: 100, editable: true, formType: 'number' },
  { field: 'status', label: '状态', width: 100, editable: true, formType: 'select', options: ['处理中','已退款','已拒绝'] },
  { field: 'apply_time', label: '申请时间', width: 130, editable: true },
]
const searchFields = [
  { field: 'status', label: '状态', type: 'select', options: ['处理中','已退款','已拒绝'] },
  { field: 'shopId', label: '店铺ID', type: 'text' },
]
</script>
<template>
  <MallCrud title="退款管理" api-path="/api/mall/refunds" :columns="columns" :search-fields="searchFields" id-field="refund_id" />
</template>