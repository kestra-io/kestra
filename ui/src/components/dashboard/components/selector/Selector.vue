<template>
    <el-dropdown trigger="click" hideOnClick placement="bottom-end">
        <el-button :icon="ChartLineVariant" class="selected">
            <span v-if="!verticalLayout" class="text-truncate">
                {{ selected?.title ?? $t('dashboards.default') }}
            </span>
        </el-button>

        <template #dropdown>
            <el-dropdown-menu class="p-3 dropdown">
                <el-button
                    type="primary"
                    :icon="Plus"
                    tag="router-link"
                    :to="{name: 'dashboards/create', query}"
                    class="w-100"
                >
                    <small>{{ $t("dashboards.creation.label") }}</small>
                </el-button>

                <Item
                    :dashboard="{
                        id: filtered.filter(d => d.title === selected?.title)?.[0]?.id ?? 'default',
                        title: selected?.title ?? $t('dashboards.default')
                    }"
                    :edit="edit"
                    :setAsDefault="setAsTenantDefault"
                    class="mt-3"
                />

                <hr class="my-2">

                <el-input
                    v-model="search"
                    :placeholder="$t('search')"
                    :prefixIcon="Magnify"
                    clearable
                    class="my-1 mb-3 search"
                />

                <div class="overflow-x-auto items">
                    <Item
                        v-for="(dashboard, index) in filtered"
                        :key="index"
                        :dashboard
                        :edit="edit"
                        :remove="remove"
                        :setAsDefault="setAsTenantDefault"
                        @click="select(dashboard)"
                    />
                    <span v-if="!filtered.length" class="empty">
                        {{ $t("dashboards.empty") }}
                    </span>
                </div>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {onBeforeMount, ref, computed, watch} from "vue";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../../../utils/toast";
    const toast = useToast();

    import {useDashboardStore} from "../../../../stores/dashboard";
    const dashboardStore = useDashboardStore();


    import Item from "./Item.vue";

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm");

    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import {Dashboard} from "../../types.ts";


    const emits = defineEmits(["dashboard"]);

    const rootName = computed(() => ["flows/update", "namespaces/update"].includes(route.name as string) ? route.name : "home")
    const query = computed(() => {
        return {
            name: rootName.value,
            params: JSON.stringify({...route.params, dashboard: undefined}),
        };
    });

    const search = ref("");
    const dashboards = ref<{ id: string; title: string }[]>([]);
    const filtered = computed(() => {
        const DEFAULT = {id: "default", title: t("dashboards.default")};

        return [DEFAULT, ...dashboards.value].filter((d) => !search.value || d.title.toLowerCase().includes(search.value.toLowerCase()));
    });


    const selected = ref<Dashboard|undefined>(undefined);

    const select = (dashboard: any) => {
        emits("dashboard", dashboard.id);
    };

    const setAsTenantDefault = (id: string) => {
        switch (rootName.value){
        case "flows/update": dashboardStore.saveDefaults({defaultFlowOverviewDashboard: id}); break;
        case "namespaces/update": dashboardStore.saveDefaults({defaultNamespaceOverviewDashboard: id}); break;
        default: dashboardStore.saveDefaults({defaultHomeDashboard: id});
        }
    };

    const edit = (id: string) => {
        router.push({name: "dashboards/update", params: {dashboard: id}});
    };

    const remove = (dashboard: {title: string, id: string}) => {
        toast.confirm(t("dashboards.deletion.confirmation", {title: dashboard.title}), () => {
            return dashboardStore.delete(dashboard.id).then(() => {
                dashboards.value = dashboards.value.filter((d) => d.id !== dashboard.id);
                toast.deleted(dashboard.title);
            });
        });
    };

    const fetchDashboards = () => {
        dashboardStore
            .list({})
            .then((response: { results: { id: string; title: string }[] }) => {
                dashboards.value = response.results;
            });
    };

    onBeforeMount(() => {
        fetchDashboards();
        const dashboardId = dashboardStore.getDashboardRelatedToThisRoute(route);
        if(dashboardId){
            dashboardStore.load(dashboardId).then(dash => selected.value=dash);
        } else {
            selected.value = undefined;
        }
    });

    const tenant = ref();
    watch(() => route.params.tenant, (t) => {
        if (tenant.value !== t) {
            fetchDashboards();
            tenant.value = t;
        }
    }, {immediate: true});


</script>

<style scoped lang="scss">
.selected {
    span{
        font-size: 14px;
    }
}
.dropdown {
    width: 300px;

    .search {
        font-size: revert;
    }

    :deep(li.el-dropdown-menu__item) {
        &:hover,
        &:focus {
            background: var(--ks-select-hover);
        }
    }
}

.items {
    max-height: 193.4px !important; // 5 visible items

    :deep(li.el-dropdown-menu__item) {
        border-radius: unset;
    }
}
</style>
