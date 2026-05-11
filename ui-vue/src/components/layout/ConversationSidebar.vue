<template>
  <aside class="sidebar">
    <div class="sidebar-title">会话</div>
    <button class="new-chat-btn">+ 新建会话</button>
    <input class="search" placeholder="搜索会话..." />
    <div class="conversation-list">
      <div v-for="item in mockConversations" :key="item.id" class="conversation-item">
        <div class="conversation-name">{{ item.title }}</div>
        <div class="conversation-time">{{ item.time }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
const mockConversations = [
  { id: '1', title: '新对话', time: '刚刚' },
  { id: '2', title: 'MCP 工具调用调试', time: '2分钟前' },
  { id: '3', title: '前端布局迁移', time: '10分钟前' }
]
</script>
