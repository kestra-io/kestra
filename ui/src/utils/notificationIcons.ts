import type {Component} from "vue"
import BellOutline from "vue-material-design-icons/BellOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AccountCheckOutline from "vue-material-design-icons/AccountCheckOutline.vue"

// Producers register an entry here when they introduce a notification type that deserves
// its own icon (e.g. an EE case-management feature, async-operation progress). Any
// unregistered type falls back to BellOutline in the caller.
export const NOTIFICATION_ICONS: Record<string, Component> = {
    GENERIC: BellOutline,
    HUMAN_TASK_PENDING: AccountCheckOutline,
    SYSTEM_EXECUTION_FAILED: AlertCircleOutline,
}

export function registerNotificationIcon(type: string, icon: Component) {
    NOTIFICATION_ICONS[type] = icon
}
