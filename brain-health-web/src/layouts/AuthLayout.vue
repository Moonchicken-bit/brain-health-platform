<script setup lang="ts">
</script>

<template>
  <div class="auth-layout">
    <div class="auth-container">
      <!-- Logo + Title -->
      <div class="auth-header">
        <div class="auth-logo">
          <svg viewBox="0 0 40 40" width="40" height="40">
            <rect width="40" height="40" rx="10" fill="#409eff"/>
            <text x="20" y="28" text-anchor="middle" fill="#fff" font-size="22" font-weight="bold">B</text>
          </svg>
        </div>
        <h1>脑健康数据平台</h1>
        <p>Psychological & Brain Health Data Platform</p>
      </div>

      <!-- Login Card -->
      <div class="auth-card">
        <router-view />
      </div>

      <!-- Footer -->
      <div class="auth-footer">
        <span>&copy; {{ new Date().getFullYear() }} Brain Health Platform</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.auth-container {
  width: 400px;
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;

  .auth-logo {
    margin-bottom: 16px;
  }

  h1 {
    color: #1a1a2e;
    font-size: 22px;
    font-weight: 700;
    margin: 0 0 6px;
    letter-spacing: 1px;
  }

  p {
    color: #909399;
    font-size: 13px;
    margin: 0;
    font-weight: 400;
  }
}

.auth-card {
  background: #fff;
  border-radius: 12px;
  padding: 36px 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.auth-footer {
  text-align: center;
  margin-top: 24px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
