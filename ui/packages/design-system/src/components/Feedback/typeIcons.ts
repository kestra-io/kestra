import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
import InformationSlabCircleOutline from "vue-material-design-icons/InformationSlabCircleOutline.vue"

export const TYPE_ICONS = {
    success: CheckCircleOutline,
    info: InformationSlabCircleOutline,
    error: AlertBoxOutline,
    warning: AlertOutline,
} as const

export type FeedbackType = keyof typeof TYPE_ICONS
