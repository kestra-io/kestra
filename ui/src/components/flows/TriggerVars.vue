<template>
    <el-table stripe table-layout="auto" fixed :data="Object.entries(data).map(([key, value]) => ({key, value}))">
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
                <template v-else-if="scope.row.key === 'cron'">
                    <cron :cron-expression="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'key'">
                    {{ scope.row.value }}
                    <el-button @click="emit('on-copy', null)">
                        {{ $t('copy url') }}
                    </el-button>
                </template>
                <template v-else>
                    <var-value :value="scope.row.value" :execution="execution" />
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>

<script>
    import VarValue from "../executions/VarValue.vue";
    import Markdown from "../layout/Markdown.vue";
    import Cron from "../layout/Cron.vue";

    export default {
        emits: ["on-copy"],
        components: {
            VarValue,
            Markdown,
            Cron
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
        methods: {
            emit(type, event) {
                this.$emit(type, event);
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }
</style>