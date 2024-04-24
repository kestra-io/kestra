<template>
    <el-table stripe table-layout="auto" fixed :data="variables">
        <el-table-column prop="key" rowspan="3" :label="$t('name')">
            <template #default="scope">
                <code>{{ scope.row.key }}</code>
            </template>
        </el-table-column>

        <el-table-column prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.key === 'description'">
                    <markdown :source="scope.row.value" />
                </template>
                <template v-else>
                    <var-value :value="scope.row.value" :execution="execution" />
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>

<script>
    import Utils from "../../utils/utils";
    import VarValue from "../executions/VarValue.vue";
    import Markdown from "../layout/Markdown.vue";

    export default {
        components: {
            VarValue,
            Markdown
        },
        props: {
            data: {
                type: Object,
                required: true
            },
            execution: {
                type: Object,
                required: false,
                default: undefined
            }
        },
        computed: {
            variables() {
                return Utils.executionVars(this.data);
            },
        },
    };
</script>

<style lang="scss" scoped>
    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }
</style>