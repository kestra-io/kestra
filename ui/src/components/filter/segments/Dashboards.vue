<template>
    <el-dropdown trigger="click" placement="bottom-end">
        <KestraIcon placement="bottom">
            <el-button :icon="Menu" class="main-button d-inline">
                <span class="text-truncate">{{ selectedDashboard ?? $t('default_dashboard') }}</span>
            </el-button>
        </KestraIcon>

        <template #dropdown>
            <el-dropdown-menu class="p-4 dropdown">
                <el-button
                    type="primary"
                    :icon="Plus"
                    tag="router-link"
                    :to="{name: 'dashboards/create'}"
                    class="w-100"
                >
                    <small>{{ t("create_custom_dashboard") }}</small>
                </el-button>

                <el-input
                    v-if="filtered.length >= 10"
                    v-model="search"
                    :placeholder="$t('search')"
                    :prefix-icon="Magnify"
                    clearable
                    class="my-3"
                />

                <el-dropdown-item
                    @click="selectDashboard(null)"
                    :class="{'mt-3': filtered.length < 10}"
                >
                    <small>{{ t("default_dashboard") }}</small>
                </el-dropdown-item>

                <hr class="my-2">

                <div class="overflow-x-auto scroller items">
                    <el-dropdown-item
                        v-for="(dashboard, index) in filtered"
                        :key="index"
                        @click="selectDashboard(dashboard)"
                    >
                        <div class="d-flex align-items-center w-100">
                            <div class="col text-truncate">
                                <small>{{ dashboard.title }}</small>
                            </div>

                            <div class="col-auto mt-1">
                                <Pencil
                                    @click.stop="editDashboard(dashboard)"
                                    class="mx-2"
                                />
                                <DeleteOutline
                                    @click.stop="remove(dashboard)"
                                />
                            </div>
                        </div>
                    </el-dropdown-item>
                    <span
                        v-if="!filtered.length"
                        class="px-3 text-center empty"
                    >
                        {{ t("custom_dashboard_empty") }}
                    </span>
                </div>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {onBeforeMount, ref, computed, getCurrentInstance} from "vue";
    import KestraIcon from "../../Kicon.vue";
    import {Menu, Plus, DeleteOutline, Magnify, Pencil} from "../utils/icons";
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";
    import {useRouter, useRoute} from "vue-router";

    const {t} = useI18n({useScope: "global"});
    const store = useStore();
    const route = useRoute();
    const router = useRouter();
    const emits = defineEmits(["dashboard"]);
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();

    const remove = (dashboard: any) => {
        toast.confirm(t("delete confirm", {name: dashboard.title}), () => {
            store.dispatch("dashboard/delete", dashboard.id).then((item) => {
                dashboards.value = dashboards.value.filter((d) => d.id !== dashboard.id);
                toast.deleted(item.title);
                router.push({name: "home"});
            });
        });
    };

    const search = ref("");
    const dashboards = ref<{ id: string; title: string }[]>([]);
    const filtered = computed(() => {
        return dashboards.value.filter(
            (d) =>
                !search.value ||
                d.title.toLowerCase().includes(search.value.toLowerCase()),
        );
    });

    const selectedDashboard = ref(null)

    const selectDashboard = (dashboard: any) => {
        selectedDashboard.value = dashboard?.title;
        emits("dashboard", dashboard)
    }

    const editDashboard = (dashboard: any) => {
        router.push({name: "dashboards/update", params: {id: dashboard.id}});
    }

    onBeforeMount(() => {
        store
            .dispatch("dashboard/list", {})
            .then((response: { results: { id: string; title: string }[] }) => {
                dashboards.value = response.results;
                if (route.params?.id) {
                    const dashboard = dashboards.value.find(d => d.id === route.params.id);
                    if (dashboard) {
                        selectedDashboard.value = dashboard.title;
                    }
                }
            });
    });
</script>

<style scoped lang="scss">
@import "../styles/filter";

.dropdown {
    width: 300px;
}

.items {
    max-height: 160px !important; // 5 visible items
}

.main-button {
    max-width: 300px;

    span {
        max-width: 250px;
    }
}
</style>