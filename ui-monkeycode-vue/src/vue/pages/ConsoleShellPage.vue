<template>
  <div class="console-shell">
    <aside class="sidebar">
      <div class="logo">MonkeyCode</div>
      <button class="new-task">+ 启动任务</button>
      <input class="search" placeholder="搜索任务/项目" />
      <nav class="menu">
        <router-link to="/console/tasks">任务</router-link>
        <router-link to="/console/gitbot">GitBot</router-link>
        <router-link to="/console/ide">IDE</router-link>
      </nav>
    </aside>
    <section class="workspace">
      <header class="workspace-header">
        <span>Console</span>
        <div class="actions">
          <button>获取积分</button>
          <button>刷新</button>
        </div>
      </header>
      <main class="workspace-body">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.console-shell { height: 100vh; background: #f3f5f8; display: grid; grid-template-columns: 280px 1fr; gap: 8px; padding: 8px; }
.sidebar { border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.logo { font-size: 14px; font-weight: 600; }
.new-task { height: 34px; border: 1px solid #ddd6fe; border-radius: 8px; background: #f5f3ff; color: #6d28d9; }
.search { height: 34px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 0 10px; }
.menu { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.menu a { color: #374151; text-decoration: none; font-size: 14px; padding: 6px 8px; border-radius: 8px; }
.menu a.router-link-active { background: #f3f4f6; color: #111827; }
.workspace { border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; overflow: hidden; display: flex; flex-direction: column; }
.workspace-header { height: 46px; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; justify-content: space-between; padding: 0 12px; font-size: 14px; font-weight: 600; }
.actions { display: flex; gap: 8px; }
.actions button { height: 30px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; padding: 0 10px; font-size: 12px; }
.workspace-body { flex: 1; overflow: auto; padding: 12px; }
</style>
