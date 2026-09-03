import {defineStore} from "pinia"
import {ref, shallowRef} from "vue"
import type {KsBreadcrumbItem} from "@kestra-io/design-system"

export const useTopNavStore = defineStore("topNav", () => {
    const title = ref<string>("")
    const breadcrumb = ref<KsBreadcrumbItem[]>([])
    const bookmarkLabel = ref<string | undefined>(undefined)
    const description = ref<string | undefined>(undefined)
    const beta = ref<boolean>(false)
    const hasTitleSlot = ref<boolean>(false)
    const hasDescriptionSlot = ref<boolean>(false)
    const hideMainIcon = ref<boolean>(false)
    const ownerId = shallowRef<symbol | null>(null)

    return {
        title,
        breadcrumb,
        bookmarkLabel,
        description,
        beta,
        hasTitleSlot,
        hasDescriptionSlot,
        hideMainIcon,
        ownerId,
    }
})
