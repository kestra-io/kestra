<template>
    <TopNavBar
        :title="routeInfo.title"
        :description="props.dashboard?.description"
    >
        <template v-if="showSelector || isAllowedFlow" #actions>
            <Dashboards
                v-if="showSelector"
                @dashboard="(value: string) => props.load?.(value)"
            />
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
    import {computed} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"

    const {t} = useI18n()
    const route = useRoute()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()

    import TopNavBar from "../../layout/TopNavBar.vue"
    import Dashboards from "override/components/dashboard/Selector.vue"

    import NavBarAction from "../../layout/NavBarAction.vue"

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

    const showSelector = computed(() =>
        ALLOWED_CREATION_ROUTES.includes(routeFamily(route.name)) && (isAllowedDashboard.value || isCustomDashboardsDisabled.value),
    )

    const routeInfo = computed(() => ({title: props.dashboard?.title || t("overview")}))

    import useRouteContext from "../../../composables/useRouteContext"
    useRouteContext(routeInfo)
</script>
