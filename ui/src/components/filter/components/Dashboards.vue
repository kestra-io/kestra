<template>
    <el-dropdown trigger="click" placement="bottom-end">
        <el-button :icon="ViewDashboardEdit" class="ms-2" />

        <template #dropdown>
            <el-dropdown-menu class="p-4 dashboard-dropdown">
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
                    @click="emits('dashboard')"
                    :class="{'mt-3': filtered.length < 10}"
                >
                    <small>{{ t("default_dashboard") }}</small>
                </el-dropdown-item>

                <hr class="my-2">

                <div class="overflow-x-auto saved scroller">
                    <el-dropdown-item
                        v-for="(dashboard, index) in filtered"
                        :key="index"
                        @click="emits('dashboard', dashboard)"
                    >
                        <div class="d-flex align-items-center w-100">
                            <div class="col text-truncate">
                                <small>{{ dashboard.title }}</small>
                            </div>

                            <div class="col-auto">
                                <DeleteOutline
                                    @click.stop="remove(dashboard.id)"
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
    import {onBeforeMount, ref, computed} from "vue";

    import ViewDashboardEdit from "vue-material-design-icons/ViewDashboardEdit.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    const emits = defineEmits(["dashboard"]);

    const remove = (id) => {
        store.dispatch("dashboard/delete", id).then(() => {
            dashboards.value = dashboards.value.filter((d) => d.id !== id);
        });
    };

    const search = ref("");
    const dashboards = ref([]);
    const filtered = computed(() => {
        return dashboards.value.filter(
            (d) =>
                !search.value ||
                d.title.toLowerCase().includes(search.value.toLowerCase()),
        );
    });
    onBeforeMount(() => {
        store.dispatch("dashboard/list", {}).then((response) => {
            dashboards.value = response.results;
        });
    });
</script>

<style lang="scss">
.dashboard-dropdown {
    width: 300px;
}

.empty {
    color: var(--bs-gray-900);
}

.saved {
    max-height: 160px !important; // 5 visible items
}

.scroller {
    &::-webkit-scrollbar {
        height: 5px;
        width: 5px;
    }

    &::-webkit-scrollbar-track {
        background: var(--card-bg);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--bs-primary);
        border-radius: 0px;
    }
}
</style>
