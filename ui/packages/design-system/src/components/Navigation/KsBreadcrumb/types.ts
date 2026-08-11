import type {RouterLink} from "vue-router"

type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"]

export interface KsBreadcrumbItem {
    label: string
    link?: RouterLinkTo
    disabled?: boolean
    onClick?: () => void
}
