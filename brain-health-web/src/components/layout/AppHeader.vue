<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{
  'toggle-sidebar': []
}>()

const router = useRouter()
const authStore = useAuthStore()

function handleLogout() {
  authStore.logout()
}

function goProfile() {
  router.push('/profile')
}
</script>

<template>
  <div class="app-header">
    <!-- Left: toggle + breadcrumb -->
    <div class="header-left">
      <el-icon
        class="toggle-btn"
        :size="20"
        @click="emit('toggle-sidebar')"
      >
        <Fold />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- Right: user menu -->
    <div class="header-right">
      <el-dropdown trigger="click" @command="(cmd: string) => {
        if (cmd === 'profile') goProfile()
        else if (cmd === 'logout') handleLogout()
      }">
        <span class="user-info">
          <el-icon><UserFilled /></el-icon>
          <span class="username">{{ authStore.realName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人设置
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toggle-btn {
  cursor: pointer;
  color: #666;

  &:hover {
    color: #409EFF;
  }
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;

  &:hover {
    background: #f5f5f5;
  }

  .username {
    font-size: 14px;
  }
}
</style>
