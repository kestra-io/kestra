<template>
    <TopNavBar
        :title="route.title"
        :breadcrumb="[{label: t('dashboards.labels.singular'), link: {}}]"
        :description="props.dashboard?.description"
    >
        <template v-if="isAllowed" #additional-right>
            <ul>
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
                <li v-if="!props.dashboard?.id">
                    <router-link :to="{name: 'dashboards/create'}">
                        <el-button :icon="ViewDashboardEdit">
                            {{ t("dashboards.creation.label") }}
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

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import TopNavBar from "../../layout/TopNavBar.vue";

    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import ViewDashboardEdit from "vue-material-design-icons/ViewDashboardEdit.vue";

    import permission from "../../../models/permission";
    import action from "../../../models/action";

    const props = defineProps({dashboard: {type: Object, default: undefined}});

    const user = computed(() => store.state.auth.user);
    const isAllowed = computed(() => user.value.isAllowedGlobal(permission.FLOW, action.CREATE));

    const route = computed(() => ({title: props.dashboard?.title ?? t("overview")}));

    import useRouteContext from "../../../mixins/useRouteContext";
    useRouteContext(route);
</script>
