import { request } from '@/utils/request'

// 后端未启用 jimmer TS client（/ts.zip 不可用），这里直接封装所需接口，
// 替代原本由 jimmer 生成的 Api 类。DataChat.vue 仅用到 agentJson()。
export const api = {
  a2acontroller: {
    agentJson: () => request({ url: '/.well-known/agent-card.json', method: 'GET' }),
  },
}
