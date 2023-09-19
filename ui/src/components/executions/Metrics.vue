<template>
    <el-dropdown-item
        :icon="ChartAreaspline"
        @click="onClick"
    >
        {{ $t('metrics') }}
    </el-dropdown-item>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('metrics')"
        destroy-on-close
        :append-to-body="true"
        size=""
        direction="ltr"
    >
        <metrics-table ref="table" :task-run-id="taskRun.id" :execution="execution" />
    </el-drawer>
</template>

<script setup>
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline.vue";

</script>

<script>
    import MetricsTable from "./MetricsTable.vue";

    export default {
        components: {
            MetricsTable
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
