<template>
    <ks-select
        :modelValue="value"
        @update:model-value="onInput"
        filterable
        clearable
        multiple
        collapseTags
        :placeholder="$t('state')"
    >
        <ks-option
            v-for="item in statuses"
            :key="item.key"
            :label="item.name"
            :value="item.key"
        >
            <KsExecutionStatus :status="item.key" size="small" />
        </ks-option>
    </ks-select>
</template>
<script>
    import {State} from "@kestra-io/ui-design-system"
    import {KsExecutionStatus} from "@kestra-io/ui-design-system"

    export default {
        components: {KsExecutionStatus},
        props: {
            value: {
                type: Array,
                default: undefined
            }
        },
        emits: ["update:modelValue"],
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", value)
            },
        },
        computed: {
            statuses() {
                return State.allStates();
            }
        }
    };
</script>
