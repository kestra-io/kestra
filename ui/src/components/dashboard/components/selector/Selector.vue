<template>
    <template v-if="asItem">
        <KsDropdownItem :icon="ChartLineVariant" @click="isOpen = true">
            {{ selected?.title ?? $t("dashboards.default") }}
        </KsDropdownItem>
        <KsDialog
            v-model="isOpen"
            destroyOnClose
            appendToBody
        >
            <template #header>
                <h5 class="mb-0">
                    {{ $t("dashboards.switch") }}
                </h5>
            </template>
            <Content
                :dashboards="dashboards"
                :selected="selected"
                :query="query"
                @select="onSelect"
                @set-default="setAsTenantDefault"
                @edit="edit"
                @remove="remove"
            />
        </KsDialog>
    </template>
    <KsDropdown v-else trigger="click" hideOnClick :placement>
        <slot>
            <KsButton :icon="ChartLineVariant" class="selected">
                <span v-if="!verticalLayout" class="text-truncate">
                    {{ selected?.title ?? $t('dashboards.default') }}
                </span>
            </KsButton>
        </slot>
        <template #dropdown>
            <KsDropdownMenu class="p-2 dropdown">
                <Content
                    :dashboards="dashboards"
                    :selected="selected"
                    :query="query"
                    @select="onSelect"
                    @set-default="setAsTenantDefault"
                    @edit="edit"
                    @remove="remove"
                />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {ref, computed, inject, watch} from "vue"

    import {useRoute, useRouter} from "vue-router"
    import Content from "./Content.vue"
    import {asItemKey} from "../../../layout/navBarActionsContext"

    const asItem = inject(asItemKey, false)

    const route = useRoute()
    const router = useRouter()
    import {useActiveTab} from "../../../../composables/useActiveTab"
    const activeTab = useActiveTab()
    const isRouterDriven = computed(() => route.meta?.tab !== undefined)

    import {useI18n} from "vue-i18n"
    const {t} = useI18n({useScope: "global"})

    import {useToast} from "../../../../utils/toast"
    const toast = useToast()

    import {useDashboardStore} from "../../../../stores/dashboard"
    const dashboardStore = useDashboardStore()

    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"

    withDefaults(defineProps<{placement?: string}>(), {placement: "bottom-end"})

    const emits = defineEmits<{dashboard: [id: string]}>()

    const isOpen = ref(false)

    const rootName = computed(() => {
        const name = String(route.name ?? "")
        if (name.startsWith("flows/update")) return "flows/update"
        if (name.startsWith("namespaces/update")) return "namespaces/update"
        return "home"
    })
    // Pages migrated to router children (e.g. flows/update) no longer carry the
    // active tab in route.params, so re-derive it via useActiveTab and bake it into
    // the target route name directly rather than a `tab` param (which would be
    // silently discarded before the parent route's redirect runs).
    const query = computed(() => {
        const {tab: _tab, ...restParams} = route.params as Record<string, unknown>
        const name = rootName.value === "home" || !isRouterDriven.value
            ? rootName.value
            : `${rootName.value}/${activeTab.value}`
        const params = rootName.value === "home" || isRouterDriven.value
            ? restParams
            : {...restParams, tab: activeTab.value}
        return {
            name,
            params: JSON.stringify({...params, dashboard: undefined}),
        }
    })

    const dashboards = ref<{id: string; title: string; isDefault: boolean}[]>([])

    const selected = computed(() => dashboardStore.activeDashboard
        ? {id: dashboardStore.activeDashboard.id, title: dashboardStore.activeDashboard.title ?? dashboardStore.activeDashboard.id}
        : undefined)

    const onSelect = (id: string) => {
        emits("dashboard", id)
        isOpen.value = false
    }

    const setAsTenantDefault = async (id: string) => {
        switch (rootName.value){
        case "flows/update": await dashboardStore.saveDefaults({defaultFlowOverviewDashboard: id}); break
        case "namespaces/update": await dashboardStore.saveDefaults({defaultNamespaceOverviewDashboard: id}); break
        default: await dashboardStore.saveDefaults({defaultHomeDashboard: id})
        }
        await fetchDashboards()
    }

    const edit = (id: string) => {
        router.push({name: "dashboards/update", params: {dashboard: id}})
    }

    const remove = (dashboard: {title: string, id: string}) => {
        toast.confirm(t("dashboards.deletion.confirmation", {title: dashboard.title}), () => {
            return dashboardStore.delete(dashboard.id).then(() => {
                dashboards.value = dashboards.value.filter((d) => d.id !== dashboard.id)
                toast.deleted(dashboard.title)
            })
        })
    }

    const fetchDashboards = async () => {
        dashboards.value = await dashboardStore.list({}, route)
    }

    fetchDashboards()
    watch(() => route.params.tenant, fetchDashboards)

</script>

<style scoped lang="scss">
.selected {
    span{
        font-size: var(--ks-font-size-sm);
    }
}
.dropdown {
    width: 18rem;
}
</style>
