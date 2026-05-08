<template>
  <div class="task-page">
    <h2>任务列表</h2>
    <p>Vue 迁移阶段2：Console Shell + Tasks 页面结构已接入。</p>
    <div class="cards">
      <div v-for="i in 3" :key="i" class="card">
        <div class="title">任务 #{{ i }}</div>
        <div class="desc">等待迁移真实任务数据与状态机</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-page h2 { margin: 0 0 8px; }
.task-page p { margin: 0 0 14px; color: #6b7280; font-size: 13px; }
.cards { display: grid; gap: 10px; }
.card { border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; background: #fff; }
.title { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
.desc { font-size: 13px; color: #6b7280; }
</style>
