<template>
    <KsTable tableLayout="auto" fixed :data="Object.entries(data).map(([key, value]) => ({key, value}))">
        <KsTableColumn prop="key" rowspan="3" :label="$t('name')">
            <template #default="scope">
                {{ getHumanizeLabel(scope.row.key) }}
            </template>
        </KsTableColumn>

        <KsTableColumn prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.key === 'description'">
                    <KsMarkdown :content="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'cron'">
                    <Cron :cronExpression="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'key'">
                    {{ scope.row.value }}
                    <KsButton @click="emit('on-copy', null)">
                        {{ $t('copy url') }}
                    </KsButton>
                </template>
                <template v-else>
                    <VarValue :value="scope.row.value" :execution="execution" :restrictUri="true" />
                </template>
            </template>
        </KsTableColumn>
    </KsTable>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import VarValue from "../executions/VarValue.vue"
    import {KsMarkdown} from "@kestra-io/design-system"
    import Cron from "../layout/Cron.vue"
    import {Execution} from "../../stores/executions"

    const {t, te} = useI18n()

    defineProps<{
        data: Record<string, any>;
        execution?: Execution;
    }>()

    const emit = defineEmits<{ (e: "on-copy", event: any): void }>()

    const getHumanizeLabel = (key: string): string => {
        const mappings: Record<string, string> = {
            "flowId": "flow",
            "nextEvaluationDate": "next evaluation date",
            "lastTriggeredDate": "last trigger date",
            "updatedAt": "state updated date",
            "evaluatedAt": "last evaluation date",
            "locked": "locked",
            "states": "trigger_states",
        }
        const translationKey = mappings[key] ?? key
        return te(translationKey) && t(translationKey) || translationKey
    }
</script>

<style scoped lang="scss">
    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }

    :deep(.kel-table__cell:nth-child(2) span) {
        color: var(--ks-text-secondary);
    }
</style>