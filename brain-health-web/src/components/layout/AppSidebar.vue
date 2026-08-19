<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  collapsed: boolean
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

interface MenuItem {
  path: string
  title: string
  icon: string
  permission?: string
  children?: MenuItem[]
}

const businessMenuItems: MenuItem[] = [
  { path: '/dashboard', title: '工作台', icon: 'HomeFilled' },
  { path: '/subjects', title: '受试者管理', icon: 'User' },
  { path: '/visit-entry', title: '访视录入', icon: 'EditPen' },
  { path: '/score-center', title: '评分总览', icon: 'DataAnalysis' },
  { path: '/imaging', title: '影像数据', icon: 'PictureFilled' },
  { path: '/genetics', title: '遗传数据', icon: 'Connection' },
  { path: '/lab', title: '实验室', icon: 'Monitor' },
  { path: '/search', title: '数据检索', icon: 'Search' },
  { path: '/export', title: '数据导出', icon: 'Download' },
  { path: '/adni', title: 'ADNI数据集', icon: 'FolderOpened' },
]

const adminMenuItems: MenuItem[] = [
  ...businessMenuItems,
  { path: '/admin/users', title: '账号管理', icon: 'UserFilled' },
  { path: '/admin/institutions', title: '机构管理', icon: 'OfficeBuilding' },
  { path: '/admin/projects', title: '项目管理', icon: 'Folder' },
  { path: '/admin/dynamic-fields', title: '专业字段配置', icon: 'SetUp' },
  { path: '/admin/visit-templates', title: '访视模板管理', icon: 'Collection' },
  { path: '/admin/visit-fields', title: '访视表单配置', icon: 'EditPen' },
]

const patientMenuItems: MenuItem[] = [
  { path: '/visit-entry', title: '我的量表', icon: 'EditPen' },
]

const isPatient = computed(() => authStore.hasAnyRole(['patient', 'PATIENT']))
const isAdmin = computed(() => authStore.hasAnyRole(['admin', 'ADMIN']))
const menuItems = computed(() =>
  isPatient.value ? patientMenuItems : (isAdmin.value ? adminMenuItems : businessMenuItems)
)

const activeMenu = computed(() => {
  const { path } = route
  // Find the matching menu item
  for (const item of menuItems.value) {
    if (path.startsWith(item.path)) {
      return item.path
    }
  }
  return path
})

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="sidebar">
    <!-- Logo area -->
    <div class="sidebar-logo">
      <el-icon :size="28"><Monitor /></el-icon>
      <span v-show="!collapsed" class="logo-text">脑健康平台</span>
    </div>

    <!-- Navigation menu -->
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
      router
    >
      <el-menu-item
        v-for="item in menuItems"
        :key="item.path"
        :index="item.path"
        @click="navigate(item.path)"
      >
        <el-icon>
          <component :is="item.icon" />
        </el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped lang="scss">
.sidebar {
  height: 100%;
}

.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .logo-text {
    margin-left: 10px;
    font-size: 18px;
    font-weight: bold;
    white-space: nowrap;
  }
}

.el-menu {
  border-right: none;
}
</style>
