<template>
    <el-card :header="$t('triggers')">
        <el-table :data="triggers" stripe table-layout="auto">
            <el-table-column v-for="prop in columns" :key="prop" :prop="prop" :label="getLabel(prop)" />
            <el-table-column :label="$t('description')">
                <template #default="scope">
                    <Markdown :source="scope.row.description" />
                </template>
            </el-table-column>
        </el-table>
    </el-card>
</template>

<script>
    import Markdown from "../layout/Markdown.vue";

    export default {
        components: {Markdown},
        data() {
            return {
                columns: [
                    "id",
                    "type",
                    "cron",
                    "key",
                ],
            }
        },
        props: {
            triggers: {
                type: Array,
                default: undefined,
            },
        },
        methods: {
            getLabel(prop) {
                return this.$t(prop);
            },
        }
    };
</script>
<style lang="scss">
    .el-card {
        margin-top: var(--spacer);
    }
</style>