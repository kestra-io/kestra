<template>
    <KsNoData v-if="!variables.length" />

    <div v-else class="vars">
        <div class="vars-row vars-head">
            <KsText size="small">{{ $t(keyLabelTranslationKey) }}</KsText>
            <KsText size="small">{{ $t('value') }}</KsText>
        </div>

        <DynamicScroller
            :items="variables"
            :minItemSize="40"
            keyField="key"
            :buffer="200"
            :prerender="20"
            class="vars-rows"
        >
            <template #default="{item, index, active}">
                <DynamicScrollerItem
                    :item="item"
                    :active="active"
                    :sizeDependencies="[item.value]"
                    :dataIndex="index"
                >
                    <div class="vars-row">
                        <code class="vars-key">{{ item.key }}</code>

                        <div>
                            <KsDateAgo v-if="item.date" :inverted="true" :date="item.value" />
                            <template v-else-if="item.subflow">
                                {{ item.value }}
                                <SubFlowLink :executionId="item.value" />
                            </template>
                            <VarValue v-else :execution="executionsStore.execution" :value="item.value" />
                        </div>
                    </div>
                </DynamicScrollerItem>
            </template>
        </DynamicScroller>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"

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

<style scoped lang="scss">
.vars {
    display: flex;
    flex-direction: column;
    min-height: 0;
}

.vars-rows {
    /* Bounds the scroll window so only the visible rows are built; a short list stays its own
       height, since the total is below the max (kestra-io/kestra#19316). */
    max-height: 60vh;
}

.vars-row {
    display: grid;
    grid-template-columns: minmax(10rem, 15rem) 1fr;
    gap: var(--ks-spacing-4);
    align-items: start;
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.vars-head {
    color: var(--ks-text-secondary);
    border-bottom: 1px solid var(--ks-border-default);
}

.vars-key {
    color: var(--ks-text-primary);
    overflow-wrap: anywhere;
}
</style>
