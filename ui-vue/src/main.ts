import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './styles.css'

import WorkspacePage from './pages/WorkspacePage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: WorkspacePage }
  ]
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
