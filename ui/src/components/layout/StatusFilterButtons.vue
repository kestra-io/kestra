<template>
    <el-select
        :modelValue="value"
        @update:model-value="onInput"
        filterable
        clearable
        multiple
        collapseTags
        :persistent="false"
        :placeholder="$t('state')"
    >
        <el-option
            v-for="item in statuses"
            :key="item.key"
            :label="item.name"
            :value="item.key"
        >
            <Status :status="item.key" size="small" />
        </el-option>
    </el-select>
</template>
<script>
    import {State, Status} from "@kestra-io/ui-libs"

    export default {
        components: {Status},
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
