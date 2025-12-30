<template>
    <el-dropdown trigger="click" hideOnClick placement="bottom-end">
        <el-button :icon="ChartLineVariant" class="selected">
            <span v-if="!verticalLayout" class="text-truncate">
                {{ selected ?? t("dashboards.default") }}
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
                    <small>{{ t("dashboards.creation.label") }}</small>
                </el-button>

                <Item
                    :dashboard="{
                        id: filtered.filter(d => d.title === selected)?.[0]?.id ?? 'default',
                        title: selected ?? t('dashboards.default')
                    }"
                    :edit="edit"
                    class="mt-3"
                />

                <hr class="my-2">

                <el-input
                    v-model="search"
                    :placeholder="t('search')"
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
                        @click="select(dashboard)"
                    />
                    <span v-if="!filtered.length" class="empty">
                        {{ t("dashboards.empty") }}
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

    import {getDashboard} from "../../composables/useDashboards";

    import Item from "./Item.vue";

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm");

    import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    const emits = defineEmits(["dashboard"]);

    const query = computed(() => {
        return {
            name: ["flows/update", "namespaces/update"].includes(route.name as string) ? route.name : "home",
            params: JSON.stringify({...route.params, dashboard: undefined}),
        };
    });

    const search = ref("");
    const dashboards = ref<{ id: string; title: string }[]>([]);
    const filtered = computed(() => {
        const DEFAULT = {id: "default", title: t("dashboards.default")};

        return [DEFAULT, ...dashboards.value].filter((d) => !search.value || d.title.toLowerCase().includes(search.value.toLowerCase()));
    });

    const STORAGE_KEY = getDashboard(route, "key");
    
    const selected = ref<string | null>(null);
    const select = (dashboard: any) => {
        selected.value = dashboard?.title;

        if (STORAGE_KEY) {
            if (dashboard?.id) {
                localStorage.setItem(STORAGE_KEY, dashboard.id);
            } else {
                localStorage.removeItem(STORAGE_KEY);
            }
        }

        emits("dashboard", dashboard.id);
    };

    const edit = (id: string) => {
        router.push({name: "dashboards/update", params: {dashboard: id}});
    };

    const remove = (dashboard: any) => {
        toast.confirm(t("dashboards.deletion.confirmation", {title: dashboard.title}), () => {
            return dashboardStore.delete(dashboard.id).then(() => {
                dashboards.value = dashboards.value.filter((d) => d.id !== dashboard.id);
                toast.deleted(dashboard.title);
            });
        });
    };

    const getStoredDashboard = () => STORAGE_KEY ? localStorage.getItem(STORAGE_KEY) : null;
    const fetchDashboards = () => {
        dashboardStore
            .list({})
            .then((response: { results: { id: string; title: string }[] }) => {
                dashboards.value = response.results;

                const creation = Boolean(route.query.created);
                const lastSelected = creation 
                    ? (route.params?.dashboard ?? getStoredDashboard()) 
                    : (getStoredDashboard() ?? route.params?.dashboard);

                if (lastSelected) {
                    const dashboard = dashboards.value.find((d) => d.id === lastSelected);

                    if (dashboard) {
                        selected.value = dashboard.title;
                        emits("dashboard", dashboard.id);
                    } else {
                        selected.value = null;
                        emits("dashboard", "default");
                    }
                }
            });
    };

    onBeforeMount(() => fetchDashboards());

    const tenant = ref();
    watch(() => route.params.tenant, (t) => {
        if (tenant.value !== t) {
            fetchDashboards();
            tenant.value = t;
        }
    }, {immediate: true});

    watch(() => route.params?.dashboard, (val) => {
        if(route.name === "home" && STORAGE_KEY) {
            localStorage.setItem(STORAGE_KEY, val as string);
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
