<template>
  <div class="app-shell">
    <TopHeader />
    <div class="main-shell">
      <ConversationSidebar />
      <main class="workspace-main">
        <ChatPanel />
        <BuilderPanel />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import TopHeader from '@/components/layout/TopHeader.vue'
import ConversationSidebar from '@/components/layout/ConversationSidebar.vue'
import ChatPanel from '@/components/workspace/ChatPanel.vue'
import BuilderPanel from '@/components/workspace/BuilderPanel.vue'
</script>
