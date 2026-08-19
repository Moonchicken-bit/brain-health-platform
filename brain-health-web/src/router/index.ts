import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Layouts
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'

const routes: RouteRecordRaw[] = [
  // ---- Auth routes (no sidebar) ----
  {
    path: '/login',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('@/views/auth/LoginView.vue'),
        meta: { title: '登录', requiresAuth: false },
      },
    ],
  },
  {
    path: '/forgot-password',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'ForgotPassword',
        component: () => import('@/views/auth/ForgotPasswordView.vue'),
        meta: { title: '忘记密码', requiresAuth: false },
      },
    ],
  },

  // ---- Protected routes (with sidebar) ----
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/dashboard',
    children: [
      // Dashboard
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' },
      },

      // Subject management
      {
        path: 'subjects',
        name: 'SubjectList',
        component: () => import('@/views/subject/SubjectListView.vue'),
        meta: { title: '受试者管理', icon: 'User' },
      },
      {
        path: 'subjects/register',
        name: 'SubjectRegister',
        component: () => import('@/views/subject/SubjectRegisterView.vue'),
        meta: { title: '受试者登记', hidden: true },
      },
      {
        path: 'subjects/:id',
        name: 'SubjectDetail',
        component: () => import('@/views/subject/SubjectDetailView.vue'),
        meta: { title: '受试者详情', hidden: true },
      },

      // Session
      {
        path: 'sessions/:id',
        name: 'SessionDetail',
        component: () => import('@/views/session/SessionDetailView.vue'),
        meta: { title: '访视详情', hidden: true },
      },

      // Scale assessment
      {
        path: 'scales',
        name: 'ScaleInstrumentList',
        component: () => import('@/views/scale/ScaleInstrumentList.vue'),
        meta: { title: '量表管理', icon: 'Edit' },
      },
      {
        path: 'scales/:id/assess',
        name: 'ScaleAssessmentForm',
        component: () => import('@/views/scale/ScaleAssessmentForm.vue'),
        meta: { title: '量表评估', hidden: true },
      },
      {
        path: 'assessments/:id',
        name: 'ScaleAssessmentDetail',
        component: () => import('@/views/scale/ScaleAssessmentDetail.vue'),
        meta: { title: '评估详情', hidden: true },
      },

      // Imaging
      {
        path: 'imaging',
        name: 'ImagingSessionList',
        component: () => import('@/views/imaging/ImagingSessionList.vue'),
        meta: { title: '影像数据', icon: 'PictureFilled' },
      },
      {
        path: 'imaging/upload',
        name: 'ImagingUpload',
        component: () => import('@/views/imaging/ImagingUploadView.vue'),
        meta: { title: '影像上传', hidden: true },
      },
      {
        path: 'imaging/:id',
        name: 'ImagingSessionDetail',
        component: () => import('@/views/imaging/ImagingSessionDetail.vue'),
        meta: { title: '影像详情', hidden: true },
      },
      {
        path: 'imaging/:id/viewer',
        name: 'ImagingViewer',
        component: () => import('@/views/imaging/ImagingViewer.vue'),
        meta: { title: '影像查看器', hidden: true },
      },

      // Genetics
      {
        path: 'genetics',
        name: 'GeneticsSampleList',
        component: () => import('@/views/genetics/GeneticsSampleList.vue'),
        meta: { title: '遗传数据', icon: 'Connection' },
      },
      {
        path: 'genetics/upload',
        name: 'GeneticsUpload',
        component: () => import('@/views/genetics/GeneticsUploadView.vue'),
        meta: { title: '遗传数据上传', hidden: true },
      },

      // Lab tests
      {
        path: 'lab',
        name: 'LabResultTable',
        component: () => import('@/views/lab/LabResultTableView.vue'),
        meta: { title: '检验数据', icon: 'Monitor' },
      },

      // Cross-modal search
      {
        path: 'search',
        name: 'CrossModalSearch',
        component: () => import('@/views/search/CrossModalSearchView.vue'),
        meta: { title: '跨模态检索', icon: 'Search' },
      },

      // Data export
      {
        path: 'export',
        name: 'ExportRequestList',
        component: () => import('@/views/export/ExportRequestList.vue'),
        meta: { title: '数据导出', icon: 'Download' },
      },

      // ADNI
      {
        path: 'adni',
        name: 'ADNISubjectList',
        component: () => import('@/views/adni/ADNISubjectListView.vue'),
        meta: { title: 'ADNI数据集', icon: 'FolderOpened' },
      },

      // Admin
      {
        path: 'admin/users',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagementView.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', roles: ['admin'] },
      },
      {
        path: 'admin/roles',
        name: 'RoleManagement',
        component: () => import('@/views/admin/RoleManagementView.vue'),
        meta: { title: '角色管理', icon: 'Avatar', roles: ['admin'] },
      },
      {
        path: 'admin/institutions',
        name: 'InstitutionManagement',
        component: () => import('@/views/admin/InstitutionManagementView.vue'),
        meta: { title: '机构管理', icon: 'OfficeBuilding', roles: ['admin'] },
      },
      {
        path: 'admin/projects',
        name: 'ProjectManagement',
        component: () => import('@/views/admin/ProjectManagementView.vue'),
        meta: { title: '项目管理', icon: 'Folder', roles: ['admin'] },
      },
      {
        path: 'admin/dynamic-fields',
        name: 'DynamicFieldManagement',
        component: () => import('@/views/admin/DynamicFieldManagementView.vue'),
        meta: { title: '专业字段配置', icon: 'SetUp', roles: ['admin'] },
      },
      {
        path: 'admin/visit-templates',
        name: 'VisitTemplateManagement',
        component: () => import('@/views/admin/VisitTemplateManagementView.vue'),
        meta: { title: '访视模板管理', icon: 'Collection', roles: ['admin'] },
      },
      {
        path: 'admin/visit-fields',
        name: 'VisitFieldManagement',
        component: () => import('@/views/admin/VisitFieldManagementView.vue'),
        meta: { title: '访视表单配置', icon: 'EditPen', roles: ['admin'] },
      },
      {
        path: 'admin/audit-logs',
        name: 'AuditLog',
        component: () => import('@/views/admin/AuditLogView.vue'),
        meta: { title: '审计日志', icon: 'Document', roles: ['admin'] },
      },

      // Visit Entry (core CRF form)
      {
        path: 'visit-entry',
        name: 'VisitEntry',
        component: () => import('@/views/visit/VisitEntryView.vue'),
        meta: { title: '访视录入', icon: 'EditPen' },
      },

      // Score Center
      {
        path: 'score-center',
        name: 'ScoreCenter',
        component: () => import('@/views/score/ScoreCenterView.vue'),
        meta: { title: '评分总览', icon: 'DataAnalysis' },
      },

      // Profile
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/auth/ProfileView.vue'),
        meta: { title: '个人设置', hidden: true },
      },
    ],
  },

  // Error pages
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '403', requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '404', requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// Global navigation guard
router.beforeEach(async (to, _from, next) => {
  // Set page title
  document.title = `${to.meta.title || '脑健康平台'} - 心理及脑健康数据管理平台`

  const authStore = useAuthStore()

  // Check auth requirement (default: true)
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.path || '/dashboard' } })
    return
  }

  if (authStore.isAuthenticated && !authStore.userInfo) {
    await authStore.fetchUserInfo()
  }

  const isPatient = authStore.hasAnyRole(['patient'])
  const patientAllowedRoutes = new Set(['VisitEntry', 'Profile', 'Login'])

  if (isPatient && !patientAllowedRoutes.has(String(to.name))) {
    next({ name: 'VisitEntry' })
    return
  }

  if (to.name === 'Login' && authStore.isAuthenticated) {
    next({ name: isPatient ? 'VisitEntry' : 'Dashboard' })
    return
  }

  // Check route-level permission
  if (to.meta.permission && !authStore.hasPermission(to.meta.permission as string)) {
    next({ name: 'Forbidden' })
    return
  }

  if (to.meta.roles && !authStore.hasAnyRole(to.meta.roles as string[])) {
    next({ name: 'Forbidden' })
    return
  }

  next()
})

export default router
