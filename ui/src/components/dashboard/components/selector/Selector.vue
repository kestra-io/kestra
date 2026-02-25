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
                        id: filtered.filter(d => d.id === selected?.id)?.[0]?.id ?? 'default',
                        title: (selected?.title ?? $t('dashboards.default')),
                        isDefault: filtered.filter(d => d.id === selected?.id)?.[0]?.isDefault
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


    const emits = defineEmits(["dashboard"]);

    const rootName = computed(() => ["flows/update", "namespaces/update"].includes(route.name as string) ? route.name : "home");
    const query = computed(() => {
        return {
            name: rootName.value,
            params: JSON.stringify({...route.params, dashboard: undefined}),
        };
    });

    const search = ref("");
    const dashboards = ref<{ id: string; title: string, isDefault: boolean }[]>([]);
    const filtered = computed<{id: string, title: string, isDefault: boolean}[]>(() => {
        return dashboards.value.filter((d) => !search.value || d.title.toLowerCase().includes(search.value.toLowerCase()));
    });


    const selected = computed(() => {
        if(dashboardStore.activeDashboard){
            return {id: dashboardStore.activeDashboard.id, title:dashboardStore.activeDashboard.title ?? dashboardStore.activeDashboard.id}
        } else {
            return undefined
        }
    });

    const select = (dashboard: {id: string}) => {
        emits("dashboard", dashboard.id);
    };

    const setAsTenantDefault = async (id: string) => {
        switch (rootName.value){
        case "flows/update": await dashboardStore.saveDefaults({defaultFlowOverviewDashboard: id}); break;
        case "namespaces/update": await dashboardStore.saveDefaults({defaultNamespaceOverviewDashboard: id}); break;
        default: await dashboardStore.saveDefaults({defaultHomeDashboard: id});
        }
        dashboards.value = []
        await fetchDashboards()
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

    const fetchDashboards = async () => {
        dashboards.value = await dashboardStore.list({}, route) ;
    };

    onBeforeMount(() => {
        fetchDashboards();
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
