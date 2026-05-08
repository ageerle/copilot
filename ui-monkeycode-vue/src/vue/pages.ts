import { defineComponent, h } from 'vue'

function makePage(title: string) {
  return defineComponent({
    name: title.replace(/\s+/g, ''),
    setup() {
      return () =>
        h('div', { class: 'min-h-screen bg-white text-gray-900 p-6' }, [
          h('div', { class: 'text-xs text-gray-500 mb-2' }, 'MonkeyCode Vue Migration (Stage 1)'),
          h('h1', { class: 'text-2xl font-semibold mb-2' }, title),
          h('p', { class: 'text-sm text-gray-600' }, '该页面路径已按上游 React 路由完成对齐，功能将在后续阶段迁移。')
        ])
    }
  })
}

export const LoginPage = makePage('Login Page')
export const WelcomePage = makePage('Welcome Page')
export const UserConsolePage = defineComponent({
  name: 'UserConsolePage',
  setup(_, { slots }) {
    return () => h('div', { class: 'min-h-screen bg-[#f5f6f8] p-3' }, [
      h('div', { class: 'text-xs text-gray-500 mb-2' }, 'MonkeyCode Vue Migration (Stage 1)'),
      h('h1', { class: 'text-xl font-semibold mb-3' }, 'User Console'),
      h('div', { class: 'border rounded bg-white p-3' }, slots.default ? slots.default() : 'Console Content')
    ])
  }
})
export const ManagerConsolePage = UserConsolePage
export const TasksPage = makePage('Tasks Page')
export const TaskDetailPage = makePage('Task Detail Page')
export const ProjectOverviewPage = makePage('Project Overview Page')
export const GitBotsPage = makePage('Git Bots Page')
export const IDEPage = makePage('IDE Page')
export const TerminalPage = makePage('Terminal Page')
export const FileManagerPage = makePage('File Manager Page')
export const SharedTerminalPage = makePage('Shared Terminal Page')
export const TeamManagerDashboard = makePage('Manager Dashboard')
export const TeamManagerMembers = makePage('Manager Members')
export const TeamManagerHosts = makePage('Manager Hosts')
export const TeamManagerImages = makePage('Manager Images')
export const TeamManagerModels = makePage('Manager Models')
export const TeamManagerLogs = makePage('Manager Logs')
export const TeamManagerManager = makePage('Manager Admins')
export const TeamManagerOtherSettings = makePage('Manager Other Settings')
export const PlaygroundPage = makePage('Playground Page')
export const PlaygroundCreatePage = makePage('Playground Create Page')
export const PlaygroundDetailPage = makePage('Playground Detail Page')
export const PublicTaskPage = makePage('Public Task Page')
export const PrivacyPolicyPage = makePage('Privacy Policy Page')
export const UserAgreementPage = makePage('User Agreement Page')
export const FindPasswordPage = makePage('Find Password Page')
export const ResetPasswordPage = makePage('Reset Password Page')
