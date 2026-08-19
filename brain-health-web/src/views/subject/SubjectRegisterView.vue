<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { subjectApi } from '@/api/modules/subject'
import http from '@/api/client'

const router = useRouter()

// ---- Form state ----
const formRef = ref<FormInstance>()
const loading = ref(false)

interface SelectOption {
  id: number
  name: string
}

const institutions = ref<SelectOption[]>([])
const projects = ref<SelectOption[]>([])
const cohorts = ref<SelectOption[]>([])
const maritalStatusOptions = ref<SelectOption[]>([])
const bloodTypeOptions = ref<SelectOption[]>([])

const form = reactive({
  subjectId: '',
  firstName: '',
  lastName: '',
  sex: '',
  dateOfBirth: '',
  educationYears: undefined as number | undefined,
  ethnicity: '',
  handedness: '',
  phone: '',
  maritalStatusCodeId: undefined as number | undefined,
  bloodTypeCodeId: undefined as number | undefined,
  heightCm: undefined as number | undefined,
  weightKg: undefined as number | undefined,
  addressCity: '',
  addressDistrict: '',
  institutionId: undefined as number | undefined,
  projectId: undefined as number | undefined,
  cohortId: undefined as number | undefined,
  isConsented: false,
  consentDate: '',
  enrollmentDate: '',
  remark: '',
})

// ---- Ethnicity options ----
const ethnicityOptions = [
  '汉族', '回族', '满族', '蒙古族', '藏族', '维吾尔族',
  '苗族', '彝族', '壮族', '布依族', '朝鲜族', '侗族',
  '瑶族', '白族', '土家族', '哈尼族', '哈萨克族', '傣族',
  '黎族', '其他',
]

// ---- Validation rules ----
const rules: FormRules = {
  sex: [
    { required: true, message: '请选择性别', trigger: 'change' },
  ],
  dateOfBirth: [
    { required: true, message: '请选择出生日期', trigger: 'change' },
  ],
  institutionId: [
    { required: true, message: '请选择所属机构', trigger: 'change' },
  ],
  projectId: [
    { required: true, message: '请选择所属项目', trigger: 'change' },
  ],
}

// ---- Date picker: disallow future dates ----
function disabledDate(time: Date): boolean {
  return time.getTime() > Date.now()
}

// ---- Load reference data ----
// ---- Load reference data ----
async function loadReferenceData() {
  // 独立加载每项数据，一项失败不影响其他
  const safeGet = async (url: string): Promise<SelectOption[]> => {
    try {
      const res = await http.get<{ code: number; data: SelectOption[] }>(url)
      return res.data.data || []
    } catch { return [] }
  }

  const [inst, proj, coh, marital, blood] = await Promise.all([
    safeGet('/api/v1/institutions'),
    safeGet('/api/v1/projects'),
    safeGet('/api/v1/cohorts'),
    safeGet('/api/v1/admin/marital-statuses'),
    safeGet('/api/v1/admin/blood-types'),
  ])

  institutions.value = inst
  projects.value = proj
  cohorts.value = coh
  maritalStatusOptions.value = marital
  bloodTypeOptions.value = blood
}

onMounted(() => {
  loadReferenceData()
})

// ---- Submit handler ----
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await subjectApi.create({
        subjectId: form.subjectId || undefined as any,
        firstName: form.firstName || undefined,
        lastName: form.lastName || undefined,
        sex: form.sex,
        dateOfBirth: form.dateOfBirth,
        educationYears: form.educationYears,
        ethnicityCodeId: undefined,
        handedness: form.handedness || undefined,
        phone: form.phone || undefined,
        maritalStatusCodeId: form.maritalStatusCodeId,
        bloodTypeCodeId: form.bloodTypeCodeId,
        heightCm: form.heightCm,
        weightKg: form.weightKg,
        addressCity: form.addressCity || undefined,
        addressDistrict: form.addressDistrict || undefined,
        institutionId: form.institutionId!,
        projectId: form.projectId!,
        isConsented: form.isConsented,
        consentDate: form.consentDate || undefined,
        enrollmentDate: form.enrollmentDate || undefined,
        remarks: form.remark || undefined,
        ...(form.cohortId ? { cohortId: form.cohortId } as any : {}),
      } as any)

      const data = res.data?.data ?? res.data
      const id = data?.id
      ElMessage.success('受试者登记成功')
      if (id) {
        router.push({ name: 'SubjectDetail', params: { id } })
      } else {
        router.push({ name: 'SubjectList' })
      }
    } catch {
      // API interceptor already shows ElMessage.error
    } finally {
      loading.value = false
    }
  })
}

// ---- Cancel handler ----
function handleCancel() {
  router.back()
}
</script>

<template>
  <div class="subject-register-view">
    <h2 class="page-title">受试者登记</h2>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="register-form"
      size="default"
    >
      <!-- 受试者ID -->
      <el-form-item label="受试者ID">
        <el-input
          v-model="form.subjectId"
          placeholder="留空则自动生成"
          clearable
          style="max-width: 320px"
        />
        <span class="form-tip">留空将由系统自动生成唯一ID</span>
      </el-form-item>

      <!-- 性别 -->
      <el-form-item label="性别" prop="sex">
        <el-radio-group v-model="form.sex">
          <el-radio value="MALE">男</el-radio>
          <el-radio value="FEMALE">女</el-radio>
          <el-radio value="OTHER">其他</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 出生日期 -->
      <el-form-item label="出生日期" prop="dateOfBirth">
        <el-date-picker
          v-model="form.dateOfBirth"
          type="date"
          placeholder="请选择出生日期"
          :disabled-date="disabledDate"
          value-format="YYYY-MM-DD"
          style="max-width: 320px"
        />
      </el-form-item>

      <!-- 教育程度 -->
      <el-form-item label="教育程度">
        <el-input-number
          v-model="form.educationYears"
          :min="0"
          :max="30"
          placeholder="受教育年数"
          style="max-width: 320px"
        />
        <span class="form-tip">单位：年</span>
      </el-form-item>

      <!-- 民族 -->
      <el-form-item label="民族">
        <el-select
          v-model="form.ethnicity"
          placeholder="请选择民族"
          clearable
          filterable
          style="max-width: 320px"
        >
          <el-option
            v-for="item in ethnicityOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>

      <!-- 利手 -->
      <el-form-item label="利手">
        <el-radio-group v-model="form.handedness">
          <el-radio value="RIGHT">右利手</el-radio>
          <el-radio value="LEFT">左利手</el-radio>
          <el-radio value="BOTH">双利手</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 姓名 -->
      <el-form-item label="姓名">
        <el-row :gutter="12" style="max-width: 480px">
          <el-col :span="12">
            <el-input v-model="form.lastName" placeholder="姓" clearable />
          </el-col>
          <el-col :span="12">
            <el-input v-model="form.firstName" placeholder="名" clearable />
          </el-col>
        </el-row>
      </el-form-item>

      <!-- 联系电话 -->
      <el-form-item label="联系电话">
        <el-input v-model="form.phone" placeholder="请输入联系电话" clearable style="max-width: 320px" />
      </el-form-item>

      <!-- 婚姻状况 -->
      <el-form-item label="婚姻状况">
        <el-select v-model="form.maritalStatusCodeId" placeholder="请选择婚姻状况" clearable style="max-width: 320px">
          <el-option v-for="item in maritalStatusOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>

      <!-- 身高 / 体重 -->
      <el-form-item label="身高">
        <el-input-number v-model="form.heightCm" :min="50" :max="250" :step="0.1" placeholder="cm" style="max-width: 200px" />
        <span class="form-tip">cm</span>
      </el-form-item>
      <el-form-item label="体重">
        <el-input-number v-model="form.weightKg" :min="20" :max="300" :step="0.1" placeholder="kg" style="max-width: 200px" />
        <span class="form-tip">kg</span>
      </el-form-item>

      <!-- 血型 -->
      <el-form-item label="血型">
        <el-select v-model="form.bloodTypeCodeId" placeholder="请选择血型" clearable style="max-width: 320px">
          <el-option v-for="item in bloodTypeOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>

      <!-- 居住地址 -->
      <el-form-item label="居住城市">
        <el-input v-model="form.addressCity" placeholder="请输入居住城市" clearable style="max-width: 320px" />
      </el-form-item>
      <el-form-item label="居住区县">
        <el-input v-model="form.addressDistrict" placeholder="请输入居住区县" clearable style="max-width: 320px" />
      </el-form-item>

      <!-- 知情同意 -->
      <el-form-item label="知情同意">
        <el-switch v-model="form.isConsented" active-text="已签署" inactive-text="未签署" />
        <span v-if="form.isConsented" style="margin-left: 12px">
          <el-date-picker v-model="form.consentDate" type="date" placeholder="签署日期" value-format="YYYY-MM-DD" style="width: 180px" />
        </span>
      </el-form-item>

      <!-- 入组日期 -->
      <el-form-item label="入组日期">
        <el-date-picker v-model="form.enrollmentDate" type="date" placeholder="选择入组日期" value-format="YYYY-MM-DD" style="max-width: 320px" />
      </el-form-item>

      <!-- 所属机构 -->
      <el-form-item label="所属机构" prop="institutionId">
        <el-select
          v-model="form.institutionId"
          placeholder="请选择所属机构"
          clearable
          filterable
          style="max-width: 320px"
        >
          <el-option
            v-for="item in institutions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <!-- 所属项目 -->
      <el-form-item label="所属项目" prop="projectId">
        <el-select
          v-model="form.projectId"
          placeholder="请选择所属项目"
          clearable
          filterable
          style="max-width: 320px"
        >
          <el-option
            v-for="item in projects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <!-- 队列 -->
      <el-form-item label="队列">
        <el-select
          v-model="form.cohortId"
          placeholder="请选择队列（可选）"
          clearable
          filterable
          style="max-width: 320px"
        >
          <el-option
            v-for="item in cohorts"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <!-- 备注 -->
      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="4"
          placeholder="请输入备注信息"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- Actions -->
      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ loading ? '提交中...' : '提 交' }}
        </el-button>
        <el-button @click="handleCancel">
          取 消
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts">
export default {
  name: 'SubjectRegisterView',
}
</script>

<style scoped lang="scss">
.subject-register-view {
  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 24px;
  }

  .register-form {
    max-width: 720px;

    .form-tip {
      margin-left: 10px;
      font-size: 13px;
      color: #909399;
    }
  }
}
</style>
