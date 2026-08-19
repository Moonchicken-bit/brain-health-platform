<script setup lang="ts">
import { ref, reactive, onMounted, h, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElTag, ElButton, ElLink, ElTable, ElTableColumn } from 'element-plus'
import { Search, RefreshRight, Upload, Delete, ArrowRight } from '@element-plus/icons-vue'
import {
  geneticsApi,
  type GeneticsSample,
  type GeneticsVariant,
  type GeneticsSampleSearchParams,
} from '@/api/modules/genetics'
import http from '@/api/client'
import type { PageResult } from '@/types/api'

const router = useRouter()

// ---- Filter form ----
const filterForm = reactive<GeneticsSampleSearchParams>({
  subjectId: '',
  geneSymbol: '',
  variantType: '',
  clinicalSignificance: '',
})

// ---- Variant type options ----
const variantTypeOptions = [
  { label: 'SNV', value: 'SNV' },
  { label: 'Insertion', value: 'INS' },
  { label: 'Deletion', value: 'DEL' },
  { label: 'CNV', value: 'CNV' },
  { label: 'SV (结构变异)', value: 'SV' },
]

// ---- Clinical significance options ----
const clinicalSignificanceOptions = [
  { label: 'Pathogenic', value: 'Pathogenic' },
  { label: 'Likely Pathogenic', value: 'Likely_Pathogenic' },
  { label: 'Uncertain', value: 'Uncertain_Significance' },
  { label: 'Likely Benign', value: 'Likely_Benign' },
  { label: 'Benign', value: 'Benign' },
]

// ---- QC status options ----
const qcStatusOptions = [
  { label: '通过', value: 'Passed' },
  { label: '失败', value: 'Failed' },
  { label: '待审核', value: 'Pending' },
]

// ---- Table ----
const tableData = ref<GeneticsSample[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Subject ID lookup ----
const subjectIdMap = ref<Map<number, string>>(new Map())

// ---- Expanded row state ----
const expandedRows = ref<number[]>([])
const expandedVariantData = reactive<Map<number, GeneticsVariant[]>>(new Map())
const expandedVariantLoading = reactive<Map<number, boolean>>(new Map())
const expandedVariantPagination = reactive<
  Map<number, { page: number; size: number; total: number }>
>(new Map())

// ---- Variant detail dialog ----
const variantDialogVisible = ref(false)
const variantDialogData = ref<GeneticsVariant | null>(null)

// ---- VCF upload dialog ----
const uploadDialogVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

// ======================== API calls ========================

async function fetchSamples() {
  loading.value = true
  try {
    const params: GeneticsSampleSearchParams = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.subjectId) params.subjectId = filterForm.subjectId
    if (filterForm.geneSymbol) params.geneSymbol = filterForm.geneSymbol
    if (filterForm.variantType) params.variantType = filterForm.variantType
    if (filterForm.clinicalSignificance) params.clinicalSignificance = filterForm.clinicalSignificance

    const res = await geneticsApi.listSamples(params)
    if (res.data.code === 200) {
      const pageResult = res.data.data as PageResult<GeneticsSample>
      tableData.value = pageResult.records
      pagination.total = pageResult.total
      fetchSubjectNames(pageResult.records)
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchSubjectNames(records: GeneticsSample[]) {
  const subjectIds = [...new Set(records.map((r) => r.subjectId))]
  const missing = subjectIds.filter((id) => !subjectIdMap.value.has(id))
  if (missing.length === 0) return

  for (const id of missing) {
    try {
      const res = await http.get<{ code: number; data: { subjectId: string } }>(`/api/v1/subjects/${id}`)
      if (res.data.code === 200 && res.data.data) {
        subjectIdMap.value.set(id, res.data.data.subjectId)
      }
    } catch {
      subjectIdMap.value.set(id, String(id))
    }
  }
}

async function fetchVariantsForSample(sampleId: number) {
  const curr = expandedVariantPagination.get(sampleId)
  const page = curr?.page || 1
  const size = curr?.size || 10

  expandedVariantLoading.set(sampleId, true)
  try {
    const res = await geneticsApi.listVariants({ sampleId, page, size })
    if (res.data.code === 200) {
      const pageResult = res.data.data as PageResult<GeneticsVariant>
      expandedVariantData.set(sampleId, pageResult.records)
      expandedVariantPagination.set(sampleId, { page, size, total: pageResult.total })
    }
  } catch {
    // error handled by interceptor
  } finally {
    expandedVariantLoading.set(sampleId, false)
  }
}

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchSamples()
}

function handleReset() {
  filterForm.subjectId = ''
  filterForm.geneSymbol = ''
  filterForm.variantType = ''
  filterForm.clinicalSignificance = ''
  pagination.page = 1
  fetchSamples()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchSamples()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchSamples()
}

// ---- Row expand ----

function handleExpand(row: GeneticsSample, expandedState: boolean | GeneticsSample[]) {
  const sampleId = row.id!
  const isExpanded = Array.isArray(expandedState)
    ? expandedState.some((r) => r.id === sampleId)
    : expandedState
  if (isExpanded) {
    // Row is expanded — fetch variants
    if (!expandedVariantData.has(sampleId)) {
      fetchVariantsForSample(sampleId)
    }
  }
}

function handleVariantPageChange(sampleId: number, page: number) {
  const curr = expandedVariantPagination.get(sampleId)
  expandedVariantPagination.set(sampleId, { page, size: curr?.size || 10, total: curr?.total || 0 })
  fetchVariantsForSample(sampleId)
}

function handleVariantSizeChange(sampleId: number, size: number) {
  expandedVariantPagination.set(sampleId, { page: 1, size, total: 0 })
  fetchVariantsForSample(sampleId)
}

// ---- Variant detail dialog ----

function handleVariantDetail(variant: any) {
  variantDialogData.value = variant as GeneticsVariant
  variantDialogVisible.value = true
}

// ---- Upload ----

function handleGoUpload() {
  router.push('/genetics/upload')
}

function handleUploadOpen() {
  uploadFile.value = null
  uploadDialogVisible.value = true
}

function handleFileChange(file: File) {
  uploadFile.value = file
}

function handleFileRemove() {
  uploadFile.value = null
}

async function handleUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择 VCF 文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    await geneticsApi.uploadVcf(formData)
    ElMessage.success('VCF 文件上传成功')
    uploadDialogVisible.value = false
    fetchSamples()
  } catch {
    // error handled by interceptor
  } finally {
    uploading.value = false
  }
}

// ---- Delete ----

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要删除该样本记录吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await geneticsApi.deleteSample(row.id!)
    ElMessage.success('删除成功')
    // Clean up expanded state
    expandedVariantData.delete(row.id!)
    expandedVariantPagination.delete(row.id!)
    expandedVariantLoading.delete(row.id!)
    fetchSamples()
  } catch {
    // user cancelled or error
  }
}

// ======================== Display helpers ========================

function getSubjectIdDisplay(row: any): string {
  return subjectIdMap.value.get(row.subjectId) || String(row.subjectId)
}

function getSampleTypeLabel(type: string): string {
  switch (type) {
    case 'Whole_Blood':
      return '全血'
    case 'Buffy_Coat':
      return '血沉棕黄层'
    case 'Saliva':
      return '唾液'
    case 'DNA':
      return 'DNA'
    case 'Plasma':
      return '血浆'
    default:
      return type || '-'
  }
}

function getQcStatusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'Passed':
      return 'success'
    case 'Failed':
      return 'danger'
    case 'Pending':
      return 'warning'
    default:
      return 'info'
  }
}

function getQcStatusLabel(status: string): string {
  switch (status) {
    case 'Passed':
      return '通过'
    case 'Failed':
      return '失败'
    case 'Pending':
      return '待审核'
    default:
      return status || '未知'
  }
}

function getVariantTypeLabel(type: string): string {
  const found = variantTypeOptions.find((o) => o.value === type)
  return found ? found.label : type || '-'
}

function getClinicalSignificanceLabel(sig?: string): string {
  const found = clinicalSignificanceOptions.find((o) => o.value === sig)
  return found ? found.label : sig || '-'
}

function getClinicalSignificanceType(sig?: string): 'danger' | 'warning' | 'info' | 'success' {
  switch (sig) {
    case 'Pathogenic':
    case 'Likely_Pathogenic':
      return 'danger'
    case 'Uncertain_Significance':
      return 'warning'
    case 'Likely_Benign':
    case 'Benign':
      return 'success'
    default:
      return 'info'
  }
}

function getImpactLabel(impact?: string): string {
  switch (impact) {
    case 'HIGH':
      return '高'
    case 'MODERATE':
      return '中'
    case 'LOW':
      return '低'
    case 'MODIFIER':
      return '修饰'
    default:
      return impact || '-'
  }
}

function getImpactType(impact?: string): 'danger' | 'warning' | 'info' {
  switch (impact) {
    case 'HIGH':
      return 'danger'
    case 'MODERATE':
      return 'warning'
    case 'LOW':
    case 'MODIFIER':
      return 'info'
    default:
      return 'info'
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function formatDateTime(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}`
}

// ---- Table ref for expand ----
const tableRef = ref<any>()

// ======================== Init ========================

onMounted(() => {
  fetchSamples()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">遗传数据管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="受试者ID">
          <el-input
            v-model="filterForm.subjectId"
            placeholder="请输入受试者ID"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="基因符号">
          <el-input
            v-model="filterForm.geneSymbol"
            placeholder="例如: APOE, BDNF"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="变异类型">
          <el-select
            v-model="filterForm.variantType"
            placeholder="全部"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="opt in variantTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="临床意义">
          <el-select
            v-model="filterForm.clinicalSignificance"
            placeholder="全部"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="opt in clinicalSignificanceOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            搜索
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" :icon="Upload" @click="handleUploadOpen">
          上传VCF
        </el-button>
        <el-button @click="handleGoUpload">
          批量上传
          <el-icon style="margin-left: 4px"><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="total-count">共 {{ pagination.total }} 条记录</span>
      </div>
    </div>

    <!-- Table -->
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="tableData"
      stripe
      border
      style="width: 100%"
      size="default"
      @expand-change="handleExpand"
      row-key="id"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expanded-variant-section">
            <div class="expanded-title">
              变异列表 (样本 #{{ row.id }})
              <span class="expanded-subtitle">
                — 共 {{ expandedVariantPagination.get(row.id)?.total || 0 }} 个变异
              </span>
            </div>

            <el-table
              v-loading="expandedVariantLoading.get(row.id)"
              :data="expandedVariantData.get(row.id) || []"
              stripe
              border
              size="small"
              style="width: 100%"
            >
              <el-table-column prop="geneSymbol" label="基因符号" width="120" />
              <el-table-column label="变异类型" width="100" align="center">
                <template #default="{ row: vr }">
                  {{ getVariantTypeLabel(vr.variantType) }}
                </template>
              </el-table-column>
              <el-table-column label="染色体" width="80" align="center">
                <template #default="{ row: vr }">
                  {{ vr.chromosome }}
                </template>
              </el-table-column>
              <el-table-column label="位置" width="120" align="center">
                <template #default="{ row: vr }">
                  {{ vr.position }}
                </template>
              </el-table-column>
              <el-table-column label="Ref" width="100" align="center">
                <template #default="{ row: vr }">
                  <code class="variant-code">{{ vr.ref }}</code>
                </template>
              </el-table-column>
              <el-table-column label="Alt" width="100" align="center">
                <template #default="{ row: vr }">
                  <code class="variant-code">{{ vr.alt }}</code>
                </template>
              </el-table-column>
              <el-table-column label="临床意义" width="130" align="center">
                <template #default="{ row: vr }">
                  <el-tag
                    :type="getClinicalSignificanceType(vr.clinicalSignificance)"
                    size="small"
                  >
                    {{ getClinicalSignificanceLabel(vr.clinicalSignificance) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="影响" width="80" align="center">
                <template #default="{ row: vr }">
                  <el-tag :type="getImpactType(vr.impact)" size="small">
                    {{ getImpactLabel(vr.impact) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="基因型" width="100" align="center">
                <template #default="{ row: vr }">
                  {{ vr.genotype || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center" fixed="right">
                <template #default="{ row: vr }">
                  <el-button link type="primary" size="small" @click="handleVariantDetail(vr)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- Variant pagination -->
            <div
              v-if="expandedVariantPagination.get(row.id)"
              class="variant-pagination-wrapper"
            >
              <el-pagination
                :current-page="expandedVariantPagination.get(row.id)?.page || 1"
                :page-size="expandedVariantPagination.get(row.id)?.size || 10"
                :page-sizes="[5, 10, 20, 30]"
                :total="expandedVariantPagination.get(row.id)?.total || 0"
                layout="total, sizes, prev, pager, next"
                background
                small
                @current-change="(p: number) => handleVariantPageChange(row.id!, p)"
                @size-change="(s: number) => handleVariantSizeChange(row.id!, s)"
              />
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="受试者ID" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getSubjectIdDisplay(row) }}
        </template>
      </el-table-column>

      <el-table-column label="样本类型" width="130" align="center">
        <template #default="{ row }">
          {{ getSampleTypeLabel(row.sampleType) }}
        </template>
      </el-table-column>

      <el-table-column label="测序平台" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.platform || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="QC状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getQcStatusType(row.qcStatus)" size="small">
            {{ getQcStatusLabel(row.qcStatus) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="变异数" width="100" align="center">
        <template #default="{ row }">
          <span class="variant-count">{{ row.variantCount }}</span>
        </template>
      </el-table-column>

      <el-table-column label="上传时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleVariantDetail(row)">
            查看
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            :icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 15, 20, 30, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- VCF upload dialog -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传 VCF 文件"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="upload-dialog-body">
        <p class="upload-tip">
          请上传符合 VCF v4.0+ 规范的变异检测结果文件。
          文件大小不超过 100MB，支持 .vcf / .vcf.gz 格式。
        </p>

        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".vcf,.vcf.gz"
          :on-change="(f: any) => handleFileChange(f.raw as File)"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload" :size="40">
            <Upload />
          </el-icon>
          <div class="el-upload__text">
            将 VCF 文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 .vcf / .vcf.gz 格式，最大 100MB
            </div>
          </template>
        </el-upload>
      </div>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!uploadFile"
          @click="handleUpload"
        >
          上传
        </el-button>
      </template>
    </el-dialog>

    <!-- Variant detail dialog -->
    <el-dialog
      v-model="variantDialogVisible"
      title="变异详情"
      width="600px"
      :close-on-click-modal="true"
    >
      <div v-if="variantDialogData" class="variant-detail-body">
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="基因符号">
            <strong>{{ variantDialogData.geneSymbol }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="变异类型">
            {{ getVariantTypeLabel(variantDialogData.variantType) }}
          </el-descriptions-item>
          <el-descriptions-item label="染色体">
            {{ variantDialogData.chromosome }}
          </el-descriptions-item>
          <el-descriptions-item label="位置">
            {{ variantDialogData.position }}
          </el-descriptions-item>
          <el-descriptions-item label="参考碱基">
            <code class="variant-code">{{ variantDialogData.ref }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="变异碱基">
            <code class="variant-code">{{ variantDialogData.alt }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="rsID">
            {{ variantDialogData.rsId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="基因型">
            {{ variantDialogData.genotype || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="临床意义" :span="2">
            <el-tag
              :type="getClinicalSignificanceType(variantDialogData.clinicalSignificance)"
              size="small"
            >
              {{ getClinicalSignificanceLabel(variantDialogData.clinicalSignificance) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="影响等级" :span="2">
            <el-tag :type="getImpactType(variantDialogData.impact)" size="small">
              {{ getImpactLabel(variantDialogData.impact) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="等位基因频率">
            {{ variantDialogData.alleleFrequency !== undefined ? variantDialogData.alleleFrequency : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="测序深度">
            {{ variantDialogData.readDepth !== undefined ? variantDialogData.readDepth : '-' }}x
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ variantDialogData.description || '暂无描述' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <template #footer>
        <el-button @click="variantDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'GeneticsSampleList',
}
</script>

<style scoped lang="scss">
.page-container {
  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 20px;
  }
}

// ---- Filter bar ----
.filter-bar {
  background: #fff;
  border-radius: 8px;
  padding: 20px 20px 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

// ---- Toolbar ----
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    gap: 10px;
  }

  .toolbar-right {
    .total-count {
      font-size: 13px;
      color: #909399;
    }
  }
}

// ---- Pagination ----
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 0 4px;
}

// ---- Expanded variant section ----
.expanded-variant-section {
  padding: 16px 24px;
  background: #fafafa;

  .expanded-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 12px;

    .expanded-subtitle {
      font-weight: 400;
      font-size: 13px;
      color: #909399;
    }
  }

  .variant-code {
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 12px;
    background: #f4f4f5;
    padding: 2px 6px;
    border-radius: 3px;
    color: #303133;
  }

  .variant-pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}

// ---- Variant count highlight ----
.variant-count {
  font-weight: 600;
  color: #409eff;
}

// ---- VCF upload dialog ----
.upload-dialog-body {
  .upload-tip {
    font-size: 14px;
    color: #606266;
    margin: 0 0 16px;
    line-height: 1.6;
  }
}

// ---- Variant detail dialog ----
.variant-detail-body {
  .variant-code {
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 12px;
    background: #f4f4f5;
    padding: 2px 6px;
    border-radius: 3px;
    color: #303133;
  }
}
</style>
