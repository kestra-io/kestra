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

    const emits = defineEmits(["dashboard"]);

    const dashboards = ref([]);
    onBeforeMount(() => {
        // TODO: Fetch proper listing of dashboards
        dashboards.value = [
            {
                title: "Sales Overview",
                id: "3b80afe1-5bac-49b2-9e6d-8e25050f7d11",
            },
            {
                title: "Customer Insights",
                id: "c103ef09-46c2-4ef0-bbec-d4e2c1ae2cc0",
            },
            {
                title: "Team Performance",
                id: "725bd7b5-1136-4889-9494-c22a9a9e3407",
            },
            {
                title: "Revenue Tracker",
                id: "fdc03818-285b-4bed-baa5-ae7590629723",
            },
            {
                title: "Project Summary",
                id: "fbd080ca-d15c-45cf-90d4-de6c9dc95d5f",
            },
            {
                title: "Marketing Analytics",
                id: "c03d1487-ab2d-4081-9380-de80fd5d4d2c",
            },
            {
                title: "Task Management",
                id: "365b35b6-6e82-4fc7-b1ce-53caf12c2868",
            },
            {
                title: "Support Metrics",
                id: "4ebbd60b-5d97-4751-a4e0-5fe2a75a59c5",
            },
        ];
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
