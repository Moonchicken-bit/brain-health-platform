<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, Upload } from '@element-plus/icons-vue'
import http from '@/api/client'

const router = useRouter()

// ---- State ----
const loading = ref(false)
const modalities = ref<{id:number;name:string;code:string}[]>([])
const sessions = ref<any[]>([])
const pagination = reactive({ page: 1, size: 15, total: 0 })

const filterForm = reactive({
  subjectId: '',
  modalityId: undefined as number | undefined,
  qcStatus: '',
})

async function loadModalities() {
  try {
    const res = await http.get('/api/v1/imaging/modalities?_t=' + Date.now())
    if (res.data?.code === 200) modalities.value = res.data.data || []
  } catch { modalities.value = [] }
}

async function loadSessions() {
  loading.value = true
  try {
    const params: any = { page: pagination.page, size: pagination.size }
    if (filterForm.subjectId) params.subjectId = filterForm.subjectId
    if (filterForm.modalityId) params.modalityId = filterForm.modalityId
    if (filterForm.qcStatus) params.qcStatus = filterForm.qcStatus

    const res = await http.get('/api/v1/imaging/sessions', { params })
    if (res.data?.code === 200) {
      sessions.value = res.data.data?.records || res.data.data || []
      pagination.total = res.data.data?.total || 0
    }
  } catch { sessions.value = [] } finally { loading.value = false }
}

function handleSearch() { pagination.page = 1; loadSessions() }
function handleReset() { filterForm.subjectId = ''; filterForm.modalityId = undefined; filterForm.qcStatus = ''; handleSearch() }

onMounted(() => { loadModalities(); loadSessions() })
</script>

<template>
  <div class="page-container">
    <div class="page-title">影像数据管理</div>

    <!-- Filter -->
    <div class="filter-bar">
      <el-input v-model="filterForm.subjectId" placeholder="受试者ID" clearable style="width:180px" @keyup.enter="handleSearch" />
      <el-select v-model="filterForm.modalityId" placeholder="模态" clearable style="width:160px">
        <el-option v-for="m in modalities" :key="m.id" :label="m.name" :value="m.id" />
      </el-select>
      <el-select v-model="filterForm.qcStatus" placeholder="QC状态" clearable style="width:140px">
        <el-option label="通过" value="Passed" />
        <el-option label="失败" value="Failed" />
        <el-option label="待审" value="Pending" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
      <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
    </div>

    <!-- Upload -->
    <div style="margin-bottom:16px">
      <el-button type="primary" :icon="Upload" @click="router.push('/imaging/upload')">上传影像</el-button>
    </div>

    <!-- Table -->
    <el-table v-loading="loading" :data="sessions" stripe border style="width:100%">
      <el-table-column label="受试者ID" width="140">
        <template #default="{row}">{{ row.subjectId || row.subject_id || '-' }}</template>
      </el-table-column>
      <el-table-column label="模态" width="120">
        <template #default="{row}">{{ modalities.find(m=>m.id===row.modalityId)?.name || row.modalityName || '-' }}</template>
      </el-table-column>
      <el-table-column label="采集日期" width="130">
        <template #default="{row}">{{ row.acquisitionDate || row.acquisition_date || '-' }}</template>
      </el-table-column>
      <el-table-column label="序列数" width="80" align="center">
        <template #default="{row}">{{ row.seriesCount ?? row.series_count ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="QC状态" width="100">
        <template #default="{row}">
          <el-tag :type="row.qcStatus==='Passed'?'success':row.qcStatus==='Failed'?'danger':'warning'" size="small">
            {{ row.qcStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="预处理" width="100">
        <template #default="{row}">
          <el-tag :type="row.preprocessingStatus==='Completed'?'success':'info'" size="small">
            {{ row.preprocessingStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" size="small" @click="router.push(`/imaging/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div style="display:flex;justify-content:flex-end;margin-top:16px">
      <el-pagination
        v-model:current-page="pagination.page" v-model:page-size="pagination.size"
        :total="pagination.total" :page-sizes="[10,15,20,30]" layout="total,sizes,prev,pager,next"
        background @current-change="loadSessions" @size-change="loadSessions"
      />
    </div>
  </div>
</template>

<script lang="ts">export default { name: 'ImagingSessionList' }</script>
