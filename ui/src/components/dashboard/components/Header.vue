<template>
    <TopNavBar
        :title="routeInfo.title"
        :description="props.dashboard?.description"
        :hideMainIcon="showSelector"
    >
        <template
            v-if="showSelector"
            #title
        >
            <Dashboards
                placement="bottom-start"
                @dashboard="(value: string) => props.load?.(value)"
            >
                <button type="button" class="dashboard-trigger">
                    <KsIcon size="sm" class="leading">
                        <ChartLineVariant />
                    </KsIcon>
                    <span class="text-truncate">{{ routeInfo.title }}</span>
                    <KsIcon size="sm" class="chevron">
                        <ChevronDown />
                    </KsIcon>
                </button>
            </Dashboards>
        </template>

        <template v-if="isAllowedDashboard || isAllowedFlow" #actions>
            <NavBarActions>
                <NavBarAction
                    v-if="props.dashboard?.id && props.dashboard?.id !== 'default' && isAllowedDashboard"
                    :icon="Pencil"
                    :label="$t('dashboards.edition.label')"
                    :to="{name: 'dashboards/update', params: {dashboard: props.dashboard.id}}"
                />

                <template #primary>
                    <NavBarAction
                        v-if="isAllowedFlow"
                        type="primary"
                        :icon="Plus"
                        :label="$t('create_flow')"
                        :to="{name: 'flows/create'}"
                    />
                </template>
            </NavBarActions>
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useAuthStore} from "override/stores/auth"

    const {t} = useI18n()
    const route = useRoute()
    const authStore = useAuthStore()

    import TopNavBar from "../../layout/TopNavBar.vue"
    import Dashboards from "./selector/Selector.vue"

    import NavBarActions from "../../layout/NavBarActions.vue"
    import NavBarAction from "../../layout/NavBarAction.vue"

    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Plus from "vue-material-design-icons/Plus.vue"

    import resource from "../../../models/resource"
    import action from "../../../models/action"
    import {ALLOWED_CREATION_ROUTES} from "../composables/useDashboards"

    const props = defineProps({
        dashboard: {type: Object, default: undefined},
        load: {type: Function, default: undefined},
    })

    const isAllowedFlow = computed(() => authStore.user?.isAllowed(resource.FLOW, action.CREATE, "*"))

    const isAllowedDashboard = computed(() => authStore.user?.isAllowed(resource.DASHBOARD, action.CREATE, "*"))

    const showSelector = computed(() =>
        ALLOWED_CREATION_ROUTES.includes(String(route.name)) && isAllowedDashboard.value,
    )

    const routeInfo = computed(() => ({title: props.dashboard?.title ?? t("overview")}))

    import useRouteContext from "../../../composables/useRouteContext"
    useRouteContext(routeInfo)
</script>

<style scoped lang="scss">
    .dashboard-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2);
        background: none;
        border: none;
        border-radius: var(--ks-radius-sm);
        font: inherit;
        color: inherit;
        cursor: pointer;
        transition: background-color 0.15s ease, color 0.15s ease;

        .leading {
            flex-shrink: 0;
        }

        .chevron {
            flex-shrink: 0;
            color: var(--ks-text-secondary);
            transition: transform 0.1s ease;
        }

        &:hover {
            background-color: var(--ks-bg-hover);

            .leading {
                color: var(--ks-icon-active);
            }
        }

        &[aria-expanded="true"] .chevron {
            transform: rotate(180deg);
        }
    }
</style>
