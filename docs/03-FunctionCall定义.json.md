# 数据查询工具 FunctionCall 定义（JSON）

> 用于将 DataAgent 作为子 Agent 供上层电商客服主 Agent 远程调用（A2A）。

\`\`\`json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "query_sales_report",
        "description": "查询店铺/时间段销售额、GMV、客单价、退款率等经营指标（运营）。",
        "parameters": {
          "type": "object",
          "properties": {
            "shop_id":   {"type": "integer", "description": "所属店铺ID，运营必填，禁止跨店铺"},
            "start_date":{"type": "string", "format": "date", "description": "起始日期 yyyy-MM-dd"},
            "end_date":  {"type": "string", "format": "date", "description": "结束日期 yyyy-MM-dd"},
            "group_by":  {"type": "string", "enum": ["shop","category","date","product"], "description": "细分维度"},
            "order_by":  {"type": "string", "description": "排序指标,如 gmv/sales/refund_rate"},
            "top_n":     {"type": "integer", "description": "排行榜取前 N"}
          },
          "required": ["shop_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "query_product_sales",
        "description": "查询商品销量、库存变动、商品排行榜。",
        "parameters": {
          "type": "object",
          "properties": {
            "shop_id":  {"type": "integer"},
            "product_id":{"type": "integer"},
            "category": {"type": "string"},
            "start_date":{"type": "string"}, "end_date":{"type": "string"},
            "top_n":    {"type": "integer"}
          },
          "required": ["shop_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "query_refund_stats",
        "description": "查询退款统计，按店铺/时段/原因聚合。",
        "parameters": {
          "type": "object",
          "properties": {
            "shop_id":{"type":"integer"},"start_date":{"type":"string"},"end_date":{"type":"string"},
            "group_by":{"type":"string","enum":["shop","reason","date"]}
          },
          "required":["shop_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "query_activity",
        "description": "查询活动核销金额/订单数用于活动复盘。",
        "parameters": {
          "type": "object",
          "properties": {
            "activity_name":{"type":"string"},"shop_id":{"type":"integer"},
            "start_date":{"type":"string"},"end_date":{"type":"string"}
          }
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "query_single_buyer",
        "description": "查询单个买家历史订单/售后/消费汇总（客服坐席，仅允许单个 buyer_id，敏感脱敏）。",
        "parameters": {
          "type": "object",
          "properties": {
            "buyer_id":{"type":"integer","description":"单个买家ID，必填"},
            "include_orders":{"type":"boolean"}, "include_refunds":{"type":"boolean"}
          },
          "required":["buyer_id"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "transfer_to_business_agent",
        "description": "非数据类业务（退货办理、物流咨询、保修、投诉人工）转交上层电商客服主 Agent。",
        "parameters": {"type":"object","properties":{"intent":{"type":"string"},"summary":{"type":"string"}},"required":["intent"]}
      }
    }
  ]
}
\`\`\`

> 工程内实际以「规划器将问题拆步 + SqlGeneratorNode 生成只读 SQL」完成查询，
> FunctionCall 定义主要用于对外对接与 API 化封装说明。
