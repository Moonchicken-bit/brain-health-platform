<script setup lang="ts">
import { ref, reactive, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  UploadFilled,
  Upload,
  CircleCheck,
  CircleClose,
  Clock,
  Loading,
  InfoFilled,
  Document,
  WarningFilled,
} from '@element-plus/icons-vue'
import http from '@/api/client'
import { geneticsApi } from '@/api/modules/genetics'
import type { VariantSummary, QcFilterResult, ChromosomeVariantCount } from '@/api/modules/genetics'
import { subjectApi } from '@/api/modules/subject'

// ---- Types ----
interface SubjectOption {
  id: number
  subjectId: string
  sex: string
  dateOfBirth?: string
}

interface UploadFileItem {
  uid: string
  name: string
  size: number
  raw: File
  status: 'pending' | 'uploading' | 'success' | 'failed' | 'paused'
  progress: number
  uploadId?: string
  errorMsg?: string
}

interface PlatformOption {
  id: number
  name: string
  label: string
  description?: string
}

interface ReferenceGenomeOption {
  id: number
  name: string
  label: string
  description?: string
}

interface DynamicField {
  id: number
  fieldCode: string
  label: string
  description?: string
  fieldType: string
  unit?: string
  defaultValue?: string
  optionsJson?: string
  requiredFlag: boolean
}

// ---- Constants ----
const CHUNK_SIZE = 20 * 1024 * 1024 // 20MB per chunk
const CHUNKED_UPLOAD_THRESHOLD = 50 * 1024 * 1024 // Keep direct uploads below server request limit
const ACCEPTED_FILE_TYPES = '.vcf,.vcf.gz,.vcf.bgz'
const ACCEPTED_EXTENSIONS = new Set(['vcf', 'gz', 'bgz'])

// ---- Router ----
const router = useRouter()

// ---- Step state ----
const currentStep = ref(1) // 1: select subject/session, 2: upload, 3: review

// ---- Subject & session selection ----
const subjects = ref<SubjectOption[]>([])
const subjectsLoading = ref(false)

const selectedSubjectId = ref<number | null>(null)
const selectedSessionId = ref<number | null>(null)

const sessions = ref<{ id: number; visitLabel: string; status: string }[]>([])
const sessionsLoading = ref(false)

// New session creation
const createNewSession = ref(false)
const newSessionLabel = ref('')
const newSessionDate = ref('')

// ---- Platform & reference genome ----
const platforms = ref<PlatformOption[]>([])
const platformsLoading = ref(false)
const selectedPlatform = ref<string>('')

const referenceGenomes = ref<ReferenceGenomeOption[]>([])
const referenceGenomesLoading = ref(false)
const selectedReferenceGenome = ref<string>('')
const dynamicFields = ref<DynamicField[]>([])
const dynamicValues = reactive<Record<string, any>>({})

// ---- Upload state ----
const uploadQueue = ref<UploadFileItem[]>([])
const uploading = ref(false)
const uploadSpeed = ref('')
const overallProgress = ref(0)
const totalBytes = ref(0)
const uploadedBytes = ref(0)
const cancelTokenSources = new Map<string, AbortController>()

// ---- Parsing state ----
const parsing = ref(false)
const parsingProgress = ref(0)
const parsingMessage = ref('')
const variantSummary = ref<VariantSummary | null>(null)
const sampleId = ref<number | null>(null)

// ---- Computed ----
const hasFiles = computed(() => uploadQueue.value.length > 0)
const allDone = computed(() =>
  uploadQueue.value.length > 0 &&
  uploadQueue.value.every((f) => f.status === 'success')
)
const hasFailures = computed(() =>
  uploadQueue.value.some((f) => f.status === 'failed')
)
const canUpload = computed(() =>
  selectedSubjectId.value &&
  selectedSessionId.value &&
  selectedPlatform.value &&
  selectedReferenceGenome.value &&
  hasFiles.value &&
  !uploading.value
)

const selectedSubject = computed(() =>
  subjects.value.find((s) => s.id === selectedSubjectId.value)
)
const dynamicFieldsValid = computed(() => dynamicFields.value.every(field => {
  if (!field.requiredFlag) return true
  const value = dynamicValues[field.fieldCode]
  return value !== undefined && value !== null && value !== ''
}))

// ---- Lifecycle ----
onMounted(() => {
  fetchPlatforms()
  fetchReferenceGenomes()
  fetchDynamicFields()
})

async function fetchDynamicFields() {
  const response = await http.get('/api/v1/genetics/dynamic-fields')
  dynamicFields.value = response.data?.data || []
  for (const field of dynamicFields.value) {
    if (dynamicValues[field.fieldCode] === undefined && field.defaultValue != null) {
      dynamicValues[field.fieldCode] = field.defaultValue
    }
  }
}

function fieldOptions(field: DynamicField): string[] {
  if (!field.optionsJson) return []
  try {
    const parsed = typeof field.optionsJson === 'string'
      ? JSON.parse(field.optionsJson) : field.optionsJson
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function saveDynamicValues() {
  if (!sampleId.value || !dynamicFields.value.length) return
  await http.put(`/api/v1/genetics/samples/${sampleId.value}/dynamic-values`, dynamicValues)
}

// ---- Subject search ----
async function searchSubjects(query: string) {
  if (!query) {
    subjects.value = []
    return
  }
  subjectsLoading.value = true
  try {
    const res = await subjectApi.list({ keyword: query, size: 20 })
    const data = res.data?.data
    subjects.value = (data?.records || []).map((r: any) => ({
      id: r.id,
      subjectId: r.subjectId,
      sex: r.sex,
      dateOfBirth: r.dateOfBirth,
    }))
  } catch {
    subjects.value = []
  } finally {
    subjectsLoading.value = false
  }
}

function onSubjectSelect(id: number) {
  selectedSubjectId.value = id
  selectedSessionId.value = null
  sessions.value = []
  fetchSessions()
}

// ---- Session fetching ----
async function fetchSessions() {
  if (!selectedSubjectId.value) return
  sessionsLoading.value = true
  try {
    const res = await subjectApi.getSessions(selectedSubjectId.value)
    sessions.value = (res.data?.data || []).map((s: any) => ({
      id: s.id,
      visitLabel: s.visitLabel || s.id,
      status: s.status,
    }))
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

// ---- Create new session ----
async function handleCreateSession() {
  if (!selectedSubjectId.value || !newSessionLabel.value || !newSessionDate.value) {
    ElMessage.warning('请填写访视标签和日期')
    return
  }
  try {
    const res = await http.post('/api/v1/sessions', {
      subjectId: selectedSubjectId.value,
      visitLabel: newSessionLabel.value,
      visitDate: newSessionDate.value,
      status: 'IN_PROGRESS',
    })
    const data = res.data?.data
    if (data?.id) {
      selectedSessionId.value = data.id
      ElMessage.success('访视创建成功')
      createNewSession.value = false
      newSessionLabel.value = ''
      newSessionDate.value = ''
      await fetchSessions()
    }
  } catch {
    // handled by interceptor
  }
}

// ---- Platform & reference genome ----
async function fetchPlatforms() {
  platformsLoading.value = true
  try {
    const res = await geneticsApi.getPlatforms()
    platforms.value = res.data?.data || []
  } catch {
    // Use fallback defaults
    platforms.value = [
      { id: 1, name: 'ILLUMINA', label: 'Illumina' },
      { id: 2, name: 'BGI', label: '华大智造 (BGI/MGI)' },
      { id: 3, name: 'ONT', label: 'Oxford Nanopore' },
      { id: 4, name: 'PACBIO', label: 'PacBio' },
      { id: 5, name: 'ION_TORRENT', label: 'Ion Torrent' },
    ]
  } finally {
    platformsLoading.value = false
  }
}

async function fetchReferenceGenomes() {
  referenceGenomesLoading.value = true
  try {
    const res = await geneticsApi.getReferenceGenomes()
    referenceGenomes.value = res.data?.data || []
  } catch {
    // Use fallback defaults
    referenceGenomes.value = [
      { id: 1, name: 'GRCh37', label: 'GRCh37 / hg19' },
      { id: 2, name: 'GRCh38', label: 'GRCh38 / hg38' },
      { id: 3, name: 'T2T-CHM13', label: 'T2T-CHM13' },
    ]
  } finally {
    referenceGenomesLoading.value = false
  }
}

// ---- File helpers ----
function getFileExtension(name: string): string {
  const lower = name.toLowerCase()
  if (lower.endsWith('.vcf.gz')) return 'vcf.gz'
  if (lower.endsWith('.vcf.bgz')) return 'vcf.bgz'
  const parts = name.split('.')
  return parts.length > 1 ? parts.pop()!.toLowerCase() : ''
}

function isValidFileType(name: string): boolean {
  const ext = getFileExtension(name)
  return ACCEPTED_EXTENSIONS.has(ext) || name.toLowerCase().endsWith('.vcf.gz') || name.toLowerCase().endsWith('.vcf.bgz')
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function generateUploadId(): string {
  return Date.now().toString(36) + '-' + Math.random().toString(36).substring(2, 10)
}

// ---- File selection ----
function handleFilesAdded(files: FileList | File[]) {
  const arr = Array.from(files)
  const valid: File[] = []
  const rejected: string[] = []

  for (const f of arr) {
    if (isValidFileType(f.name)) {
      valid.push(f)
    } else {
      rejected.push(f.name)
    }
  }

  if (rejected.length) {
    ElMessage.warning(`以下文件类型不支持，已跳过: ${rejected.join(', ')}`)
  }

  const newItems: UploadFileItem[] = valid.map((f) => ({
    uid: generateUploadId(),
    name: f.name,
    size: f.size,
    raw: f,
    status: 'pending',
    progress: 0,
  }))

  uploadQueue.value.push(...newItems)
  updateOverallProgress()
}

function removeFile(uid: string) {
  const idx = uploadQueue.value.findIndex((f) => f.uid === uid)
  if (idx === -1) return
  const item = uploadQueue.value[idx]
  if (item.status === 'uploading') {
    const ctrl = cancelTokenSources.get(uid)
    if (ctrl) {
      ctrl.abort()
      cancelTokenSources.delete(uid)
    }
  }
  uploadQueue.value.splice(idx, 1)
  updateOverallProgress()
}

function clearQueue() {
  if (uploading.value) return
  uploadQueue.value = []
  overallProgress.value = 0
  totalBytes.value = 0
  uploadedBytes.value = 0
}

function updateOverallProgress() {
  totalBytes.value = uploadQueue.value.reduce((sum, f) => sum + f.size, 0)
  const done = uploadQueue.value.reduce((sum, f) => {
    if (f.status === 'success') return sum + f.size
    if (f.status === 'uploading') return sum + Math.round(f.size * f.progress / 100)
    return sum
  }, 0)
  uploadedBytes.value = done
  overallProgress.value = totalBytes.value > 0 ? Math.round((done / totalBytes.value) * 100) : 0
}

// ---- Upload logic ----
async function startUpload() {
  if (!canUpload.value) return

  uploading.value = true
  uploadSpeed.value = ''

  const pendingFiles = uploadQueue.value.filter(
    (f) => f.status === 'pending' || f.status === 'failed' || f.status === 'paused'
  )
  pendingFiles.forEach((f) => {
    f.status = 'pending'
    f.progress = 0
    f.errorMsg = undefined
  })

  for (const file of pendingFiles) {
    const ctrl = new AbortController()
    cancelTokenSources.set(file.uid, ctrl)

    try {
      if (file.size >= CHUNKED_UPLOAD_THRESHOLD) {
        await uploadFileInChunks(file, ctrl)
      } else {
        await uploadFileDirect(file, ctrl)
      }
      file.status = 'success'
      file.progress = 100
    } catch (err: any) {
      if (err?.name === 'CanceledError' || err?.message === 'canceled') {
        file.status = 'paused'
      } else {
        file.status = 'failed'
        file.errorMsg = err?.message || '上传失败'
      }
    } finally {
      cancelTokenSources.delete(file.uid)
      updateOverallProgress()
    }
  }

  uploading.value = false

  if (allDone.value) {
    ElMessage.success('所有文件上传完成')
    await triggerParsing()
  } else if (hasFailures.value) {
    ElMessage.warning('部分文件上传失败，请检查后重试')
  }
}

async function uploadFileDirect(file: UploadFileItem, ctrl: AbortController) {
  file.status = 'uploading'
  const startTime = Date.now()

  const formData = new FormData()
  formData.append('file', file.raw)
  formData.append('subjectId', String(selectedSubjectId.value))
  formData.append('sessionId', String(selectedSessionId.value))
  formData.append('platform', selectedPlatform.value)
  formData.append('referenceGenome', selectedReferenceGenome.value)

  try {
    await http.post('/api/v1/genetics/samples/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000,
      signal: ctrl.signal,
      onUploadProgress: (e) => {
        if (e.total) {
          file.progress = Math.round((e.loaded * 100) / e.total)
          const elapsed = (Date.now() - startTime) / 1000
          if (elapsed > 0) {
            uploadSpeed.value = formatFileSize(e.loaded / elapsed) + '/s'
          }
          updateOverallProgress()
        }
      },
    })
  } catch (err: any) {
    throw err
  }
}

async function uploadFileInChunks(file: UploadFileItem, ctrl: AbortController) {
  file.status = 'uploading'
  file.uploadId = file.uploadId || generateUploadId()
  const uploadId = file.uploadId

  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  const startTime = Date.now()

  for (let i = 0; i < totalChunks; i++) {
    if (ctrl.signal.aborted) throw new Error('canceled')

    const start = i * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, file.size)
    const chunk = file.raw.slice(start, end)

    const chunkForm = new FormData()
    chunkForm.append('file', chunk, file.name)
    chunkForm.append('uploadId', uploadId)
    chunkForm.append('chunkIndex', String(i))
    chunkForm.append('totalChunks', String(totalChunks))
    chunkForm.append('fileName', file.name)
    chunkForm.append('fileSize', String(file.size))
    chunkForm.append('subjectId', String(selectedSubjectId.value))
    chunkForm.append('sessionId', String(selectedSessionId.value))
    chunkForm.append('platform', selectedPlatform.value)
    chunkForm.append('referenceGenome', selectedReferenceGenome.value)

    await geneticsApi.uploadChunk(chunkForm)

    file.progress = Math.round(((i + 1) / totalChunks) * 100)
    const elapsed = (Date.now() - startTime) / 1000
    const uploaded = (i + 1) * CHUNK_SIZE
    if (elapsed > 0) {
      uploadSpeed.value = formatFileSize(Math.min(uploaded, file.size) / elapsed) + '/s'
    }
    updateOverallProgress()
  }

  await geneticsApi.mergeChunks({
    uploadId,
    fileName: file.name,
    totalChunks,
    fileSize: file.size,
    subjectId: selectedSubjectId.value!,
    sessionId: selectedSessionId.value!,
    platform: selectedPlatform.value,
    referenceGenome: selectedReferenceGenome.value,
  })
}

// ---- Retry single file ----
async function retryFile(uid: string) {
  const file = uploadQueue.value.find((f) => f.uid === uid)
  if (!file || uploading.value) return

  file.status = 'pending'
  file.progress = 0
  file.errorMsg = undefined

  uploading.value = true
  const ctrl = new AbortController()
  cancelTokenSources.set(uid, ctrl)

  try {
    if (file.size >= CHUNKED_UPLOAD_THRESHOLD) {
      await uploadFileInChunks(file, ctrl)
    } else {
      await uploadFileDirect(file, ctrl)
    }
    file.status = 'success'
    file.progress = 100
  } catch (err: any) {
    if (err?.name === 'CanceledError' || err?.message === 'canceled') {
      file.status = 'paused'
    } else {
      file.status = 'failed'
      file.errorMsg = err?.message || '重试失败'
    }
  } finally {
    cancelTokenSources.delete(uid)
    uploading.value = false
    updateOverallProgress()
  }

  if (allDone.value) {
    ElMessage.success('所有文件上传完成')
    await triggerParsing()
  }
}

// ---- Retry all failed ----
async function retryAllFailed() {
  const failedFiles = uploadQueue.value.filter((f) => f.status === 'failed')
  for (const file of failedFiles) {
    file.status = 'pending'
    file.progress = 0
    file.errorMsg = undefined
  }

  uploading.value = true
  for (const file of failedFiles) {
    const ctrl = new AbortController()
    cancelTokenSources.set(file.uid, ctrl)
    try {
      if (file.size >= CHUNKED_UPLOAD_THRESHOLD) {
        await uploadFileInChunks(file, ctrl)
      } else {
        await uploadFileDirect(file, ctrl)
      }
      file.status = 'success'
      file.progress = 100
    } catch (err: any) {
      if (err?.name === 'CanceledError' || err?.message === 'canceled') {
        file.status = 'paused'
      } else {
        file.status = 'failed'
        file.errorMsg = err?.message || '重试失败'
      }
    } finally {
      cancelTokenSources.delete(file.uid)
      updateOverallProgress()
    }
  }
  uploading.value = false

  if (allDone.value) {
    ElMessage.success('所有文件上传完成')
    await triggerParsing()
  }
}

// ---- VCF parsing & variant summary ----
async function triggerParsing() {
  parsing.value = true
  parsingProgress.value = 0
  parsingMessage.value = '正在提交解析任务...'

  try {
    // Fetch the most recent sample for this session
    const samplesRes = await geneticsApi.listSamples({
      subjectId: selectedSubjectId.value!,
      sessionId: selectedSessionId.value!,
      size: 1,
    })
    const records = samplesRes.data?.data?.records || []
    if (records.length > 0) {
      sampleId.value = records[0].id!
    }

    // Trigger server-side parsing if sample exists
    if (sampleId.value) {
      parsingProgress.value = 20
      parsingMessage.value = '服务器正在逐行解析 VCF 变异记录...'
      await geneticsApi.triggerParsing(sampleId.value)

      // Fetch variant summary
      parsingProgress.value = 90
      const summaryRes = await geneticsApi.getVariantSummary(sampleId.value)
      variantSummary.value = summaryRes.data?.data || null
    } else {
      throw new Error('上传完成后未取得遗传样本 ID，无法开始解析')
    }

    parsingProgress.value = 100
    parsingMessage.value = '解析完成'
    await saveDynamicValues()
    currentStep.value = 3
    ElMessage.success('遗传数据解析完成')
  } catch (err: any) {
    variantSummary.value = null
    parsingProgress.value = 0
    parsingMessage.value = err?.response?.data?.message || err?.message || 'VCF 解析失败'
    ElMessage.error(parsingMessage.value)
  } finally {
    parsing.value = false
  }
}

// ---- Drag & drop ----
const uploadAreaRef = ref<HTMLElement>()
const isDragover = ref(false)

function onDragOver(e: DragEvent) {
  e.preventDefault()
  isDragover.value = true
}

function onDragLeave() {
  isDragover.value = false
}

async function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragover.value = false
  if (!e.dataTransfer?.items) return
  const files: File[] = []
  for (let i = 0; i < e.dataTransfer.items.length; i++) {
    const file = e.dataTransfer.items[i].getAsFile()
    if (file) files.push(file)
  }
  if (files.length) {
    handleFilesAdded(files)
  }
}

// ---- Step navigation ----
function goToStep(step: number) {
  if (step === 2) {
    if (!selectedSubjectId.value || !selectedSessionId.value) {
      ElMessage.warning('请先完成受试者和访视选择')
      return
    }
    if (!selectedPlatform.value || !selectedReferenceGenome.value) {
      ElMessage.warning('请选择测序平台和参考基因组')
      return
    }
  }
  currentStep.value = step
}

function goBack() {
  if (currentStep.value === 3) {
    currentStep.value = 2
  } else if (currentStep.value === 2) {
    currentStep.value = 1
  } else {
    router.back()
  }
}

function goToSampleDetail() {
  if (sampleId.value) {
    // Navigate to genetics sample detail if route exists
    router.push({ name: 'GeneticsSampleList' })
  }
}

function startNewUpload() {
  currentStep.value = 1
  uploadQueue.value = []
  variantSummary.value = null
  sampleId.value = null
  selectedSubjectId.value = null
  selectedSessionId.value = null
  selectedPlatform.value = ''
  selectedReferenceGenome.value = ''
  overallProgress.value = 0
  totalBytes.value = 0
  uploadedBytes.value = 0
}

// ---- QC filter helpers ----
function qcFilterTagType(status: string): '' | 'success' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger'> = {
    PASS: 'success', WARN: 'warning', FAIL: 'danger',
  }
  return map[status] || ''
}

function qcFilterLabel(status: string): string {
  const map: Record<string, string> = {
    PASS: '通过', WARN: '警告', FAIL: '未通过',
  }
  return map[status] || status
}

// ---- Cleanup ----
onBeforeUnmount(() => {
  for (const ctrl of cancelTokenSources.values()) {
    ctrl.abort()
  }
  cancelTokenSources.clear()
})

// ---- el-upload handler ----
const uploadRef = ref<any>(null)

function handleExceed() {
  ElMessage.warning('文件数量超出限制')
}
</script>

<template>
  <div class="genetics-upload-view">
    <!-- Back navigation -->
    <div class="page-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <h2 class="page-title">遗传数据上传</h2>

    <!-- Steps indicator -->
    <el-steps :active="currentStep - 1" align-center class="upload-steps" finish-status="success">
      <el-step title="选择受试者与参数" />
      <el-step title="上传 VCF 文件" />
      <el-step title="解析结果" />
    </el-steps>

    <!-- ===== Step 1: Select subject, session, platform, reference ===== -->
    <el-card v-show="currentStep === 1" class="step-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>选择受试者与遗传参数</span>
        </div>
      </template>

      <el-form label-width="110px" class="selection-form">
        <!-- Subject select -->
        <el-form-item label="受试者">
          <el-select
            :model-value="selectedSubjectId"
            placeholder="请输入受试者编号搜索"
            filterable
            remote
            :remote-method="searchSubjects"
            :loading="subjectsLoading"
            clearable
            style="width: 360px"
            @change="onSubjectSelect"
          >
            <el-option
              v-for="s in subjects"
              :key="s.id"
              :label="`${s.subjectId} (${s.sex === 'MALE' ? '男' : s.sex === 'FEMALE' ? '女' : '其他'})`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <!-- Selected subject info -->
        <el-form-item v-if="selectedSubject" label="已选受试者">
          <el-tag type="info" size="large">
            {{ selectedSubject.subjectId }}
            ({{ selectedSubject.sex === 'MALE' ? '男' : selectedSubject.sex === 'FEMALE' ? '女' : '其他' }})
          </el-tag>
        </el-form-item>

        <!-- Session select -->
        <el-form-item v-if="selectedSubjectId" label="访视">
          <div class="session-row">
            <el-select
              v-model="selectedSessionId"
              placeholder="选择已有访视"
              :loading="sessionsLoading"
              clearable
              :disabled="createNewSession"
              style="width: 280px"
            >
              <el-option
                v-for="s in sessions"
                :key="s.id"
                :label="s.visitLabel"
                :value="s.id"
              >
                <span>{{ s.visitLabel }}</span>
                <el-tag size="small" style="margin-left: 8px" :type="s.status === 'COMPLETED' ? 'success' : 'warning'">
                  {{ s.status }}
                </el-tag>
              </el-option>
            </el-select>
            <el-button text type="primary" size="small" @click="createNewSession = !createNewSession">
              {{ createNewSession ? '选择已有访视' : '新建访视' }}
            </el-button>
          </div>
        </el-form-item>

        <!-- New session fields -->
        <template v-if="createNewSession && selectedSubjectId">
          <el-form-item label="访视标签" required>
            <el-input
              v-model="newSessionLabel"
              placeholder="例如: V1, Baseline, 初次访视"
              style="max-width: 280px"
              clearable
            />
          </el-form-item>
          <el-form-item label="访视日期" required>
            <el-date-picker
              v-model="newSessionDate"
              type="date"
              placeholder="选择访视日期"
              value-format="YYYY-MM-DD"
              style="max-width: 280px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" @click="handleCreateSession">
              创建访视
            </el-button>
          </el-form-item>
        </template>

        <el-divider />

        <!-- Platform selection -->
        <el-form-item label="测序平台" required>
          <el-select
            v-model="selectedPlatform"
            placeholder="选择测序平台"
            :loading="platformsLoading"
            style="width: 360px"
          >
            <el-option
              v-for="p in platforms"
              :key="p.id"
              :label="p.label"
              :value="p.name"
            >
              <span>{{ p.label }}</span>
              <span v-if="p.description" style="font-size: 12px; color: #909399; margin-left: 8px">
                {{ p.description }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- Reference genome selection -->
        <el-form-item label="参考基因组" required>
          <el-select
            v-model="selectedReferenceGenome"
            placeholder="选择参考基因组版本"
            :loading="referenceGenomesLoading"
            style="width: 360px"
          >
            <el-option
              v-for="rg in referenceGenomes"
              :key="rg.id"
              :label="rg.label"
              :value="rg.name"
            />
          </el-select>
        </el-form-item>

        <template v-if="dynamicFields.length">
          <el-divider content-position="left">管理员配置的扩展字段</el-divider>
          <el-form-item
            v-for="field in dynamicFields"
            :key="field.id"
            :label="field.label"
            :required="Boolean(field.requiredFlag)"
          >
            <el-input
              v-if="field.fieldType === 'TEXT' || field.fieldType === 'TEXTAREA'"
              v-model="dynamicValues[field.fieldCode]"
              :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
              :placeholder="field.description"
              style="width: 360px"
            >
              <template v-if="field.unit" #append>{{ field.unit }}</template>
            </el-input>
            <el-input-number
              v-else-if="field.fieldType === 'NUMBER'"
              v-model="dynamicValues[field.fieldCode]"
            />
            <el-date-picker
              v-else-if="field.fieldType === 'DATE'"
              v-model="dynamicValues[field.fieldCode]"
              value-format="YYYY-MM-DD"
            />
            <el-select
              v-else-if="field.fieldType === 'SELECT'"
              v-model="dynamicValues[field.fieldCode]"
              style="width: 360px"
            >
              <el-option v-for="option in fieldOptions(field)" :key="option" :label="option" :value="option" />
            </el-select>
            <el-select
              v-else-if="field.fieldType === 'MULTI_SELECT'"
              v-model="dynamicValues[field.fieldCode]"
              multiple
              style="width: 360px"
            >
              <el-option v-for="option in fieldOptions(field)" :key="option" :label="option" :value="option" />
            </el-select>
            <el-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model="dynamicValues[field.fieldCode]" />
          </el-form-item>
        </template>

        <el-form-item>
          <el-alert type="info" :closable="false" show-icon style="max-width: 520px">
            <template #title>
              支持的格式: VCF v4.0+ (.vcf), 压缩格式 (.vcf.gz, .vcf.bgz)
            </template>
            <template #default>
              请确保 VCF 文件中已包含样本基因型信息。文件大小建议不超过 10GB，超过 100MB 将自动启用分块上传。
            </template>
          </el-alert>
        </el-form-item>
      </el-form>

      <div class="step-actions">
        <el-button type="primary" :disabled="!selectedSubjectId || !selectedSessionId || !selectedPlatform || !selectedReferenceGenome || !dynamicFieldsValid" @click="goToStep(2)">
          下一步：上传文件
        </el-button>
      </div>
    </el-card>

    <!-- ===== Step 2: Upload files ===== -->
    <div v-show="currentStep === 2">
      <!-- Selected info bar -->
      <el-card v-if="selectedSubject" class="info-bar-card" shadow="never">
        <div class="info-bar">
          <span class="info-item">
            <strong>受试者:</strong> {{ selectedSubject.subjectId }}
          </span>
          <span class="info-item">
            <strong>平台:</strong> {{ platforms.find(p => p.name === selectedPlatform)?.label || selectedPlatform }}
          </span>
          <span class="info-item">
            <strong>参考基因组:</strong> {{ referenceGenomes.find(r => r.name === selectedReferenceGenome)?.label || selectedReferenceGenome }}
          </span>
          <el-button text type="primary" size="small" @click="goToStep(1)">
            修改
          </el-button>
        </div>
      </el-card>

      <!-- Drop zone -->
      <el-card class="upload-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>选择 VCF 文件</span>
            <span class="card-header-tip">
              支持 .vcf / .vcf.gz / .vcf.bgz
            </span>
          </div>
        </template>

        <div
          ref="uploadAreaRef"
          class="drop-zone"
          :class="{ 'is-dragover': isDragover }"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <el-upload
            ref="uploadRef"
            drag
            multiple
            :auto-upload="false"
            :show-file-list="false"
            :accept="ACCEPTED_FILE_TYPES"
            :on-exceed="handleExceed"
            @change="(_file: any, fileList: any[]) => {
              const rawFiles = fileList.map((f: any) => f.raw).filter(Boolean)
              if (rawFiles.length) handleFilesAdded(rawFiles)
              uploadRef?.clearFiles()
            }"
          >
            <div class="upload-trigger">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">
                <span>拖拽 VCF 文件到此区域，或</span>
                <em>点击选择文件</em>
              </div>
              <div class="upload-hint">
                单文件大小建议不超过 10GB；超过 100MB 将自动分块上传
              </div>
            </div>
          </el-upload>
        </div>
      </el-card>

      <!-- Upload queue -->
      <el-card v-if="hasFiles" class="queue-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>上传队列 ({{ uploadQueue.length }} 个文件)</span>
            <div class="queue-header-actions">
              <span v-if="uploading || overallProgress > 0" class="overall-progress">
                总进度: {{ overallProgress }}%
              </span>
              <span v-if="uploadSpeed" class="upload-speed">
                {{ uploadSpeed }}
              </span>
              <el-button text size="small" type="danger" @click="clearQueue" :disabled="uploading">
                清空队列
              </el-button>
            </div>
          </div>
        </template>

        <!-- Overall progress bar -->
        <div v-if="totalBytes > 0" class="overall-bar">
          <el-progress
            :percentage="overallProgress"
            :status="allDone ? 'success' : hasFailures ? 'exception' : undefined"
            :stroke-width="8"
          />
          <div class="overall-size">
            {{ formatFileSize(uploadedBytes) }} / {{ formatFileSize(totalBytes) }}
          </div>
        </div>

        <!-- File list -->
        <div class="file-list">
          <div
            v-for="file in uploadQueue"
            :key="file.uid"
            class="file-item"
          >
            <div class="file-info">
              <div class="file-name">
                <el-tag size="small" type="info" class="file-type-tag">VCF</el-tag>
                <span class="file-name-text">{{ file.name }}</span>
              </div>
              <div class="file-meta">
                <span class="file-size">{{ formatFileSize(file.size) }}</span>
                <span v-if="file.size >= CHUNKED_UPLOAD_THRESHOLD" class="chunk-badge">分块上传</span>
              </div>
            </div>
            <div class="file-progress">
              <el-progress
                :percentage="file.progress"
                :status="file.status === 'success' ? 'success' : file.status === 'failed' ? 'exception' : undefined"
                :stroke-width="6"
                style="flex: 1"
              />
            </div>
            <div class="file-status">
              <template v-if="file.status === 'pending'">
                <el-icon class="status-icon pending"><Clock /></el-icon>
                <span class="status-text">等待中</span>
              </template>
              <template v-else-if="file.status === 'uploading'">
                <el-icon class="status-icon uploading is-loading"><Loading /></el-icon>
                <span class="status-text">{{ file.size >= CHUNKED_UPLOAD_THRESHOLD ? '分块上传中' : '上传中' }}</span>
              </template>
              <template v-else-if="file.status === 'success'">
                <el-icon class="status-icon success"><CircleCheck /></el-icon>
                <span class="status-text success">完成</span>
              </template>
              <template v-else-if="file.status === 'failed'">
                <el-icon class="status-icon failed"><CircleClose /></el-icon>
                <span class="status-text failed" :title="file.errorMsg">{{ file.errorMsg || '失败' }}</span>
                <el-button text type="primary" size="small" @click="retryFile(file.uid)">
                  重试
                </el-button>
              </template>
              <template v-else-if="file.status === 'paused'">
                <el-icon class="status-icon"><CircleClose /></el-icon>
                <span class="status-text">已取消</span>
              </template>
            </div>
            <div class="file-actions">
              <el-button
                v-if="file.status !== 'uploading'"
                text
                type="danger"
                size="small"
                @click="removeFile(file.uid)"
              >
                移除
              </el-button>
            </div>
          </div>
        </div>

        <!-- Action buttons -->
        <div class="upload-actions">
          <el-button
            v-if="hasFailures && !uploading"
            type="warning"
            @click="retryAllFailed"
          >
            重试全部失败文件
          </el-button>
          <el-button
            type="primary"
            :icon="Upload"
            :loading="uploading"
            :disabled="!canUpload && !hasFailures"
            @click="startUpload"
          >
            {{ uploading ? '上传中...' : '开始上传' }}
          </el-button>
        </div>

        <!-- Chunked upload info -->
        <div class="chunk-info">
          <el-icon><InfoFilled /></el-icon>
          单文件超过 {{ formatFileSize(CHUNKED_UPLOAD_THRESHOLD) }} 将自动启用分块上传（{{ formatFileSize(CHUNK_SIZE) }}/块），上传更稳定。
        </div>
      </el-card>

      <!-- Empty hint -->
      <el-card v-if="!hasFiles && currentStep === 2" class="empty-card" shadow="never">
        <el-empty description="尚未选择 VCF 文件">
          <template #image>
            <el-icon :size="64" color="#c0c4cc"><Document /></el-icon>
          </template>
        </el-empty>
      </el-card>
    </div>

    <!-- ===== Step 3: Parsing & results ===== -->
    <div v-show="currentStep === 3">
      <!-- Parsing progress -->
      <el-card v-if="parsing" class="parsing-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>遗传数据解析中</span>
          </div>
        </template>
        <div class="parsing-content">
          <el-progress :percentage="Math.round(parsingProgress)" :stroke-width="8" />
          <p class="parsing-text">{{ parsingMessage || '正在解析 VCF 文件，请稍候...' }}</p>
        </div>
      </el-card>

      <!-- Results -->
      <el-card v-if="!parsing && variantSummary" class="result-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>解析结果</span>
            <el-button text type="primary" size="small" @click="goToSampleDetail">
              查看样本列表
            </el-button>
          </div>
        </template>

        <el-alert
          title="上传与解析完成"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 20px"
        >
          <template #default>
            成功解析 {{ variantSummary.totalVariants.toLocaleString() }} 个变异位点。
          </template>
        </el-alert>

        <!-- Variant type summary cards -->
        <div class="variant-summary-cards">
          <div class="summary-card">
            <div class="summary-number">{{ variantSummary.totalVariants.toLocaleString() }}</div>
            <div class="summary-label">总变异数</div>
          </div>
          <div class="summary-card">
            <div class="summary-number snp">{{ variantSummary.snpCount.toLocaleString() }}</div>
            <div class="summary-label">SNP</div>
          </div>
          <div class="summary-card">
            <div class="summary-number indel">{{ variantSummary.indelCount.toLocaleString() }}</div>
            <div class="summary-label">InDel</div>
          </div>
          <div class="summary-card">
            <div class="summary-number cnv">{{ variantSummary.cnvCount.toLocaleString() }}</div>
            <div class="summary-label">CNV</div>
          </div>
          <div class="summary-card">
            <div class="summary-number sv">{{ variantSummary.svCount.toLocaleString() }}</div>
            <div class="summary-label">SV</div>
          </div>
        </div>

        <!-- QC filters applied -->
        <div class="section-title">质控过滤结果</div>
        <el-table
          :data="variantSummary.qcFiltersApplied"
          stripe
          size="small"
          style="width: 100%; margin-bottom: 20px"
          max-height="340"
        >
          <el-table-column prop="filterName" label="过滤项" width="120" />
          <el-table-column prop="description" label="过滤条件" min-width="200" />
          <el-table-column prop="variantsRemoved" label="剔除变异数" width="120" align="center">
            <template #default="{ row }">
              {{ row.variantsRemoved.toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column prop="variantsPassed" label="通过变异数" width="120" align="center">
            <template #default="{ row }">
              {{ row.variantsPassed.toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="qcFilterTagType(row.status)" size="small">
                {{ qcFilterLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <!-- Quality metrics -->
        <div class="section-title">质量指标</div>
        <el-descriptions :column="3" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="Ti/Tv 比值">
            {{ variantSummary.tiTvRatio?.toFixed(2) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="杂合/纯合比值">
            {{ variantSummary.hetHomRatio?.toFixed(2) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="dbSNP 重叠率">
            {{ variantSummary.dbSnpOverlap != null ? (variantSummary.dbSnpOverlap / variantSummary.totalVariants * 100).toFixed(1) + '%' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="已知变异数">
            {{ variantSummary.dbSnpOverlap?.toLocaleString() || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="新发变异数">
            {{ variantSummary.novelVariants?.toLocaleString() || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="文件数">
            {{ uploadQueue.filter(f => f.status === 'success').length }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- Chromosome distribution -->
        <div class="section-title">染色体分布</div>
        <div class="chromosome-bars">
          <div
            v-for="chr in variantSummary.chromosomes"
            :key="chr.chromosome"
            class="chromosome-bar-item"
          >
            <span class="chromosome-label">{{ chr.chromosome }}</span>
            <div class="chromosome-bar-track">
              <div
                class="chromosome-bar-fill"
                :style="{ width: (chr.variantCount / variantSummary.chromosomes[0].variantCount * 100) + '%' }"
              />
            </div>
            <span class="chromosome-count">{{ chr.variantCount.toLocaleString() }}</span>
          </div>
        </div>

        <div class="result-actions">
          <el-button type="primary" @click="goToSampleDetail">
            查看样本详情
          </el-button>
          <el-button @click="startNewUpload">
            继续上传
          </el-button>
        </div>
      </el-card>

      <!-- Empty result -->
      <el-card v-if="!parsing && !variantSummary && currentStep === 3" class="empty-card" shadow="never">
        <el-empty description="未找到解析结果，请检查上传的文件或稍后重试">
          <template #image>
            <el-icon :size="64" color="#e6a23c"><WarningFilled /></el-icon>
          </template>
        </el-empty>
        <div class="result-actions" style="justify-content: center; padding-top: 8px">
          <el-button @click="currentStep = 2">返回上传</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: 'GeneticsUploadView',
}
</script>

<style scoped lang="scss">
.genetics-upload-view {
  max-width: 960px;

  .page-nav {
    margin-bottom: 8px;
  }

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 20px;
  }

  .upload-steps {
    margin-bottom: 28px;
  }
}

// ---- Step cards ----
.step-card {
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
    color: #303133;

    .card-header-tip {
      font-size: 13px;
      font-weight: 400;
      color: #909399;
    }
  }

  .selection-form {
    .session-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  }

  .step-actions {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #ebeef5;
    text-align: right;
  }
}

// ---- Info bar ----
.info-bar-card {
  margin-bottom: 16px;

  .info-bar {
    display: flex;
    align-items: center;
    gap: 24px;
    flex-wrap: wrap;

    .info-item {
      font-size: 14px;
      color: #606266;

      strong {
        color: #303133;
        margin-right: 4px;
      }
    }
  }
}

// ---- Drop zone ----
.upload-card {
  margin-bottom: 16px;

  .drop-zone {
    .upload-trigger {
      padding: 40px 20px;
      text-align: center;
    }

    .upload-icon {
      font-size: 48px;
      color: #409eff;
      margin-bottom: 12px;
    }

    .upload-text {
      font-size: 15px;
      color: #606266;
      margin-bottom: 6px;

      em {
        color: #409eff;
        font-style: normal;
        cursor: pointer;
      }
    }

    .upload-hint {
      font-size: 13px;
      color: #c0c4cc;
    }

    :deep(.el-upload-dragger) {
      width: 100%;
    }

    &.is-dragover :deep(.el-upload-dragger) {
      border-color: #409eff;
      background: #ecf5ff;
    }
  }
}

// ---- Queue ----
.queue-card {
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
    color: #303133;

    .queue-header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      font-weight: 400;
      color: #606266;

      .overall-progress {
        color: #409eff;
      }

      .upload-speed {
        color: #909399;
      }
    }
  }

  .overall-bar {
    margin-bottom: 16px;

    .overall-size {
      margin-top: 4px;
      font-size: 12px;
      color: #909399;
      text-align: right;
    }
  }

  .file-list {
    max-height: 420px;
    overflow-y: auto;
  }

  .file-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 0;
    border-bottom: 1px solid #f2f3f5;

    &:last-child {
      border-bottom: none;
    }

    .file-info {
      flex: 1;
      min-width: 0;

      .file-name {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-bottom: 2px;

        .file-type-tag {
          flex-shrink: 0;
        }

        .file-name-text {
          font-size: 14px;
          color: #303133;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .file-meta {
        font-size: 12px;
        color: #909399;
        display: flex;
        align-items: center;
        gap: 8px;

        .chunk-badge {
          display: inline-block;
          padding: 0 4px;
          font-size: 11px;
          color: #e6a23c;
          background: #fdf6ec;
          border-radius: 3px;
        }
      }
    }

    .file-progress {
      width: 140px;
      flex-shrink: 0;
    }

    .file-status {
      width: 110px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      gap: 4px;

      .status-icon {
        font-size: 16px;
        flex-shrink: 0;

        &.pending { color: #c0c4cc; }
        &.uploading { color: #409eff; }
        &.success { color: #67c23a; }
        &.failed { color: #f56c6c; }
      }

      .status-text {
        font-size: 13px;
        color: #909399;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;

        &.success { color: #67c23a; }
        &.failed { color: #f56c6c; }
      }
    }

    .file-actions {
      flex-shrink: 0;
    }
  }

  .upload-actions {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #ebeef5;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .chunk-info {
    margin-top: 12px;
    font-size: 12px;
    color: #909399;
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

// ---- Parsing ----
.parsing-card {
  margin-bottom: 16px;

  .card-header {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .parsing-content {
    padding: 20px 0;

    .parsing-text {
      margin-top: 16px;
      font-size: 14px;
      color: #909399;
      text-align: center;
    }
  }
}

// ---- Results ----
.result-card {
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #ebeef5;
  }

  .variant-summary-cards {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 12px;
    margin-bottom: 24px;

    .summary-card {
      text-align: center;
      padding: 16px 8px;
      background: #f5f7fa;
      border-radius: 8px;
      border: 1px solid #ebeef5;

      .summary-number {
        font-size: 24px;
        font-weight: 700;
        color: #303133;
        margin-bottom: 4px;

        &.snp { color: #409eff; }
        &.indel { color: #67c23a; }
        &.cnv { color: #e6a23c; }
        &.sv { color: #f56c6c; }
      }

      .summary-label {
        font-size: 13px;
        color: #909399;
      }
    }
  }

  .chromosome-bars {
    margin-bottom: 20px;

    .chromosome-bar-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;

      .chromosome-label {
        width: 48px;
        font-size: 12px;
        color: #606266;
        text-align: right;
        flex-shrink: 0;
      }

      .chromosome-bar-track {
        flex: 1;
        height: 14px;
        background: #f2f3f5;
        border-radius: 3px;
        overflow: hidden;

        .chromosome-bar-fill {
          height: 100%;
          background: linear-gradient(90deg, #409eff, #66b1ff);
          border-radius: 3px;
          min-width: 2px;
          transition: width 0.4s ease;
        }
      }

      .chromosome-count {
        width: 72px;
        font-size: 12px;
        color: #909399;
        flex-shrink: 0;
      }
    }
  }

  .result-actions {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}

// ---- Empty ----
.empty-card {
  margin-bottom: 16px;
}
</style>
