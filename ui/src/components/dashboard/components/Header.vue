<template>
    <TopNavBar
        :title="routeInfo.title"
        :description="props.dashboard?.description"
        :breadcrumb="breadcrumb"
        :titleSiblings="titleSiblings"
    >
        <template v-if="showSelector || isAllowedFlow" #actions>
            <Dashboards
                v-if="showSelector && !canManageDashboards"
                @dashboard="(value: string) => props.load?.(value)"
            />
            <NavBarActionsDropdown v-if="canManageDashboards">
                <template v-if="isNonDefaultDashboard">
                    <NavBarAction
                        :icon="Pencil"
                        :label="$t('dashboards.edition.label')"
                        :to="{name: 'dashboards/update', params: {dashboard: props.dashboard?.id}}"
                    />
                    <NavBarAction
                        :icon="isTenantDefault ? Bookmark : BookmarkOutline"
                        :label="$t('dashboards.set_default')"
                        @click="setAsDefault"
                    />
                    <NavBarAction
                        :icon="Delete"
                        :label="$t('delete')"
                        danger
                        @click="remove"
                    />
                </template>
                <NavBarAction
                    :icon="Plus"
                    :label="$t('dashboards.creation.label')"
                    :to="{name: 'dashboards/create'}"
                    :divided="isNonDefaultDashboard"
                />
            </NavBarActionsDropdown>
            <NavBarAction
                v-if="isAllowedFlow"
                type="primary"
                :icon="Plus"
                :label="$t('create_flow')"
                :to="{name: 'flows/create'}"
            />
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "@kestra-io/design-system"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import {useDashboardStore} from "../../../stores/dashboard"
    import {useToast} from "../../../utils/toast"

    const {t} = useI18n()
    const route = useRoute()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()
    const dashboardStore = useDashboardStore()
    const toast = useToast()

    import TopNavBar from "../../layout/TopNavBar.vue"
    import Dashboards from "override/components/dashboard/Selector.vue"

    import NavBarActionsDropdown from "../../layout/NavBarActionsDropdown.vue"
    import NavBarAction from "../../layout/NavBarAction.vue"

    import Bookmark from "vue-material-design-icons/Bookmark.vue"
    import BookmarkOutline from "vue-material-design-icons/BookmarkOutline.vue"
    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Plus from "vue-material-design-icons/Plus.vue"

    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import {ALLOWED_CREATION_ROUTES} from "../composables/useDashboards"
    import {routeFamily} from "../../../utils/routeFamily"

    const props = defineProps({
        dashboard: {type: Object, default: undefined},
        load: {type: Function, default: undefined},
    })

    const isAllowedFlow = computed(() => authStore.user?.isAllowed(resource.FLOW, action.CREATE, "*"))

    const isAllowedDashboard = computed(() => authStore.user?.isAllowed(resource.DASHBOARD, action.CREATE, "*"))

    const isCustomDashboardsDisabled = computed(() =>
        miscStore.configs?.isCustomDashboardsEnabled === false,
    )

    const isOSS = computed(() => miscStore.configs?.edition === "OSS")

    const canManageDashboards = computed(() => isAllowedDashboard.value && !isOSS.value)

    const isNonDefaultDashboard = computed(() =>
        !!props.dashboard?.id && props.dashboard.id !== "default",
    )

    const showSelector = computed(() =>
        ALLOWED_CREATION_ROUTES.includes(routeFamily(route.name)) && (isAllowedDashboard.value || isCustomDashboardsDisabled.value),
    )

    const dashboards = ref<{id: string; title: string; isDefault: boolean}[]>([])

    const isTenantDefault = computed(() => dashboards.value.find((d) => d.id === props.dashboard?.id)?.isDefault ?? false)

    const fetchDashboards = async () => {
        dashboards.value = await dashboardStore.list({}, route)
    }

    // A new loader per open dashboard, so the breadcrumb menu reloads and moves its current entry on a switch.
    const titleSiblings = computed<KsBreadcrumbLoader | undefined>(() => {
        if (!canManageDashboards.value) return undefined
        const current = props.dashboard?.id
        return async () => {
            await fetchDashboards()
            if (dashboards.value.length < 2) return []
            return dashboards.value.map((d) => ({
                label: d.id === "default" ? t("dashboards.default") : d.title,
                icon: ChartLineVariant,
                current: d.id === current,
                onClick: () => props.load?.(d.id),
            }))
        }
    })

    const breadcrumb = computed<KsBreadcrumbItem[]>(() => titleSiblings.value ? [{label: t("dashboards.labels.plural")}] : [])

    const setAsDefault = async () => {
        await dashboardStore.saveDefaults({defaultHomeDashboard: props.dashboard?.id})
        await fetchDashboards()
    }

    const remove = () => {
        const {id, title} = props.dashboard ?? {}
        return toast.confirm(t("delete confirm", {name: title ?? id}), async () => {
            await dashboardStore.delete(id)
            toast.deleted(title ?? id)
            await props.load?.("default")
        })
    }

    const routeInfo = computed(() => ({title: props.dashboard?.title || t("dashboards.default")}))

    import useRouteContext from "../../../composables/useRouteContext"
    useRouteContext(routeInfo)
</script>
