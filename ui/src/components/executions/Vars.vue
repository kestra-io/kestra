<template>
    <KsTable tableLayout="auto" fixed :data="variables">
        <KsTableColumn prop="key" minWidth="500" :label="$t(keyLabelTranslationKey)">
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
    </KsTable>
</template>

<script setup lang="ts">
    import {computed} from "vue"
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

    const variables = computed<VariableRow[]>(() => {
        return Utils.executionVars(props.data)
    })

</script>
<style>
    .key-col {
        min-width: 200px;
    }
</style>
