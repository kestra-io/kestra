<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container">
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
            <el-table-column prop="workerUuid" sortable :sort-orders="['ascending', 'descending']" :label="$t('id')" />
            <el-table-column prop="hostname" sortable :sort-orders="['ascending', 'descending']" :label="$t('hostname')" />
            <el-table-column prop="workerGroup" sortable :sort-orders="['ascending', 'descending']" :label="$t('worker group')" />
            <el-table-column prop="status" sortable :sort-orders="['ascending', 'descending']" :label="$t('state')" />
            <el-table-column prop="heartbeatDate" sortable :sort-orders="['ascending', 'descending']" :label="$t('date')">
                <template #default="scope">
                    <date-ago class-name="text-muted small" :inverted="true" :date="scope.row.heartbeatDate" />
                </template>
            </el-table-column>
        </el-table>
    </section>
</template>
<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RefreshButton from "../../components/layout/RefreshButton.vue";
    import Collapse from "../../components/layout/Collapse.vue";
    import DateAgo from "../layout/DateAgo.vue";

    export default {
        mixins: [RouteContext],
        components: {DateAgo, RefreshButton, Collapse, TopNavBar},
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