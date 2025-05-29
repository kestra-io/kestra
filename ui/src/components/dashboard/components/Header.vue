<template>
    <TopNavBar
        :title="routeInfo.title"
        :breadcrumb
        :description="props.dashboard?.description"
    >
        <template #additional-right v-if="canCreate">
            <ul>
                <li v-if="props.dashboard?.id && props.dashboard?.id !== 'default'">
                    <router-link
                        :to="{
                            name: 'dashboards/update',
                            params: {id: props.dashboard?.id},
                        }"
                        data-test-id="dashboard-update-dashboard-button"
                    >
                        <el-button :icon="Pencil">
                            {{ $t("edit_custom_dashboard") }}
                        </el-button>
                    </router-link>
                </li>
                <li v-if="!props.dashboard?.id">
                    <router-link
                        :to="{name: 'dashboards/create'}"
                        data-test-id="dashboard-create-dashboard-button"
                    >
                        <el-button :icon="ViewDashboardEdit">
                            {{ $t("create_dashboard") }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link
                        :to="{name: 'flows/create'}"
                        data-test-id="dashboard-create-button"
                    >
                        <el-button :icon="Plus" type="primary">
                            {{ $t("create_flow") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </TopNavBar>
</template>

<script setup>
    import {computed} from "vue";

    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import permission from "../../../models/permission";
    import action from "../../../models/action";

    import TopNavBar from "../../layout/TopNavBar.vue";

    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import ViewDashboardEdit from "vue-material-design-icons/ViewDashboardEdit.vue";
    import useRouteContext from "../../../mixins/useRouteContext.js";

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        dashboard: {type: Object, default: undefined},
    });

    const breadcrumb = [{label: t("dashboard_label"), link: {}}];

    const user = computed(() => store.state.auth.user);
    const canCreate = computed(() =>
        user.value.isAllowedGlobal(permission.FLOW, action.CREATE),
    );

    const routeInfo = computed(() => ({
        title: props.dashboard?.title ?? t("overview"),
    }));

    useRouteContext(routeInfo);
</script>
