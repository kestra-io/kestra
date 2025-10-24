<template>
    <el-table
        tableLayout="auto"
        fixed
        :data="Object.entries(data).map(([key, value]) => ({key, value}))"
    >
        <el-table-column prop="key" rowspan="3" :label="labelName">
            <template #default="scope">
                {{ getHumanizeLabel(scope.row.key) }}
            </template>
        </el-table-column>


        <el-table-column prop="value" :label="labelValue">
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
                        {{ tOr('triggerVars.copyUrl', 'Copy URL') }}
                    </el-button>
                </template>
                <template v-else>
                    <VarValue
                        :value="scope.row.value"
                        :execution="execution"
                        :restrictUri="true"
                    />
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import VarValue from "../executions/VarValue.vue";
    import Markdown from "../layout/Markdown.vue";
    import Cron from "../layout/Cron.vue";


    const {t, te} = useI18n();
    defineProps<{
        data: Record<string, any>;
        execution?: Record<string, any>;
    }>();

    const emit = defineEmits<{ (e: "on-copy", event: any): void }>();
    /**
     * Translate if the key exists, otherwise fall back to a readable label.
     * This prevents [intlify] "Not found 'xxx' key in 'en' locale messages" warnings.
     */

    const humanize = (s: string) =>
        s.replace(/([A-Z])/g, " $1").replace(/[_-]/g, " ").trim();

    const tOr = (key: string, fallback?: string): string =>
        te(key) ? t(key) : (fallback ?? humanize(key.split(".").pop() || key));


    const getHumanizeLabel = (key: string): string => {
        const mappings: Record<string, string> = {
            flowId: "triggerVars.flow",
            executionId: "triggerVars.currentExecution",
            nextExecutionDate: "triggerVars.nextEvaluationDate",
            date: "triggerVars.lastTriggerDate",
            updatedDate: "triggerVars.contextUpdatedDate",
            evaluateRunningDate: "triggerVars.evaluationLockDate",
            states: "triggerVars.states",
        };
        const translationKey = mappings[key] ?? `triggerVars.${key}`;
        return tOr(translationKey, humanize(key));
    };

    const labelName = tOr("triggerVars.name", "Name");
    const labelValue = tOr("triggerVars.value", "Value");
</script>

<style scoped lang="scss">
:deep(.markdown) {
  p {
    margin-bottom: auto;
  }
}

:deep(.el-table__cell:nth-child(2) span) {
  color: var(--ks-content-secondary);
}
</style>
