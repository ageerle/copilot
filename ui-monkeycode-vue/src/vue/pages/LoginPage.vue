<template>
  <div class="login-page">
    <div class="login-card">
      <h1>登录</h1>
      <p>Vue 迁移阶段2：Login 页面结构已替换为真实页面布局。</p>
      <form class="form" @submit.prevent>
        <label>
          <span>邮箱</span>
          <input type="email" placeholder="you@example.com" />
        </label>
        <label>
          <span>密码</span>
          <input type="password" placeholder="请输入密码" />
        </label>
        <button type="submit">登录</button>
      </form>
      <div class="footer-links">
        <router-link to="/findpassword">找回密码</router-link>
        <router-link to="/">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #f5f7fb; }
.login-card { width: 420px; max-width: calc(100vw - 24px); background: #fff; border: 1px solid #e5e7eb; border-radius: 14px; padding: 24px; }
h1 { margin: 0 0 8px; }
p { margin: 0 0 16px; color: #6b7280; font-size: 13px; }
.form { display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #374151; }
input { height: 38px; border: 1px solid #d1d5db; border-radius: 8px; padding: 0 10px; }
button { height: 38px; border: 1px solid #7c3aed; border-radius: 8px; background: #7c3aed; color: #fff; margin-top: 6px; }
.footer-links { margin-top: 12px; display: flex; justify-content: space-between; font-size: 13px; }
</style>
