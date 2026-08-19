<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goDashboard = () => {
  router.push({ name: 'Dashboard' })
}
</script>

<template>
  <div class="forbidden-page">
    <el-result icon="warning">
      <template #title>
        <span class="error-code">403</span>
      </template>
      <template #sub-title>
        <p class="error-message">抱歉，您没有权限访问此页面</p>
      </template>
      <template #extra>
        <el-button type="primary" @click="goDashboard">返回工作台</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.forbidden-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}

.error-code {
  font-size: 72px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 4px;
}

.error-message {
  font-size: 16px;
  color: #909399;
  margin: 0;
}
</style>
