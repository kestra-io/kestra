<template>
    <el-table
        tableLayout="auto"
        fixed
        :data="Object.entries(data).map(([key, value]) => ({ key, value }))"
    >
        <el-table-column prop="key" rowspan="3" :label="$t('triggerVars.name')">
            <template #default="scope">
                {{ getHumanizeLabel(scope.row.key) }}
            </template>
        </el-table-column>

        <el-table-column prop="value" :label="$t('triggerVars.value')">
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
                        {{ $t('triggerVars.copyUrl') }}
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
import { useI18n } from "vue-i18n";
import VarValue from "../executions/VarValue.vue";
import Markdown from "../layout/Markdown.vue";
import Cron from "../layout/Cron.vue";

const { t } = useI18n();

defineProps<{
  data: Record<string, any>;
  execution?: Record<string, any>;
}>();

const emit = defineEmits<{ (e: "on-copy", event: any): void }>();

const getHumanizeLabel = (key: string): string => {
  const mappings: Record<string, string> = {
    flowId: "triggerVars.flow",
    executionId: "triggerVars.currentExecution",
    nextExecutionDate: "triggerVars.nextEvaluationDate",
    date: "triggerVars.lastTriggerDate",
    updatedDate: "triggerVars.contextUpdatedDate",
    evaluateRunningDate: "triggerVars.evaluationLockDate",
  };
  const translationKey = mappings[key] ?? `triggerVars.${key}`;
  return t(translationKey);
};
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
