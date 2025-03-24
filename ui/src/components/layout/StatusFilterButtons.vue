<template>
    <el-select
        data-component="FILENAME_PLACEHOLDER"
        :model-value="value"
        @update:model-value="onInput"
        filterable
        clearable
        multiple
        collapse-tags
        :persistent="false"
        :placeholder="$t('state')"
    >
        <el-option
            v-for="item in statuses"
            :key="item.key"
            :label="item.name"
            :value="item.key"
        >
            <status :status="item.key" size="small" />
        </el-option>
    </el-select>
</template>
<script>
    import {State} from "@kestra-io/ui-libs"
    import Status from "../Status.vue";

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
