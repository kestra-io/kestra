<template>
    <el-table
        :data="this.flow.triggers"
        stripe
        table-layout="auto"
        @row-dblclick="goToDetail"
    >
        <el-table-column prop="id" :label="$t('id')">
            <template #default="scope">
                <router-link
                    :to="{name: 'flows/triggers/detail', params: {namespace: flow.namespace, flowId: flow.id, id: scope.row.id}}"
                >
                    {{ scope.row.id }}
                </router-link>
            </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('type')" />
        <el-table-column :label="$t('description')">
            <template #default="scope">
                <Markdown :source="scope.row.description" />
            </template>
        </el-table-column>
    </el-table>
</template>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters} from "vuex";

    export default {
        components: {Markdown},
        methods: {
            goToDetail(row) {
                this.$router.push({
                    name: "flows/triggers/detail",
                    params: {
                        flowId: this.flow.id,
                        namespace: this.flow.namespace,
                        id: row.id
                    },
                });
            }
        },
        computed: {
            ...mapGetters("flow", ["flow"])
        }
    };
</script>