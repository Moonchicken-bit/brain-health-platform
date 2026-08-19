<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  UploadFilled,
  FolderOpened,
  Upload,
  CircleCheck,
  CircleClose,
  Clock,
  Loading,
  InfoFilled,
} from '@element-plus/icons-vue'
import http from '@/api/client'
import { imagingApi } from '@/api/modules/imaging'
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
  fileType: string
  archiveAnalysis?: ArchiveAnalysis
  objectName?: string
  analysisTaskId?: string
}

interface ArchiveAnalysis {
  archiveType: string
  totalFiles: number
  dicomFiles: number
  patientIds: string[]
  patientNames: string[]
  studyInstanceUids: string[]
  series: Array<{
    seriesInstanceUid?: string
    seriesNumber?: number
    description?: string
    modality?: string
    studyDate?: string
    fileCount: number
    totalBytes: number
    previewBase64?: string
  }>
  warnings: string[]
}

interface SeriesInfo {
  id: number
  seriesUid?: string
  seriesNumber: number
  seriesDescription?: string
  sequenceName?: string
  echoTime?: number
  repetitionTime?: number
  sliceThickness?: number
  numberOfFiles: number
  fileType: string
  qcStatus: string
}

// ---- Constants ----
const CHUNK_SIZE = 20 * 1024 * 1024 // 20MB per chunk
const CHUNKED_UPLOAD_THRESHOLD = 50 * 1024 * 1024 // 50MB
const ACCEPTED_FILE_TYPES = '.dcm,.dicom,.nii,.nii.gz,.edf,.fif,.zip,.rar'
const ACCEPTED_EXTENSIONS = new Set(['dcm', 'dicom', 'nii', 'gz', 'edf', 'fif', 'zip', 'rar'])

const modalityOptions = ref<Array<{ id: number; code: string; name: string }>>([])

// ---- Router ----
const router = useRouter()

// ---- Step state ----
const currentStep = ref(1) // 1: select subject/session, 2: upload, 3: review

// ---- Subject & session selection ----
const subjects = ref<SubjectOption[]>([])
const subjectsLoading = ref(false)

const selectedSubjectId = ref<number | null>(null)
const selectedSessionId = ref<number | null>(null)
const selectedModalityId = ref<number | null>(null)

const sessions = ref<{ id: number; visitLabel: string; status: string }[]>([])
const sessionsLoading = ref(false)

// New session creation fields
const createNewSession = ref(false)
const newSessionLabel = ref('')
const newSessionDate = ref('')

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
const savingConfirmation = ref(false)
const seriesList = ref<SeriesInfo[]>([])
const imagingSessionId = ref<number | null>(null)

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
  selectedSubjectId.value && selectedSessionId.value && selectedModalityId.value && hasFiles.value && !uploading.value
)

const selectedSubject = computed(() =>
  subjects.value.find((s) => s.id === selectedSubjectId.value)
)
const selectedModalityLabel = computed(() =>
  modalityOptions.value.find((m) => m.id === selectedModalityId.value)?.name || ''
)

onMounted(async () => {
  try {
    const response = await imagingApi.getModalities()
    modalityOptions.value = response.data?.data || []
  } catch {
    modalityOptions.value = []
  }
})

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
      sessionDate: newSessionDate.value,
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

// ---- File extension helpers ----
function getFileExtension(name: string): string {
  const lower = name.toLowerCase()
  if (lower.endsWith('.nii.gz')) return 'nii.gz'
  const parts = name.split('.')
  return parts.length > 1 ? parts.pop()!.toLowerCase() : ''
}

function classifyFileType(name: string): string {
  const ext = getFileExtension(name)
  switch (ext) {
    case 'dcm':
    case 'dicom':
      return 'DICOM'
    case 'nii':
    case 'nii.gz':
      return 'NIfTI'
    case 'edf':
      return 'EDF'
    case 'fif':
      return 'FIF'
    case 'zip':
    case 'rar':
      return '压缩包'
    default:
      return 'Unknown'
  }
}

function isValidFileType(name: string): boolean {
  const ext = getFileExtension(name)
  return ACCEPTED_EXTENSIONS.has(ext) || name.toLowerCase().endsWith('.nii.gz')
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
    fileType: classifyFileType(f.name),
  }))

  uploadQueue.value.push(...newItems)
  updateOverallProgress()
}

function removeFile(uid: string) {
  const idx = uploadQueue.value.findIndex((f) => f.uid === uid)
  if (idx === -1) return
  const item = uploadQueue.value[idx]
  if (item.status === 'uploading') {
    // Cancel in-flight chunked upload
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
    // Auto-trigger DICOM parsing
    await triggerParsing()
  } else if (hasFailures.value) {
    ElMessage.warning('部分文件上传失败，请检查后重试')
  }
}

async function uploadFileDirect(file: UploadFileItem, ctrl: AbortController) {
  file.status = 'uploading'
  const startTime = Date.now()

  const formData = new FormData()
  formData.append('files', file.raw)
  formData.append('subjectId', String(selectedSubjectId.value))
  formData.append('sessionId', String(selectedSessionId.value))
  formData.append('modalityId', String(selectedModalityId.value))

  try {
    const response = await http.post('/api/v1/imaging/upload', formData, {
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
    file.objectName = response.data?.data?.objectNames?.[0]
    file.analysisTaskId = response.data?.data?.analysisTaskIds?.[0]
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
    chunkForm.append('modalityId', String(selectedModalityId.value))

    await http.post('/api/v1/imaging/upload/chunk', chunkForm, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
      signal: ctrl.signal,
    })

    file.progress = Math.round(((i + 1) / totalChunks) * 100)
    const elapsed = (Date.now() - startTime) / 1000
    const uploaded = (i + 1) * CHUNK_SIZE
    if (elapsed > 0) {
      uploadSpeed.value = formatFileSize(Math.min(uploaded, file.size) / elapsed) + '/s'
    }
    updateOverallProgress()
  }

  // After all chunks, call merge endpoint
  const mergeResponse = await http.post('/api/v1/imaging/upload/merge', {
    uploadId,
    fileName: file.name,
    totalChunks,
    fileSize: file.size,
    subjectId: selectedSubjectId.value,
    sessionId: selectedSessionId.value,
    modalityId: selectedModalityId.value,
  }, {
    timeout: 120000,
    signal: ctrl.signal,
  })
  file.objectName = mergeResponse.data?.data?.objectName
  file.analysisTaskId = mergeResponse.data?.data?.analysisTaskId
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

// ---- DICOM parsing & metadata extraction ----
async function triggerParsing() {
  if (!selectedSessionId.value) return

  parsing.value = true
  parsingProgress.value = 0

  try {
    const taskFiles = uploadQueue.value.filter(file => file.analysisTaskId)
    for (const file of taskFiles) {
      for (let attempt = 0; attempt < 600; attempt++) {
        const response = await http.get(`/api/v1/imaging/archive/tasks/${file.analysisTaskId}`)
        const task = response.data?.data
        parsingProgress.value = Math.max(parsingProgress.value, task?.progress || 0)
        if (task?.status === 'COMPLETED') {
          file.archiveAnalysis = task.result
          file.objectName = task.objectName || file.objectName
          break
        }
        if (task?.status === 'FAILED') throw new Error(task.error || '压缩包解析失败')
        await new Promise(resolve => setTimeout(resolve, 3000))
      }
      if (!file.archiveAnalysis) throw new Error('压缩包解析超时，请稍后重试')
    }
  } catch (error: any) {
    parsing.value = false
    currentStep.value = 3
    ElMessage.error(error?.message || '压缩包解析失败')
    return
  }

  const archiveAnalyses = uploadQueue.value
    .map(file => file.archiveAnalysis)
    .filter((analysis): analysis is ArchiveAnalysis => Boolean(analysis))
  if (archiveAnalyses.length) {
    seriesList.value = archiveAnalyses.flatMap(analysis => analysis.series.map((series, index) => ({
      id: index + 1,
      seriesUid: series.seriesInstanceUid,
      seriesNumber: series.seriesNumber ?? index + 1,
      seriesDescription: series.description,
      sequenceName: series.modality,
      numberOfFiles: series.fileCount,
      fileType: 'DICOM',
      qcStatus: 'PENDING',
    })))
    parsingProgress.value = 100
    currentStep.value = 3
    const warnings = archiveAnalyses.flatMap(analysis => analysis.warnings)
    if (warnings.length) ElMessage.warning(warnings.join('；'))
    const imported = await confirmArchiveImport(true)
    parsing.value = false
    if (!imported) {
      ElMessage.warning('解析已经完成，但影像检查尚未入库，请点击“重试入库”')
    }
    return
  }

  // 上传已完成，下面按真实处理阶段更新进度，不伪造随机进度。
  parsingProgress.value = Math.max(parsingProgress.value, 90)

  try {
    // Poll for series data loaded from the imaging session
    const sessionRes = await imagingApi.listSessions({ subjectId: selectedSubjectId.value! })
    const sessions = sessionRes.data?.data?.records || []
    const matching = sessions.find((s: any) => s.sessionId === selectedSessionId.value)
    if (matching?.id) {
      imagingSessionId.value = matching.id
    }

    // Fetch series
    parsingProgress.value = 95
    if (imagingSessionId.value) {
      const seriesRes = await imagingApi.getSeries(imagingSessionId.value)
      seriesList.value = seriesRes.data?.data || []
    }

    parsingProgress.value = 100
    currentStep.value = 3
    ElMessage.success('影像数据解析完成')
  } catch (err: any) {
    ElMessage.error('影像解析失败: ' + (err?.message || '请稍后重试'))
  } finally {
    parsing.value = false
  }
}

// ---- Drag & drop on upload area ----
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

  if (!e.dataTransfer) return

  const items = e.dataTransfer.items
  if (!items) return

  const files: File[] = []
  await traverseFileTree(items, files)

  if (files.length) {
    handleFilesAdded(files)
  }
}

async function traverseFileTree(items: DataTransferItemList, result: File[]) {
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.kind === 'file') {
      // @ts-ignore — webkitGetAsEntry is widely supported
      const entry = item.webkitGetAsEntry ? item.webkitGetAsEntry() : null
      if (entry?.isDirectory) {
        await readDirectory(entry as any, result)
      } else {
        const file = item.getAsFile()
        if (file) result.push(file)
      }
    }
  }
}

async function readDirectory(entry: any, result: File[]) {
  const reader = entry.createReader()
  const readEntries = (): Promise<any[]> =>
    new Promise((resolve) => reader.readEntries(resolve))

  let entries = await readEntries()
  while (entries.length > 0) {
    for (const ent of entries) {
      if (ent.isFile) {
        const file: File = await new Promise((resolve, reject) =>
          ent.file(resolve, reject)
        )
        result.push(file)
      } else if (ent.isDirectory) {
        await readDirectory(ent, result)
      }
    }
    entries = await readEntries()
  }
}

// ---- Step navigation ----
function goToStep(step: number) {
  if (step === 2 && !selectedSubjectId.value) return ElMessage.warning('请先选择受试者')
  if (step === 2 && !selectedSessionId.value) return ElMessage.warning('请先选择或创建访视')
  if (step === 2 && !selectedModalityId.value) return ElMessage.warning('请先选择影像模态')
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

async function confirmArchiveImport(automatic = false): Promise<boolean> {
  const archiveFile = uploadQueue.value.find(file => file.archiveAnalysis)
  if (!archiveFile?.archiveAnalysis || !selectedSubjectId.value || !selectedSessionId.value || !selectedModalityId.value) {
    ElMessage.warning('缺少压缩包解析结果或受试者访视信息')
    return false
  }
  savingConfirmation.value = true
  try {
    const firstSeries = archiveFile.archiveAnalysis.series[0]
    const response = await http.post('/api/v1/imaging/archive/confirm', {
      subjectId: selectedSubjectId.value,
      sessionId: selectedSessionId.value,
      modalityId: selectedModalityId.value,
      acquisitionDate: firstSeries?.studyDate
        ? `${firstSeries.studyDate.slice(0, 4)}-${firstSeries.studyDate.slice(4, 6)}-${firstSeries.studyDate.slice(6, 8)}`
        : undefined,
      sourceObject: archiveFile.objectName,
      series: archiveFile.archiveAnalysis.series,
    })
    imagingSessionId.value = response.data?.data?.id
    if (!imagingSessionId.value) {
      throw new Error('服务端未返回新建检查 ID')
    }
    ElMessage.success(automatic ? '解析和入库完成，可以查看本次影像详情' : '影像检查与序列已成功入库')
    return true
  } catch {
    if (!automatic) ElMessage.error('影像入库失败，请核对后重试')
    return false
  } finally {
    savingConfirmation.value = false
  }
}

function goToImagingSession() {
  if (imagingSessionId.value) {
    router.push({ name: 'ImagingSessionDetail', params: { id: imagingSessionId.value } })
  }
}

// ---- Status helpers ----
function qcStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PASS: '通过', FAIL: '未通过', PENDING: '待质控', UNDER_REVIEW: '审核中',
  }
  return map[status] || status
}

function qcStatusType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    PASS: 'success', FAIL: 'danger', PENDING: 'warning', UNDER_REVIEW: 'info',
  }
  return map[status] || 'info'
}

// ---- Cleanup on unmount ----
onBeforeUnmount(() => {
  for (const ctrl of cancelTokenSources.values()) {
    ctrl.abort()
  }
  cancelTokenSources.clear()
})

// ---- el-upload handlers ----
const uploadRef = ref<any>(null)

function handleHttpRequest(options: any) {
  // el-upload 要求提供请求处理器；实际上传统一由 startUpload 管理。
}

function handleExceed() {
  ElMessage.warning('文件数量超出限制')
}
</script>

<template>
  <div class="imaging-upload-view">
    <!-- Back navigation -->
    <div class="page-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <h2 class="page-title">影像数据上传</h2>

    <!-- Steps indicator -->
    <el-steps :active="currentStep - 1" align-center class="upload-steps" finish-status="success">
      <el-step title="选择受试者与访视" />
      <el-step title="上传文件" />
      <el-step title="解析结果" />
    </el-steps>

    <!-- ===== Step 1: Select subject and session ===== -->
    <el-card v-show="currentStep === 1" class="step-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>选择受试者与访视</span>
        </div>
      </template>

      <el-form label-width="100px" class="selection-form">
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
            <el-button
              text
              type="primary"
              size="small"
              @click="createNewSession = !createNewSession"
            >
              {{ createNewSession ? '选择已有访视' : '新建访视' }}
            </el-button>
          </div>
          <div class="field-help">访视代表一次检查或随访时间点，影像数据必须归属某次访视。</div>
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

        <!-- Modality select -->
        <el-form-item v-if="selectedSessionId" label="影像模态">
          <el-select
            v-model="selectedModalityId"
            placeholder="选择影像模态"
            style="width: 360px"
          >
            <el-option
              v-for="m in modalityOptions"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="step-actions">
        <el-button type="primary" :disabled="!selectedSubjectId || !selectedSessionId || !selectedModalityId" @click="goToStep(2)">
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
            <strong>模态:</strong> {{ selectedModalityLabel }}
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
            <span>选择文件</span>
            <span class="card-header-tip">
              支持 DICOM、NIfTI、EDF、FIF，以及医院发送的 ZIP/RAR 压缩包
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
            :http-request="handleHttpRequest"
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
                <span>拖拽文件到此区域，或</span>
                <em>点击选择文件</em>
              </div>
              <div class="upload-hint">
                也可拖拽整个 DICOM 文件夹到此处（自动递归扫描）
              </div>
            </div>
          </el-upload>
        </div>

        <!-- Accept entry via folder button -->
        <div class="folder-select-row">
          <input
            id="folder-input"
            type="file"
            multiple
            webkitdirectory
            hidden
            @change="(e: Event) => {
              const input = e.target as HTMLInputElement
              if (input.files) handleFilesAdded(input.files)
              input.value = ''
            }"
          />
          <el-button
            :icon="FolderOpened"
            size="small"
            @click="(e: MouseEvent) => {
              (e.target as HTMLElement).closest('.folder-select-row')
                ?.querySelector<HTMLInputElement>('#folder-input')?.click()
            }"
          >
            选择文件夹
          </el-button>
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
                <el-tag size="small" :type="file.fileType === 'DICOM' ? '' : 'info'" class="file-type-tag">
                  {{ file.fileType }}
                </el-tag>
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
          单文件超过 50MB 将自动启用分块上传（{{ formatFileSize(CHUNK_SIZE) }}/块），上传更稳定。
        </div>
      </el-card>

      <!-- Empty hint -->
      <el-card v-if="!hasFiles && currentStep === 2" class="empty-card" shadow="never">
        <el-empty description="尚未选择文件">
          <template #image>
            <el-icon :size="64" color="#c0c4cc"><UploadFilled /></el-icon>
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
            <span>影像解析中</span>
          </div>
        </template>
        <div class="parsing-content">
          <el-progress :percentage="Math.round(parsingProgress)" :stroke-width="8" />
          <p class="parsing-text">
            正在解析 DICOM 头文件并提取元数据，请稍候...
          </p>
        </div>
      </el-card>

      <!-- Results -->
      <el-card v-if="!parsing && seriesList.length > 0" class="result-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>解析结果</span>
            <el-button
              v-if="imagingSessionId"
              text
              type="primary"
              size="small"
              @click="goToImagingSession"
            >
              查看影像详情
            </el-button>
          </div>
        </template>

        <el-alert
          :title="imagingSessionId ? '上传、解析和入库完成' : '解析完成，等待入库'"
          :type="imagingSessionId ? 'success' : 'warning'"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #default>
            <template v-if="imagingSessionId">
              已将 {{ seriesList.length }} 个序列写入本次影像检查，可直接查看详情。
            </template>
            <template v-else>
              已解析 {{ seriesList.length }} 个序列，但尚未写入影像数据，请重试入库。
            </template>
          </template>
        </el-alert>

        <el-table
          :data="seriesList"
          stripe
          style="width: 100%"
          size="small"
          max-height="400"
        >
          <el-table-column prop="seriesNumber" label="序列号" width="80" align="center" />
          <el-table-column prop="seriesDescription" label="序列描述" min-width="180">
            <template #default="{ row }">
              {{ row.seriesDescription || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="sequenceName" label="序列名称" min-width="140">
            <template #default="{ row }">
              {{ row.sequenceName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="文件类型" width="100" align="center" />
          <el-table-column prop="numberOfFiles" label="文件数" width="90" align="center" />
          <el-table-column label="TE (ms)" width="90" align="center">
            <template #default="{ row }">
              {{ row.echoTime != null ? row.echoTime.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="TR (ms)" width="90" align="center">
            <template #default="{ row }">
              {{ row.repetitionTime != null ? row.repetitionTime.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="层厚 (mm)" width="90" align="center">
            <template #default="{ row }">
              {{ row.sliceThickness != null ? row.sliceThickness.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="qcStatus" label="质控状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="qcStatusType(row.qcStatus)" size="small">
                {{ qcStatusLabel(row.qcStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="result-actions">
          <el-button v-if="!imagingSessionId" type="primary" :loading="savingConfirmation" @click="confirmArchiveImport()">
            重试入库
          </el-button>
          <el-button type="primary" @click="goToImagingSession" v-if="imagingSessionId">
            查看完整影像详情
          </el-button>
          <el-button @click="() => { currentStep = 1; uploadQueue = []; seriesList = []; imagingSessionId = null; selectedSubjectId = null; selectedSessionId = null; selectedModalityId = null; overallProgress = 0; totalBytes = 0; uploadedBytes = 0 }">
            继续上传
          </el-button>
        </div>
      </el-card>

      <!-- Empty result -->
      <el-card v-if="!parsing && seriesList.length === 0 && currentStep === 3" class="empty-card" shadow="never">
        <el-empty description="未找到解析结果，请检查上传的文件或稍后重试" />
        <div class="result-actions" style="justify-content: center; padding-top: 8px">
          <el-button @click="currentStep = 2">返回上传</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ImagingUploadView',
}
</script>

<style scoped lang="scss">
.imaging-upload-view {
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

    .field-help {
      margin-top: 6px;
      color: #909399;
      font-size: 13px;
      line-height: 1.5;
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

  .folder-select-row {
    margin-top: 12px;
    text-align: right;
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
