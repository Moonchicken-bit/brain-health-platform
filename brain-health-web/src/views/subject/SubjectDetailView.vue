<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import {
  subjectApi,
  type SubjectDTO,
  type SubjectBusinessTag,
  type SubjectProjectNote,
} from '@/api/modules/subject'
import type { ScaleAssessment } from '@/api/modules/scale'
import { imagingApi, type ImagingSession } from '@/api/modules/imaging'
import http from '@/api/client'

// ---- Router ----
const router = useRouter()
const route = useRoute()

const subjectId = computed(() => Number(route.params.id))
const autoEdit = computed(() => route.query.edit === '1')

// ---- Subject state ----
const subject = ref<SubjectDTO | null>(null)
const subjectLoading = ref(false)

// ---- Tab state ----
const activeTab = ref('basic')
const loadedTabs = reactive<Record<string, boolean>>({
  basic: false,
  sessions: false,
  assessments: false,
  imaging: false,
  lab: false,
  genetics: false,
  collaboration: false,
})

// ---- Edit dialog state ----
const editDialogVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editSaving = ref(false)

interface SelectOption { id: number; name: string }
const editInstitutions = ref<SelectOption[]>([])
const editProjects = ref<SelectOption[]>([])
const editCohorts = ref<SelectOption[]>([])
const editMaritalStatusOptions = ref<SelectOption[]>([])
const editBloodTypeOptions = ref<SelectOption[]>([])

const editForm = reactive({
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
  isConsented: false,
  consentDate: '',
  enrollmentDate: '',
  remarks: '',
})

const ethnicityOptions = [
  '汉族', '回族', '满族', '蒙古族', '藏族', '维吾尔族',
  '苗族', '彝族', '壮族', '布依族', '朝鲜族', '侗族',
  '瑶族', '白族', '土家族', '哈尼族', '哈萨克族', '傣族',
  '黎族', '其他',
]

const editRules: FormRules = {
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  dateOfBirth: [{ required: true, message: '请选择出生日期', trigger: 'change' }],
}

// Snapshot for unsaved changes detection
let editFormSnapshot = ''

// ---- Sessions / visits ----
const sessions = ref<any[]>([])

// ---- Scale assessments ----
const assessments = ref<ScaleAssessment[]>([])

// ---- Imaging sessions ----
const imagingSessions = ref<ImagingSession[]>([])

// ---- Lab results ----
const labResults = ref<any[]>([])

// ---- Genetics ----
const geneticSamples = ref<any[]>([])
const geneticVariants = ref<any[]>([])
const geneticsLoading = ref(false)
const patientAccount = ref<any>({ created: false })
const patientDialogVisible = ref(false)
const patientSaving = ref(false)
const patientForm = reactive({ username: '', password: '', realName: '' })

// ---- Project collaboration ----
const availableTags = ref<SubjectBusinessTag[]>([])
const selectedTagIds = ref<number[]>([])
const projectNotes = ref<SubjectProjectNote[]>([])
const noteContent = ref('')
const collaborationLoading = ref(false)
const collaborationSaving = ref(false)
const newTagName = ref('')
const newTagColor = ref('#409EFF')

function formatDateTime(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

async function loadPatientAccount() {
  const response = await http.get(`/api/v1/subjects/${subjectId.value}/patient-account`)
  patientAccount.value = response.data.data || { created: false }
}

async function createPatientAccount() {
  if (!patientForm.username || !patientForm.password) {
    ElMessage.warning('请填写患者登录名和初始密码')
    return
  }
  patientSaving.value = true
  try {
    await http.post(`/api/v1/subjects/${subjectId.value}/patient-account`, patientForm)
    ElMessage.success('患者账号已开通，首次登录后可填写自己的量表')
    patientDialogVisible.value = false
    await loadPatientAccount()
  } finally {
    patientSaving.value = false
  }
}

// ---- Computed: format subject fields for el-descriptions ----
const subjectFields = computed(() => {
  if (!subject.value) return []
  const s = subject.value
  const name = [s.lastName, s.firstName].filter(Boolean).join('') || '-'
  return [
    { label: '受试者编号', value: s.subjectId },
    { label: '姓名', value: name },
    { label: '性别', value: sexLabel(s.sex) },
    { label: '出生日期', value: s.dateOfBirth || '-' },
    { label: '年龄', value: s.dateOfBirth ? calcAge(s.dateOfBirth) + ' 岁' : '-' },
    { label: '联系电话', value: s.phone || '-' },
    { label: '受教育年限', value: s.educationYears != null ? s.educationYears + ' 年' : '-' },
    { label: '民族', value: s.ethnicity || '-' },
    { label: '利手', value: handednessLabel(s.handedness) },
    { label: '婚姻状况', value: (s as any).maritalStatusName || '-' },
    { label: '身高', value: s.heightCm != null ? s.heightCm + ' cm' : '-' },
    { label: '体重', value: s.weightKg != null ? s.weightKg + ' kg' : '-' },
    { label: '血型', value: (s as any).bloodTypeName || '-' },
    { label: '居住城市', value: s.addressCity || '-' },
    { label: '居住区县', value: s.addressDistrict || '-' },
    { label: '知情同意', value: s.isConsented ? '已签署' : '未签署' },
    { label: '签署日期', value: s.consentDate || '-' },
    { label: '入组日期', value: s.enrollmentDate || '-' },
    { label: '所属机构', value: (s as any).institutionName || s.institutionId },
    { label: '所属项目', value: (s as any).projectName || s.projectId },
    { label: '状态', value: s.isActive ? '启用' : '停用' },
    { label: '备注', value: s.remarks || '-' },
    { label: '创建时间', value: s.createdAt || '-' },
    { label: '更新时间', value: s.updatedAt || '-' },
  ]
})

// ---- Helpers ----
function sexLabel(sex: string): string {
  const map: Record<string, string> = { M: '男', F: '女', MALE: '男', FEMALE: '女' }
  return map[sex] || sex || '-'
}

function handednessLabel(h: string | undefined): string {
  const map: Record<string, string> = { R: '右手', L: '左手', A: '双手', RIGHT: '右手', LEFT: '左手', AMBIDEXTROUS: '双手' }
  return map[h || ''] || h || '-'
}

function calcAge(dob: string): number {
  const birth = new Date(dob)
  const now = new Date()
  let age = now.getFullYear() - birth.getFullYear()
  const m = now.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age--
  return age
}

function disabledDate(time: Date): boolean {
  return time.getTime() > Date.now()
}

// ---- Load subject data ----
async function loadSubject() {
  subjectLoading.value = true
  try {
    const res = await subjectApi.getById(subjectId.value)
    subject.value = res.data.data
    loadedTabs.basic = true
  } catch {
    // error handled by interceptor
  } finally {
    subjectLoading.value = false
  }
}

// ---- Load sessions ----
async function loadSessions() {
  try {
    const res = await subjectApi.getSessions(subjectId.value)
    sessions.value = res.data.data ?? []
    loadedTabs.sessions = true
  } catch {
    sessions.value = []
    loadedTabs.sessions = true
  }
}

// ---- Load scale assessments ----
async function loadAssessments() {
  try {
    const res = await http.get(`/api/v1/assessments`, {
      params: { subjectId: subjectId.value },
    })
    assessments.value = res.data.data ?? []
    loadedTabs.assessments = true
  } catch {
    assessments.value = []
    loadedTabs.assessments = true
  }
}

// ---- Load imaging sessions ----
async function loadImaging() {
  try {
    const res = await imagingApi.listSessions({ subjectId: subjectId.value })
    imagingSessions.value = res.data.data ?? []
    loadedTabs.imaging = true
  } catch {
    imagingSessions.value = []
    loadedTabs.imaging = true
  }
}

// ---- Load lab results ----
async function loadLab() {
  try {
    const res = await http.get(`/api/v1/lab/results`, {
      params: { subjectId: subjectId.value },
    })
    labResults.value = res.data.data ?? []
    loadedTabs.lab = true
  } catch {
    labResults.value = []
    loadedTabs.lab = true
  }
}

// ---- Load genetics ----
async function loadGenetics() {
  geneticsLoading.value = true
  try {
    const [samplesRes, variantsRes] = await Promise.allSettled([
      http.get(`/api/v1/genetics/samples`, { params: { subjectId: subjectId.value } }),
      http.get(`/api/v1/genetics/variants`, { params: { subjectId: subjectId.value } }),
    ])
    geneticSamples.value = samplesRes.status === 'fulfilled' ? (samplesRes.value.data.data ?? []) : []
    geneticVariants.value = variantsRes.status === 'fulfilled' ? (variantsRes.value.data.data ?? []) : []
    loadedTabs.genetics = true
  } catch {
    geneticSamples.value = []
    geneticVariants.value = []
    loadedTabs.genetics = true
  } finally {
    geneticsLoading.value = false
  }
}

// ---- Tab change handler: lazy-load data ----
function handleTabChange(tabName: string) {
  const loaders: Record<string, () => Promise<void>> = {
    basic: loadSubject,
    sessions: loadSessions,
    assessments: loadAssessments,
    imaging: loadImaging,
    lab: loadLab,
    genetics: loadGenetics,
    collaboration: loadCollaboration,
  }
  if (!loadedTabs[tabName] && loaders[tabName]) {
    loaders[tabName]()
  }
}

async function loadCollaboration() {
  if (!subject.value?.id || !subject.value.projectId) return
  collaborationLoading.value = true
  try {
    const [tags, assigned, notes] = await Promise.all([
      subjectApi.listProjectTags(subject.value.projectId),
      subjectApi.getSubjectTags(subject.value.id),
      subjectApi.getProjectNoteHistory(subject.value.id),
    ])
    availableTags.value = tags.data.data || []
    selectedTagIds.value = (assigned.data.data || []).map((tag) => tag.id)
    projectNotes.value = notes.data.data || []
    noteContent.value = projectNotes.value[0]?.content || ''
    loadedTabs.collaboration = true
  } finally {
    collaborationLoading.value = false
  }
}

async function saveCollaboration() {
  if (!subject.value?.id) return
  collaborationSaving.value = true
  try {
    await subjectApi.setSubjectTags(subject.value.id, selectedTagIds.value)
    if (noteContent.value.trim() && noteContent.value.trim() !== projectNotes.value[0]?.content) {
      await subjectApi.saveProjectNote(subject.value.id, noteContent.value.trim())
    }
    ElMessage.success('项目标签和共享备注已保存')
    await loadCollaboration()
  } finally {
    collaborationSaving.value = false
  }
}

async function createProjectTag() {
  if (!subject.value?.projectId || !newTagName.value.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  const res = await subjectApi.createProjectTag(
    subject.value.projectId,
    newTagName.value.trim(),
    newTagColor.value,
  )
  availableTags.value.push(res.data.data)
  selectedTagIds.value.push(res.data.data.id)
  newTagName.value = ''
  ElMessage.success('项目标签已创建')
}

// ---- Edit dialog ----
function openEditDialog() {
  if (!subject.value) return
  const s = subject.value
  editForm.firstName = s.firstName || ''
  editForm.lastName = s.lastName || ''
  editForm.sex = normalizeSexForEdit(s.sex)
  editForm.dateOfBirth = s.dateOfBirth || ''
  editForm.educationYears = s.educationYears
  editForm.ethnicity = s.ethnicity || ''
  editForm.handedness = normalizeHandednessForEdit(s.handedness)
  editForm.phone = s.phone || ''
  editForm.maritalStatusCodeId = s.maritalStatusCodeId
  editForm.bloodTypeCodeId = s.bloodTypeCodeId
  editForm.heightCm = s.heightCm
  editForm.weightKg = s.weightKg
  editForm.addressCity = s.addressCity || ''
  editForm.addressDistrict = s.addressDistrict || ''
  editForm.isConsented = s.isConsented || false
  editForm.consentDate = s.consentDate || ''
  editForm.enrollmentDate = s.enrollmentDate || ''
  editForm.remarks = s.remarks || ''
  editFormSnapshot = JSON.stringify(editForm)
  editDialogVisible.value = true
}

function hasEditChanges(): boolean {
  return JSON.stringify(editForm) !== editFormSnapshot
}

function normalizeSexForEdit(sex: string): string {
  const upper = (sex || '').toUpperCase()
  if (upper === 'M' || upper === '男性') return '男'
  if (upper === 'F' || upper === '女性') return '女'
  return sex || ''
}

function normalizeHandednessForEdit(h: string | undefined): string {
  if (!h) return ''
  const upper = h.toUpperCase()
  if (upper === 'R' || upper === 'RIGHT') return 'RIGHT'
  if (upper === 'L' || upper === 'LEFT') return 'LEFT'
  if (upper === 'A' || upper === 'BOTH' || upper === 'AMBIDEXTROUS') return 'BOTH'
  return h
}

async function loadEditReferenceData() {
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
  editInstitutions.value = inst
  editProjects.value = proj
  editCohorts.value = coh
  editMaritalStatusOptions.value = marital
  editBloodTypeOptions.value = blood
}

async function handleEditSubmit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    editSaving.value = true
    try {
      await subjectApi.update(subjectId.value, {
        firstName: editForm.firstName || undefined,
        lastName: editForm.lastName || undefined,
        sex: editForm.sex === '男' ? 'MALE' : editForm.sex === '女' ? 'FEMALE' : editForm.sex,
        dateOfBirth: editForm.dateOfBirth || undefined,
        educationYears: editForm.educationYears,
        ethnicity: editForm.ethnicity || undefined,
        handedness: editForm.handedness || undefined,
        phone: editForm.phone || undefined,
        maritalStatusCodeId: editForm.maritalStatusCodeId,
        bloodTypeCodeId: editForm.bloodTypeCodeId,
        heightCm: editForm.heightCm,
        weightKg: editForm.weightKg,
        addressCity: editForm.addressCity || undefined,
        addressDistrict: editForm.addressDistrict || undefined,
        isConsented: editForm.isConsented,
        consentDate: editForm.consentDate || undefined,
        enrollmentDate: editForm.enrollmentDate || undefined,
        remarks: editForm.remarks || undefined,
      } as any)
      ElMessage.success('保存成功')
      editDialogVisible.value = false
      // Refresh subject data
      await loadSubject()
    } catch {
      // API interceptor shows error
    } finally {
      editSaving.value = false
    }
  })
}

async function handleEditCancel() {
  if (hasEditChanges()) {
    try {
      await ElMessageBox.confirm('有未保存的修改，确定放弃吗？', '确认', {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch { return }
  }
  editDialogVisible.value = false
}

// ---- Navigation ----
function goBack() {
  router.push({ name: 'SubjectList' })
}

function goEdit() {
  openEditDialog()
}

// ---- Init ----
onMounted(() => {
  loadSubject()
  loadPatientAccount()
  loadEditReferenceData()
  if (autoEdit.value) {
    // Auto-open edit dialog when coming from list "编辑" button
    // Need to wait for subject to load first
    const checkAndOpen = setInterval(() => {
      if (subject.value) {
        clearInterval(checkAndOpen)
        openEditDialog()
      }
    }, 200)
    // Safety timeout after 5 seconds
    setTimeout(() => clearInterval(checkAndOpen), 5000)
  }
})
</script>

<template>
  <div class="subject-detail-view">
    <!-- Top bar -->
    <div class="detail-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
      <span class="header-title" v-if="subject">
        受试者详情 - {{ subject.subjectId }}
      </span>
      <el-button type="primary" :icon="Edit" @click="goEdit">编辑</el-button>
      <el-button v-if="!patientAccount.created" type="success" @click="patientDialogVisible = true">
        开通患者账号
      </el-button>
      <el-tag v-else type="success">患者账号：{{ patientAccount.username }}</el-tag>
    </div>

    <!-- Loading state -->
    <div v-if="subjectLoading" class="loading-wrapper">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- Tabs -->
    <template v-else-if="subject">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="detail-tabs">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <div class="tab-panel">
            <el-descriptions :column="2" border>
              <el-descriptions-item
                v-for="field in subjectFields"
                :key="field.label"
                :label="field.label"
                :span="field.label === '受试者编号' || field.label === '外部编号' ? 1 : 1"
              >
                {{ field.value }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 访视记录 -->
        <el-tab-pane label="访视记录" name="sessions">
          <div class="tab-panel">
            <el-empty v-if="!sessions.length && loadedTabs.sessions" description="暂无访视记录" />
            <el-table v-else :data="sessions" stripe size="medium" style="width: 100%">
              <el-table-column prop="visitLabel" label="访视标签" min-width="120" />
              <el-table-column prop="visitDate" label="访视日期" min-width="130">
                <template #default="{ row }">
                  {{ row.visitDate || row.sessionDate || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag
                    :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 3: 量表评估 -->
        <el-tab-pane label="量表评估" name="assessments">
          <div class="tab-panel">
            <el-empty v-if="!assessments.length && loadedTabs.assessments" description="暂无量表评估记录" />
            <el-table v-else :data="assessments" stripe size="medium" style="width: 100%">
              <el-table-column prop="assessmentDate" label="评估日期" min-width="130" />
              <el-table-column label="量表名称" min-width="160">
                <template #default="{ row }">
                  {{ (row as any).instrumentName || row.instrumentId }}
                </template>
              </el-table-column>
              <el-table-column prop="totalScore" label="总分" width="100" />
              <el-table-column label="评估者" min-width="120">
                <template #default="{ row }">
                  {{ (row as any).examinerName || row.examinerId || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="dataEntryStatus" label="录入状态" width="110">
                <template #default="{ row }">
                  <el-tag
                    :type="row.dataEntryStatus === 'SUBMITTED' ? 'success' : row.dataEntryStatus === 'IN_PROGRESS' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.dataEntryStatus }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 4: 影像检查 -->
        <el-tab-pane label="影像检查" name="imaging">
          <div class="tab-panel">
            <el-empty v-if="!imagingSessions.length && loadedTabs.imaging" description="暂无影像检查记录" />
            <el-table v-else :data="imagingSessions" stripe size="medium" style="width: 100%">
              <el-table-column prop="acquisitionDate" label="采集日期" min-width="130" />
              <el-table-column label="模态" min-width="120">
                <template #default="{ row }">
                  {{ (row as any).modalityName || row.modalityId }}
                </template>
              </el-table-column>
              <el-table-column prop="seriesCount" label="序列数" width="90" />
              <el-table-column prop="qcStatus" label="质控状态" width="110">
                <template #default="{ row }">
                  <el-tag
                    :type="row.qcStatus === 'PASS' ? 'success' : row.qcStatus === 'FAIL' ? 'danger' : row.qcStatus === 'PENDING' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.qcStatus }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="preprocessingStatus" label="预处理状态" width="120">
                <template #default="{ row }">
                  <el-tag
                    :type="row.preprocessingStatus === 'COMPLETED' ? 'success' : row.preprocessingStatus === 'RUNNING' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.preprocessingStatus || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 5: 检验结果 -->
        <el-tab-pane label="检验结果" name="lab">
          <div class="tab-panel">
            <el-empty v-if="!labResults.length && loadedTabs.lab" description="暂无检验结果" />
            <el-table v-else :data="labResults" stripe size="medium" style="width: 100%">
              <el-table-column prop="testDate" label="检验日期" min-width="130" />
              <el-table-column prop="testName" label="检验项目" min-width="160" />
              <el-table-column prop="result" label="结果值" min-width="120" />
              <el-table-column prop="unit" label="单位" width="80" />
              <el-table-column label="参考范围" min-width="140">
                <template #default="{ row }">
                  {{ row.referenceRange || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="异常标识" width="100">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.abnormalFlag"
                    :type="row.abnormalFlag === 'H' || row.abnormalFlag === 'L' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.abnormalFlag === 'H' ? '偏高' : row.abnormalFlag === 'L' ? '偏低' : '异常' }}
                  </el-tag>
                  <span v-else>正常</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- Tab 6: 遗传数据 -->
        <el-tab-pane label="遗传数据" name="genetics">
          <div class="tab-panel" v-loading="geneticsLoading">
            <!-- Genetic samples -->
            <div class="genetics-section">
              <h4 class="subsection-title">遗传样本</h4>
              <el-empty
                v-if="!geneticSamples.length && loadedTabs.genetics"
                description="暂无遗传样本"
              />
              <el-table v-else :data="geneticSamples" stripe size="small" style="width: 100%">
                <el-table-column prop="sampleId" label="样本编号" min-width="140" />
                <el-table-column prop="sampleType" label="样本类型" min-width="120" />
                <el-table-column prop="collectionDate" label="采集日期" min-width="130" />
                <el-table-column prop="biobank" label="生物样本库" min-width="140" />
                <el-table-column prop="quality" label="质量" width="90" />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag
                      :type="row.status === 'AVAILABLE' ? 'success' : 'info'"
                      size="small"
                    >
                      {{ row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- Key variants -->
            <div class="genetics-section">
              <h4 class="subsection-title">关键变异位点</h4>
              <el-empty
                v-if="!geneticVariants.length && loadedTabs.genetics"
                description="暂无变异数据"
              />
              <el-table v-else :data="geneticVariants" stripe size="small" style="width: 100%">
                <el-table-column prop="gene" label="基因" min-width="120" />
                <el-table-column prop="variant" label="变异位点" min-width="160" />
                <el-table-column prop="chromosome" label="染色体" width="90" />
                <el-table-column prop="position" label="位置" width="120" />
                <el-table-column prop="refAllele" label="参考等位基因" width="120" />
                <el-table-column prop="altAllele" label="变异等位基因" width="120" />
                <el-table-column prop="clinicalSignificance" label="临床意义" min-width="140">
                  <template #default="{ row }">
                    <el-tag
                      :type="row.clinicalSignificance === 'PATHOGENIC' ? 'danger' : row.clinicalSignificance === 'BENIGN' ? 'success' : 'warning'"
                      size="small"
                    >
                      {{ row.clinicalSignificance }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="项目协作" name="collaboration">
          <div v-loading="collaborationLoading" class="collaboration-panel">
            <el-card shadow="never">
              <template #header>
                <div class="collaboration-header">
                  <div>
                    <strong>项目业务标签</strong>
                    <div class="section-help">同一项目成员共享；个人星标仍仅自己可见。</div>
                  </div>
                </div>
              </template>
              <el-checkbox-group v-model="selectedTagIds" class="project-tag-list">
                <el-checkbox
                  v-for="tag in availableTags"
                  :key="tag.id"
                  :value="tag.id"
                  border
                >
                  <span class="tag-color" :style="{ backgroundColor: tag.color }" />
                  {{ tag.name }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="availableTags.length === 0" description="当前项目尚未建立业务标签" :image-size="60" />
              <div class="new-tag-row">
                <el-input v-model="newTagName" maxlength="50" placeholder="新标签名称，如：待随访、失访风险" />
                <el-color-picker v-model="newTagColor" />
                <el-button @click="createProjectTag">创建并选中</el-button>
              </div>
            </el-card>

            <el-card shadow="never" class="note-card">
              <template #header>
                <div>
                  <strong>项目共享备注</strong>
                  <div class="section-help">每次保存都会生成新版本，不覆盖历史记录。</div>
                </div>
              </template>
              <el-input
                v-model="noteContent"
                type="textarea"
                :rows="5"
                maxlength="2000"
                show-word-limit
                placeholder="填写需要项目成员共同关注的随访、数据缺失或复核事项"
              />
              <div class="collaboration-actions">
                <el-button type="primary" :loading="collaborationSaving" @click="saveCollaboration">
                  保存标签和备注
                </el-button>
              </div>
              <el-timeline v-if="projectNotes.length" class="note-history">
                <el-timeline-item
                  v-for="note in projectNotes"
                  :key="note.id"
                  :timestamp="`${formatDateTime(note.createdAt)} · 用户 #${note.createdBy} · 版本 ${note.revisionNo}`"
                  placement="top"
                >
                  {{ note.content }}
                </el-timeline-item>
              </el-timeline>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- Error state -->
    <el-result
      v-else
      icon="error"
      title="加载失败"
      sub-title="无法加载受试者信息，请稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="loadSubject">重新加载</el-button>
      </template>
    </el-result>

    <!-- ===== Edit Dialog ===== -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑受试者信息"
      width="650px"
      :close-on-click-modal="false"
      @closed="handleEditCancel"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
        size="default"
      >
        <!-- 姓名 -->
        <el-form-item label="姓名">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-input v-model="editForm.lastName" placeholder="姓" clearable />
            </el-col>
            <el-col :span="12">
              <el-input v-model="editForm.firstName" placeholder="名" clearable />
            </el-col>
          </el-row>
        </el-form-item>

        <!-- 性别 -->
        <el-form-item label="性别" prop="sex">
          <el-radio-group v-model="editForm.sex">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
            <el-radio value="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 出生日期 -->
        <el-form-item label="出生日期" prop="dateOfBirth">
          <el-date-picker
            v-model="editForm.dateOfBirth"
            type="date"
            placeholder="请选择出生日期"
            :disabled-date="disabledDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 教育程度 -->
        <el-form-item label="受教育年限">
          <el-input-number v-model="editForm.educationYears" :min="0" :max="30" placeholder="受教育年数" style="width: 100%" />
          <span class="form-tip">单位：年</span>
        </el-form-item>

        <!-- 民族 -->
        <el-form-item label="民族">
          <el-select v-model="editForm.ethnicity" placeholder="请选择民族" clearable filterable style="width: 100%">
            <el-option v-for="item in ethnicityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <!-- 利手 -->
        <el-form-item label="利手">
          <el-radio-group v-model="editForm.handedness">
            <el-radio value="RIGHT">右利手</el-radio>
            <el-radio value="LEFT">左利手</el-radio>
            <el-radio value="BOTH">双利手</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 联系电话 -->
        <el-form-item label="联系电话">
          <el-input v-model="editForm.phone" placeholder="请输入联系电话" clearable />
        </el-form-item>

        <!-- 婚姻状况 -->
        <el-form-item label="婚姻状况">
          <el-select v-model="editForm.maritalStatusCodeId" placeholder="请选择婚姻状况" clearable style="width: 100%">
            <el-option v-for="item in editMaritalStatusOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <!-- 身高 / 体重 -->
        <el-form-item label="身高">
          <el-input-number v-model="editForm.heightCm" :min="50" :max="250" :step="0.1" placeholder="cm" style="width: 100%" />
          <span class="form-tip">cm</span>
        </el-form-item>
        <el-form-item label="体重">
          <el-input-number v-model="editForm.weightKg" :min="20" :max="300" :step="0.1" placeholder="kg" style="width: 100%" />
          <span class="form-tip">kg</span>
        </el-form-item>

        <!-- 血型 -->
        <el-form-item label="血型">
          <el-select v-model="editForm.bloodTypeCodeId" placeholder="请选择血型" clearable style="width: 100%">
            <el-option v-for="item in editBloodTypeOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <!-- 居住地址 -->
        <el-form-item label="居住城市">
          <el-input v-model="editForm.addressCity" placeholder="请输入居住城市" clearable />
        </el-form-item>
        <el-form-item label="居住区县">
          <el-input v-model="editForm.addressDistrict" placeholder="请输入居住区县" clearable />
        </el-form-item>

        <!-- 知情同意 -->
        <el-form-item label="知情同意">
          <el-switch v-model="editForm.isConsented" active-text="已签署" inactive-text="未签署" />
          <span v-if="editForm.isConsented" style="margin-left:12px">
            <el-date-picker v-model="editForm.consentDate" type="date" placeholder="签署日期" value-format="YYYY-MM-DD" style="width:180px" />
          </span>
        </el-form-item>

        <!-- 入组日期 -->
        <el-form-item label="入组日期">
          <el-date-picker v-model="editForm.enrollmentDate" type="date" placeholder="选择入组日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <!-- 备注 -->
        <el-form-item label="备注">
          <el-input v-model="editForm.remarks" type="textarea" :rows="3" placeholder="请输入备注信息" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleEditCancel">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSubmit">
          {{ editSaving ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
    <el-dialog v-model="patientDialogVisible" title="开通患者登录账号" width="520px">
      <el-alert
        title="该账号只绑定当前受试者，登录后只能看到并填写自己的量表任务。"
        type="info"
        :closable="false"
        style="margin-bottom: 18px"
      />
      <el-form label-width="100px">
        <el-form-item label="患者姓名"><el-input v-model="patientForm.realName" /></el-form-item>
        <el-form-item label="登录名" required><el-input v-model="patientForm.username" /></el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="patientForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="patientDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="patientSaving" @click="createPatientAccount">确认开通</el-button>
      </template>
    </el-dialog>
</template>

<script lang="ts">
export default {
  name: 'SubjectDetailView',
}
</script>

<style scoped lang="scss">
.collaboration-panel {
  display: grid;
  gap: 16px;

  .section-help {
    margin-top: 5px;
    color: #4b5563;
    font-size: 13px;
    font-weight: 400;
  }

  .project-tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .tag-color {
    display: inline-block;
    width: 9px;
    height: 9px;
    margin-right: 5px;
    border-radius: 50%;
  }

  .new-tag-row {
    display: grid;
    grid-template-columns: minmax(180px, 360px) auto auto;
    align-items: center;
    gap: 10px;
    margin-top: 18px;
  }

  .note-card {
    .collaboration-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 14px;
    }

    .note-history {
      margin-top: 24px;
      color: #374151;
      white-space: pre-wrap;
    }
  }
}

.subject-detail-view {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  min-height: calc(100vh - 60px - 40px - 40px);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;

  .header-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.loading-wrapper {
  padding: 40px 0;
}

.detail-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}

.tab-panel {
  padding: 4px 0;
}

.subsection-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid #409eff;
}

.genetics-section {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.form-tip {
  margin-left: 10px;
  font-size: 13px;
  color: #909399;
}
</style>
