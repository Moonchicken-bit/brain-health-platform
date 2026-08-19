<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/modules/auth'

const authStore = useAuthStore()
const twoFactorSetup = ref<{ secret: string; otpauthUri: string; recoveryCodes: string[] } | null>(null)
const twoFactorCode = ref('')
const twoFactorLoading = ref(false)

async function beginTwoFactorSetup() {
  twoFactorLoading.value = true
  try {
    const res = await authApi.setup2FA()
    twoFactorSetup.value = res.data.data
  } finally {
    twoFactorLoading.value = false
  }
}

async function enableTwoFactor() {
  if (!/^\d{6}$/.test(twoFactorCode.value)) {
    ElMessage.warning('请输入认证器生成的 6 位验证码')
    return
  }
  twoFactorLoading.value = true
  try {
    await authApi.verify2FA(twoFactorCode.value)
    ElMessage.success('双因素认证已启用，请妥善保存恢复码')
    twoFactorCode.value = ''
  } finally {
    twoFactorLoading.value = false
  }
}

// ---- Password change form ----
const passwordFormRef = ref<FormInstance>()
const passwordChanging = ref(false)

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const passwordForm = reactive<PasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules<PasswordForm> = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
    { max: 32, message: '密码长度不能超过32位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleChangePassword() {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  passwordChanging.value = true
  try {
    await authApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordFormRef.value.resetFields()
    // Force re-login after password change
    authStore.clearAuth()
    window.location.href = '/login'
  } catch {
    // Error is already handled by the HTTP interceptor
  } finally {
    passwordChanging.value = false
  }
}

// ---- Role tag display ----
function getRoleTagType(role: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    admin: 'danger',
    superadmin: 'danger',
    doctor: 'success',
    researcher: 'warning',
    data_manager: 'info',
    reviewer: '',
  }
  return map[role] || ''
}

function formatRoleName(role: string): string {
  const map: Record<string, string> = {
    admin: '管理员',
    superadmin: '超级管理员',
    doctor: '医生',
    researcher: '研究员',
    data_manager: '数据管理员',
    reviewer: '审核员',
  }
  return map[role] || role
}
</script>

<template>
  <div class="profile-page">
    <!-- Page header -->
    <div class="page-header">
      <h2>个人设置</h2>
      <p class="page-desc">查看和修改个人账户信息</p>
    </div>

    <div class="profile-content">
      <!-- User info card -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名" :span="1">
            {{ authStore.userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="真实姓名" :span="1">
            {{ authStore.userInfo?.realName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="电子邮箱" :span="1">
            {{ authStore.userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话" :span="1">
            {{ authStore.userInfo?.phone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="所属机构" :span="1">
            {{ authStore.userInfo?.institutionName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="部门" :span="1">
            {{ authStore.userInfo?.department || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="职称" :span="1">
            {{ authStore.userInfo?.title || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色" :span="1">
            <template v-if="authStore.userInfo?.roles?.length">
              <el-tag
                v-for="role in authStore.userInfo.roles"
                :key="role"
                :type="getRoleTagType(role)"
                size="small"
                style="margin-right: 6px"
              >
                {{ formatRoleName(role) }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="password-card" shadow="never">
        <template #header><div class="card-header"><span>双因素认证</span></div></template>
        <el-button v-if="!twoFactorSetup" type="primary" :loading="twoFactorLoading" @click="beginTwoFactorSetup">
          配置认证器
        </el-button>
        <div v-else style="max-width: 620px">
          <p>在认证器中手动输入密钥：<strong>{{ twoFactorSetup.secret }}</strong></p>
          <p style="word-break: break-all">配置 URI：{{ twoFactorSetup.otpauthUri }}</p>
          <p>恢复码（每个只能使用一次，请立即离线保存）：</p>
          <el-tag v-for="code in twoFactorSetup.recoveryCodes" :key="code" style="margin: 0 8px 8px 0">
            {{ code }}
          </el-tag>
          <el-input v-model="twoFactorCode" maxlength="6" placeholder="输入 6 位验证码" style="max-width: 240px; display: block; margin: 12px 0" />
          <el-button type="primary" :loading="twoFactorLoading" @click="enableTwoFactor">验证并启用</el-button>
        </div>
      </el-card>

      <!-- Change password card -->
      <el-card class="password-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>修改密码</span>
          </div>
        </template>

        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="120px"
          label-position="right"
          style="max-width: 480px"
          @submit.prevent="handleChangePassword"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="请输入新密码（6-32位）"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="passwordChanging"
              @click="handleChangePassword"
            >
              修改密码
            </el-button>
            <el-button @click="passwordFormRef?.resetFields()">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 4px;
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }

  .page-desc {
    margin: 0;
    font-size: 13px;
    color: #909399;
  }
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.info-card {
  :deep(.el-descriptions__label) {
    width: 120px;
    font-weight: 500;
  }
}

.password-card {
  .el-form-item:last-child {
    margin-bottom: 0;
  }
}
</style>
