<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/modules/auth'

const router = useRouter()
const requestRef = ref<FormInstance>()
const resetRef = ref<FormInstance>()
const requesting = ref(false)
const resetting = ref(false)
const codeSent = ref(false)
const requestForm = reactive({ email: '' })
const resetForm = reactive({ code: '', newPassword: '', confirmPassword: '' })

const requestRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
}
const resetRules: FormRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码应为 6 位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 128, message: '密码长度应为 8–128 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码必须同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== resetForm.newPassword) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur',
  }],
}

async function requestCode() {
  if (!requestRef.value) return
  try { await requestRef.value.validate() } catch { return }
  requesting.value = true
  try {
    await authApi.forgotPassword({ email: requestForm.email })
    codeSent.value = true
    ElMessage.success('如果该邮箱已注册，验证码将发送至邮箱')
  } finally {
    requesting.value = false
  }
}

async function resetPassword() {
  if (!resetRef.value) return
  try { await resetRef.value.validate() } catch { return }
  resetting.value = true
  try {
    await authApi.resetPassword({
      email: requestForm.email,
      code: resetForm.code,
      newPassword: resetForm.newPassword,
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    router.push({ name: 'Login' })
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <div class="forgot-password-view">
    <h2>找回密码</h2>
    <el-form v-if="!codeSent" ref="requestRef" :model="requestForm" :rules="requestRules" label-position="top">
      <p class="hint">输入注册邮箱，我们会发送一个 10 分钟内有效的验证码。</p>
      <el-form-item label="邮箱地址" prop="email">
        <el-input v-model="requestForm.email" size="large" type="email" clearable />
      </el-form-item>
      <el-button type="primary" size="large" :loading="requesting" class="wide" @click="requestCode">
        发送验证码
      </el-button>
    </el-form>

    <el-form v-else ref="resetRef" :model="resetForm" :rules="resetRules" label-position="top">
      <p class="hint">验证码已发送至 {{ requestForm.email }}。为保护隐私，无论邮箱是否注册都会显示此提示。</p>
      <el-form-item label="验证码" prop="code">
        <el-input v-model="resetForm.code" maxlength="6" size="large" inputmode="numeric" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="resetForm.newPassword" type="password" show-password size="large" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="resetForm.confirmPassword" type="password" show-password size="large" />
      </el-form-item>
      <el-button type="primary" size="large" :loading="resetting" class="wide" @click="resetPassword">
        重置密码
      </el-button>
      <el-button link type="primary" class="wide resend" @click="codeSent = false">重新发送</el-button>
    </el-form>
    <el-button link type="primary" class="wide footer" @click="router.push({ name: 'Login' })">返回登录</el-button>
  </div>
</template>

<style scoped lang="scss">
.forgot-password-view {
  h2 { text-align: center; margin: 0 0 18px; font-size: 21px; }
  .hint { color: #909399; line-height: 1.6; font-size: 13px; margin: 0 0 18px; }
  .wide { width: 100%; }
  .resend { margin: 12px 0 0; }
  .footer { margin-top: 14px; }
}
</style>
