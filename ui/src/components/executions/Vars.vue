<template>
    <KsDataTable
        tableLayout="auto"
        :data="pageOfVariables"
        :total="variables.length"
        v-model:currentPage="currentPage"
        v-model:pageSize="pageSize"
        :pageSizeOptions="PAGE_SIZE_OPTIONS"
        rowKey="key"
    >
        <KsTableColumn prop="key" width="240" :label="$t(keyLabelTranslationKey)">
            <template #default="scope">
                <code class="key-col">{{ scope.row.key }}</code>
            </template>
        </KsTableColumn>

        <KsTableColumn prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.date">
                    <KsDateAgo :inverted="true" :date="scope.row.value" />
                </template>
                <template v-else-if="scope.row.subflow">
                    {{ scope.row.value }}
                    <SubFlowLink :executionId="scope.row.value" />
                </template>
                <template v-else>
                    <VarValue :execution="executionsStore.execution" :value="scope.row.value" />
                </template>
            </template>
        </KsTableColumn>
    </KsDataTable>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import * as Utils from "../../utils/utils"
    import VarValue from "./VarValue.vue"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import {useExecutionsStore} from "../../stores/executions"


    interface VariableRow {
        key: string;
        value: any;
        date?: boolean;
        subflow?: boolean;
    }

    // A task emitting thousands of output values used to render a row each, and the
    // unvirtualized table froze the tab for seconds (kestra-io/kestra#19316).
    const PAGE_SIZE_OPTIONS = [100, 250, 500]

    const props = withDefaults(
        defineProps<{
            data: Record<string, any>;
            keyLabelTranslationKey?: string;
        }>(),
        {
            keyLabelTranslationKey: "name",
        },
    )

    const executionsStore = useExecutionsStore()

    const currentPage = ref(1)
    const pageSize = ref(PAGE_SIZE_OPTIONS[0])

    const variables = computed<VariableRow[]>(() => {
        return Utils.executionVars(props.data)
    })

    const pageOfVariables = computed<VariableRow[]>(() => {
        const start = (currentPage.value - 1) * pageSize.value
        return variables.value.slice(start, start + pageSize.value)
    })

    watch(() => props.data, () => {
        currentPage.value = 1
    })
</script>
<style scoped>
    .key-col {
        min-width: 200px;
    }
</style>
