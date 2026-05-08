<template>
  <div class="welcome-page">
    <header class="welcome-header">
      <div class="brand">
        <span class="dot"></span>
        <span>MonkeyCode</span>
      </div>
      <nav class="nav">
        <router-link to="/playground">广场</router-link>
        <router-link to="/login">登录</router-link>
      </nav>
    </header>
    <main class="hero">
      <h1>MonkeyCode 智能开发平台</h1>
      <p>Vue 迁移阶段2：Welcome 页面结构已替换为真实页面布局。</p>
      <div class="actions">
        <router-link class="btn primary" to="/console/tasks">进入控制台</router-link>
        <router-link class="btn" to="/playground">查看广场</router-link>
      </div>
    </main>
  </div>
</template>

<style scoped>
.welcome-page { min-height: 100vh; background: #f5f7fb; color: #111827; }
.welcome-header { height: 56px; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: #fff; }
.brand { display: flex; gap: 8px; align-items: center; font-weight: 600; }
.dot { width: 10px; height: 10px; border-radius: 50%; background: #7c3aed; }
.nav { display: flex; gap: 16px; font-size: 14px; }
.nav a { color: #374151; text-decoration: none; }
.hero { max-width: 900px; margin: 0 auto; padding: 80px 20px; }
.hero h1 { font-size: 40px; margin: 0 0 12px; }
.hero p { color: #6b7280; margin: 0 0 24px; }
.actions { display: flex; gap: 12px; }
.btn { border: 1px solid #d1d5db; border-radius: 10px; padding: 10px 16px; text-decoration: none; color: #111827; background: #fff; }
.btn.primary { background: #7c3aed; border-color: #7c3aed; color: #fff; }
</style>
