import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import App from './App.vue'
import router from './router'
import { permissionDirective } from './directives/permission'
import './assets/styles/global.scss'

const app = createApp(App)

// Pinia state management
const pinia = createPinia()
app.use(pinia)

// Vue Router
app.use(router)

// Element Plus UI framework (Chinese locale)
app.use(ElementPlus, { locale: zhCn })

// Register all Element Plus icons globally
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// Custom directives
app.directive('permission', permissionDirective)

app.mount('#app')
