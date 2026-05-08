import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import './index.css'

import App from './vue/App.vue'
import {
  LoginPage,
  WelcomePage,
  UserConsolePage,
  ManagerConsolePage,
  TasksPage,
  TaskDetailPage,
  ProjectOverviewPage,
  GitBotsPage,
  IDEPage,
  TerminalPage,
  FileManagerPage,
  SharedTerminalPage,
  TeamManagerDashboard,
  TeamManagerMembers,
  TeamManagerHosts,
  TeamManagerImages,
  TeamManagerModels,
  TeamManagerLogs,
  TeamManagerManager,
  TeamManagerOtherSettings,
  PlaygroundPage,
  PlaygroundCreatePage,
  PlaygroundDetailPage,
  PublicTaskPage,
  PrivacyPolicyPage,
  UserAgreementPage,
  FindPasswordPage,
  ResetPasswordPage
} from './vue/pages'
import WelcomeVuePage from './vue/pages/WelcomePage.vue'
import LoginVuePage from './vue/pages/LoginPage.vue'
import ConsoleShellPage from './vue/pages/ConsoleShellPage.vue'
import TaskListPage from './vue/pages/TaskListPage.vue'

const routes = [
  { path: '/', component: WelcomeVuePage },
  { path: '/playground', component: PlaygroundPage },
  { path: '/playground/create', component: PlaygroundCreatePage },
  { path: '/playground/detail', component: PlaygroundDetailPage },
  { path: '/privacy-policy', component: PrivacyPolicyPage },
  { path: '/user-agreement', component: UserAgreementPage },
  { path: '/tasks/public', component: PublicTaskPage },
  { path: '/login', component: LoginVuePage },
  { path: '/findpassword', component: FindPasswordPage },
  { path: '/resetpassword', component: ResetPasswordPage },
  {
    path: '/console',
    component: ConsoleShellPage,
    children: [
      { path: '', redirect: '/console/tasks' },
      { path: 'tasks', component: TaskListPage },
      { path: 'task/:taskId', component: TaskDetailPage },
      { path: 'project/:projectId', component: ProjectOverviewPage },
      { path: 'gitbot', component: GitBotsPage },
      { path: 'ide', component: IDEPage }
    ]
  },
  { path: '/console/terminal', component: TerminalPage },
  { path: '/console/files', component: FileManagerPage },
  { path: '/sharedterminal', component: SharedTerminalPage },
  {
    path: '/manager',
    component: ManagerConsolePage,
    children: [
      { path: '', redirect: '/manager/dashboard' },
      { path: 'dashboard', component: TeamManagerDashboard },
      { path: 'members', component: TeamManagerMembers },
      { path: 'hosts', component: TeamManagerHosts },
      { path: 'images', component: TeamManagerImages },
      { path: 'models', component: TeamManagerModels },
      { path: 'logs', component: TeamManagerLogs },
      { path: 'manager', component: TeamManagerManager },
      { path: 'other-settings', component: TeamManagerOtherSettings }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
