<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(localStorage.getItem('remember_me') === 'true')

const form = reactive({
  username: localStorage.getItem('remembered_username') || '',
  password: '',
  otpCode: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await authStore.login({
        username: form.username,
        password: form.password,
        otpCode: form.otpCode || undefined,
      })

      if (rememberMe.value) {
        localStorage.setItem('remember_me', 'true')
        localStorage.setItem('remembered_username', form.username)
      } else {
        localStorage.removeItem('remember_me')
        localStorage.removeItem('remembered_username')
      }

      ElMessage.success('登录成功')
      const patientHome = authStore.hasAnyRole(['patient']) ? '/visit-entry' : '/dashboard'
      const raw = authStore.hasAnyRole(['patient'])
        ? patientHome
        : ((route.query.redirect as string) || patientHome)
      // 只接受以 / 开头的相对路径，防止 Invalid redirection
      const redirect = (raw.startsWith('/') && !raw.startsWith('//')) ? raw : patientHome
      router.push(redirect).catch(() => router.push(patientHome))
    } catch {
      // handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-view">
    <h2 class="login-title">欢迎回来</h2>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      @keyup.enter="handleLogin"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          placeholder="用户名"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>

      <el-form-item>
        <el-input
          v-model="form.otpCode"
          placeholder="双因素验证码（已启用时填写）"
          maxlength="12"
          clearable
        />
      </el-form-item>

      <div class="login-options">
        <el-checkbox v-model="rememberMe">记住我</el-checkbox>
        <router-link :to="{ name: 'ForgotPassword' }" class="forgot-link">
          忘记密码？
        </router-link>
      </div>

      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          class="login-btn"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LoginView',
}
</script>

<style scoped lang="scss">
.login-view {
  .login-title {
    text-align: center;
    font-size: 20px;
    color: #1a1a2e;
    margin: 0 0 24px;
    font-weight: 600;
  }

  :deep(.el-input__wrapper) {
    border-radius: 8px;
    box-shadow: 0 0 0 1px #e4e7ed inset;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 0 0 1px #c0c4cc inset;
    }
  }

  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px #409eff inset;
  }

  .login-options {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .forgot-link {
      color: #909399;
      font-size: 13px;
      text-decoration: none;

      &:hover {
        color: #409eff;
      }
    }
  }

  .login-btn {
    width: 100%;
    border-radius: 8px;
    height: 44px;
    font-size: 15px;
    letter-spacing: 2px;
  }
}
</style>
