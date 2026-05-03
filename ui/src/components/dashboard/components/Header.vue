<template>
    <TopNavBar
        :title="routeInfo.title"
        :description="props.dashboard?.description"
    >
        <template v-if="isAllowedDashboard || isAllowedFlow" #additional-right>
            <NavBarActions>
                <Dashboards
                    v-if="ALLOWED_CREATION_ROUTES.includes(String(route.name)) && isAllowedDashboard"
                    @dashboard="(value: any) => props.load?.(value)"
                />
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
    import {computed} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import {useAuthStore} from "override/stores/auth";

    const {t} = useI18n();
    const route = useRoute();
    const authStore = useAuthStore();

    import TopNavBar from "../../layout/TopNavBar.vue";
    import Dashboards from "./selector/Selector.vue";

    import NavBarActions from "../../layout/NavBarActions.vue";
    import NavBarAction from "../../layout/NavBarAction.vue";

    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Plus from "vue-material-design-icons/Plus.vue";

    import permission from "../../../models/permission";
    import action from "../../../models/action";
    import {ALLOWED_CREATION_ROUTES} from "../composables/useDashboards";

    const props = defineProps({
        dashboard: {type: Object, default: undefined},
        load: {type: Function, default: undefined},
    });

    const isAllowedFlow = computed(() => authStore.user?.isAllowed(permission.FLOW, action.CREATE, "*"));

    const isAllowedDashboard = computed(() => authStore.user?.isAllowed(permission.DASHBOARD, action.CREATE, "*"));

    const routeInfo = computed(() => ({title: props.dashboard?.title ?? t("overview")}));

    import useRouteContext from "../../../composables/useRouteContext";
    useRouteContext(routeInfo);
</script>
