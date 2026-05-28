<template>
    <template v-if="asItem">
        <KsDropdownItem :icon="SwapHorizontal" @click="isOpen = true">
            {{ selected?.title ?? $t("dashboards.default") }}
        </KsDropdownItem>
        <KsDialog
            v-model="isOpen"
            destroyOnClose
            appendToBody
            width="360px"
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
    <KsDropdown v-else trigger="click" hideOnClick placement="bottom-end">
        <slot>
            <KsButton :icon="SwapHorizontal" class="selected">
                <span v-if="!verticalLayout" class="text-truncate">
                    {{ selected?.title ?? $t('dashboards.default') }}
                </span>
            </KsButton>
        </slot>
        <template #dropdown>
            <KsDropdownMenu class="p-3 dropdown">
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
    import {onBeforeMount, ref, computed, inject, watch} from "vue"

    import {useRoute, useRouter} from "vue-router"
    import Content from "./Content.vue"
    import {asItemKey} from "../../../layout/navBarActionsContext"

    const asItem = inject(asItemKey, false)

    const route = useRoute()
    const router = useRouter()

    import {useI18n} from "vue-i18n"
    const {t} = useI18n({useScope: "global"})

    import {useToast} from "../../../../utils/toast"
    const toast = useToast()

    import {useDashboardStore} from "../../../../stores/dashboard"
    const dashboardStore = useDashboardStore()

    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    import SwapHorizontal from "vue-material-design-icons/SwapHorizontal.vue"

    const emits = defineEmits<{dashboard: [id: string]}>()

    const isOpen = ref(false)

    const rootName = computed(() => ["flows/update", "namespaces/update"].includes(route.name as string) ? route.name : "home")
    const query = computed(() => ({
        name: rootName.value,
        params: JSON.stringify({...route.params, dashboard: undefined}),
    }))

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
        dashboards.value = []
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

    onBeforeMount(fetchDashboards)

    const tenant = ref()
    watch(() => route.params.tenant, (newTenant) => {
        if (tenant.value !== newTenant) {
            fetchDashboards()
            tenant.value = newTenant
        }
    }, {immediate: true})

</script>

<style scoped lang="scss">
.selected {
    span{
        font-size: var(--ks-font-size-sm);
    }
}
.dropdown {
    width: 300px;
}
</style>
