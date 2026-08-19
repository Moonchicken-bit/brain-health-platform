<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goDashboard = () => {
  router.push({ name: 'Dashboard' })
}
</script>

<template>
  <div class="not-found-container">
    <el-result icon="warning" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="goDashboard">
          <el-icon><HomeFilled /></el-icon>
          返回工作台
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.not-found-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}

:deep(.el-result__title) {
  font-size: 72px;
  font-weight: 700;
  color: #303133;
}

:deep(.el-result__subtitle) {
  font-size: 16px;
  color: #909399;
  margin-top: 8px;
}
</style>
