<template>
    <div>
        <nav>
            <collapse>
                <el-form-item>
                    <refresh-button class="float-right" @refresh="loadData" />
                </el-form-item>
            </collapse>
        </nav>
        <el-table
            :data="workers"
            ref="table"
            :default-sort="{prop: 'hostname', order: 'ascending'}"
            stripe
            table-layout="auto"
            fixed
        >
            <el-table-column prop="workerUuid" sortable :sort-orders="['ascending', 'descending']"
                             :label="$t('id')" />
            <el-table-column prop="hostname" sortable :sort-orders="['ascending', 'descending']"
                             :label="$t('hostname')" />
            <el-table-column prop="port"
                             :label="$t('port')" />
            <el-table-column prop="managementPort"
                             :label="$t('management port')" />
            <el-table-column prop="workerGroup" sortable :sort-orders="['ascending', 'descending']"
                             :label="$t('worker group')" />
        </el-table>
    </div>
</template>
<script>
    import RouteContext from "../../mixins/routeContext";
    import RefreshButton from "../../components/layout/RefreshButton.vue";
    import Collapse from "../../components/layout/Collapse.vue";

    export default {
        mixins: [RouteContext],
        components: {RefreshButton, Collapse},
        data() {
            return {
                workers: undefined,
            };
        },
        created() {
            this.loadData();
        },
        methods: {
            loadData() {
                this.$store.dispatch("worker/findAll").then(workers => {
                    console.log(workers)
                    this.workers = workers;
                });
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("workers")
                }
            }
        }
    };
</script>