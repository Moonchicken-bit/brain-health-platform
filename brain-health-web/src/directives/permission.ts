import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission directive for element-level access control.
 * Usage: <el-button v-permission="'subject:create'">新增</el-button>
 * Usage: <div v-permission="['subject:view', 'subject:edit']">...</div> (any match)
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const authStore = useAuthStore()
    const required = binding.value

    if (!required) return

    const permissions = Array.isArray(required) ? required : [required]
    const hasAccess = permissions.some((p: string) => authStore.hasPermission(p))

    if (!hasAccess) {
      el.parentNode?.removeChild(el)
    }
  },
}
