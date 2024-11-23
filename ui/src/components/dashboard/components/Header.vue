<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="props.breadcrumb">
        <template #additional-right v-if="canCreate">
            <ul>
                <li v-if="props.id">
                    <router-link
                        :to="{
                            name: 'dashboards/update',
                            params: {id: props.id},
                        }"
                    >
                        <el-button :icon="Pencil">
                            {{ $t("edit_custom_dashboard") }}
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

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        title: {type: String, default: undefined},
        breadcrumb: {type: Array, default: () => []},
        id: {type: String, default: undefined},
    });

    const user = computed(() => store.state.auth.user);
    const canCreate = computed(() =>
        user.value.isAllowedGlobal(permission.FLOW, action.CREATE),
    );

    const routeInfo = computed(() => ({
        title: props.title ?? t("homeDashboard.title"),
    }));
</script>
