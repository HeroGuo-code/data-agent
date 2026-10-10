import axios from 'axios'

// 通过 vite 代理转发，/api 前缀会被剥掉后发往后端 9933
const http = axios.create({
  baseURL: '',
  timeout: 30000,
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => Promise.reject(err.response ? err.response.data : err),
)

export default http