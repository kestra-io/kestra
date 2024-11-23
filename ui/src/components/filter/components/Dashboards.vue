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

                <el-dropdown-item @click="emits('dashboard')" class="mt-3">
                    {{ t("default_dashboard") }}
                </el-dropdown-item>

                <hr class="my-2">

                <div class="overflow-x-auto saved scroller">
                    <el-dropdown-item
                        v-for="(dashboard, index) in dashboards"
                        :key="index"
                        @click="emits('dashboard', dashboard)"
                    >
                        {{ dashboard.title }}
                    </el-dropdown-item>
                </div>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {onBeforeMount, ref} from "vue";

    import ViewDashboardEdit from "vue-material-design-icons/ViewDashboardEdit.vue";
    import Plus from "vue-material-design-icons/Plus.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    const emits = defineEmits(["dashboard"]);

    const dashboards = ref([]);
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
