<template>
    <el-dropdown-item
        :icon="ChartAreaspline"
        @click="onClick"
    >
        {{ $t('metrics') }}
    </el-dropdown-item>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('metrics')"
    >
        <metrics-table ref="table" :task-run-id="taskRun.id" :execution="execution" />
    </drawer>
</template>

<script setup>
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue";

</script>

<script>
    import MetricsTable from "./MetricsTable.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {
            MetricsTable,
            Drawer
        },
        data() {
            return {
                isOpen: false,
            };
        },
        props: {
            embed: {
                type: Boolean,
                default: true
            },
            taskRun: {
                type: Object,
                required: true
            },
            execution: {
                type: Object,
                required: true
            }
        },
        methods: {
            onClick() {
                this.isOpen = !this.isOpen;
                this.$nextTick(() => {
                    this.$refs.table.loadData(this.$refs.table.onDataLoaded);
                });
            },
        },
    };
</script>
