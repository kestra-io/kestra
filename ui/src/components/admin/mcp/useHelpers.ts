import {computed, Ref} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

interface Breadcrumb {
    label: string;
    link?: {name: string; params?: Record<string, string>};
    disabled?: boolean;
}

interface Details {
    title: string;
    breadcrumb: Breadcrumb[];
}

export function useHelpers() {
    const route = useRoute()
    const {t} = useI18n({useScope: "global"})

    const serverId = computed(() => route.params?.id as string | undefined)
    const isCreate = computed(() => !serverId.value)

    const details: Ref<Details> = computed(() => ({
        title: isCreate.value ? t("mcp.create") : (serverId.value ?? t("mcp.servers")),
        breadcrumb: [
            {label: t("mcp.servers"), link: {name: "admin/mcp-servers"}},
        ],
    }))

    return {details, serverId, isCreate}
}
