<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppHeader from '@/components/layout/AppHeader.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isSidebarCollapsed = ref(false)

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}
</script>

<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isSidebarCollapsed ? '64px' : '220px'" class="layout-aside">
      <AppSidebar :collapsed="isSidebarCollapsed" />
    </el-aside>

    <!-- Main area -->
    <el-container>
      <!-- Header -->
      <el-header class="layout-header">
        <AppHeader @toggle-sidebar="toggleSidebar" />
      </el-header>

      <!-- Content -->
      <el-main class="layout-main">
        <router-view />
      </el-main>

      <!-- Footer -->
      <el-footer class="layout-footer">
        <span>心理及脑健康数据管理平台 v1.0 &copy; {{ new Date().getFullYear() }} 安徽省立医院 × 滁州学院</span>
      </el-footer>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.layout-header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
  height: 60px;
  line-height: 60px;
}

.layout-main {
  background: #f0f2f5;
  min-height: calc(100vh - 60px - 40px);
  padding: 20px;
}

.layout-footer {
  background: #fff;
  border-top: 1px solid #e6e6e6;
  height: 40px;
  line-height: 40px;
  text-align: center;
  font-size: 12px;
  color: #999;
}
</style>
