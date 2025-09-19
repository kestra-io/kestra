<template>
    <el-table tableLayout="auto" fixed :data="Object.entries(data).map(([key, value]) => ({key, value}))">
        <el-table-column prop="key" rowspan="3" :label="$t('name')">
            <template #default="scope">
                {{ getHumanizeLabel(scope.row.key) }}
            </template>
        </el-table-column>

        <el-table-column prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.key === 'description'">
                    <Markdown :source="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'cron'">
                    <Cron :cronExpression="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'key'">
                    {{ scope.row.value }}
                    <el-button @click="emit('on-copy', null)">
                        {{ $t('copy url') }}
                    </el-button>
                </template>
                <template v-else>
                    <VarValue :value="scope.row.value" :execution="execution" :restrictUri="true" />
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
            },
            getHumanizeLabel(key) {
                const keyMappings = {
                    "id": this.$t("id"),
                    "triggerId": this.$t("triggerId"), 
                    "flowId": this.$t("flow"),
                    "namespace": this.$t("namespace"),
                    "type": this.$t("type"),
                    "workerId": this.$t("workerId"),
                    "executionId": this.$t("current execution"),
                    "nextExecutionDate": this.$t("next evaluation date"),
                    "date": this.$t("last trigger date"),
                    "updatedDate": this.$t("context updated date"),
                    "evaluateRunningDate": this.$t("evaluation lock date"),
                    "description": this.$t("description"),
                    "cron": this.$t("cron"),
                    "key": this.$t("key"),
                    "backfill": this.$t("backfill"),
                    "state": this.$t("state"),
                    "enabled": this.$t("enabled"),
                    "codeDisabled": this.$t("codeDisabled"),
                    "paused": this.$t("paused"),
                    "tenantId": this.$t("tenantId"),
                    "conditions": this.$t("conditions"),
                };
                return keyMappings[key] || key.charAt(0).toUpperCase() + key.slice(1);
            },
        }
    };
</script>

<style lang="scss" scoped>
    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }

    :deep(.el-table__cell:nth-child(2) span) {
        color: var(--ks-content-secondary);
    }
</style>