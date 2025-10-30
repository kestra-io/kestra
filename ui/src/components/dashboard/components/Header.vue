<template>
    <TopNavBar
        :title="routeInfo.title"
        :breadcrumb="[{label: t('dashboards.labels.singular'), link: undefined}]"
        :description="props.dashboard?.description"
    >
        <template v-if="isAllowed" #additional-right>
            <ul>
                <li
                    v-if="ALLOWED_CREATION_ROUTES.includes(String(route.name))"
                >
                    <Dashboards
                        @dashboard="(value: any) => props.load?.(value)"
                        class="me-1"
                    />
                </li>
                <li
                    v-if="props.dashboard?.id && props.dashboard?.id !== 'default'"
                >
                    <router-link
                        :to="{name: 'dashboards/update', params: {id: props.dashboard?.id}}"
                    >
                        <el-button :icon="Pencil">
                            {{ t("dashboards.edition.label") }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link :to="{name: 'flows/create'}">
                        <el-button :icon="Plus" type="primary">
                            {{ t("create_flow") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
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

    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Plus from "vue-material-design-icons/Plus.vue";

    import permission from "../../../models/permission";
    import action from "../../../models/action";
    import {ALLOWED_CREATION_ROUTES} from "../composables/useDashboards";

    const props = defineProps({
        dashboard: {type: Object, default: undefined},
        load: {type: Function, default: undefined},
    });

    const user = computed(() => authStore.user);
    const isAllowed = computed(() => user.value.isAllowedGlobal(permission.FLOW, action.CREATE));

    const routeInfo = computed(() => ({title: props.dashboard?.title ?? t("overview")}));

    import useRouteContext from "../../../composables/useRouteContext";
    useRouteContext(routeInfo);
</script>
