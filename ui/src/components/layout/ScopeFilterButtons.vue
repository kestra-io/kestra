<template>
    <el-select
        :model-value="value"
        @update:model-value="onInput"
        collapse-tags
        multiple
        :placeholder="$t('scope_filter.all', {label})"
    >
        <el-option
            v-for="item in options"
            :key="item.key"
            :label="item.name"
            :value="item.key"
        />
    </el-select>
</template>
<script>
    export default {
        props: {
            value: {type: Array, default: () => []},
            label: {type: String, required: true},
        },
        emits: ["update:modelValue"],
        data() {
            return {
                options: [
                    {
                        name: this.$t("scope_filter.user", {label: this.label}),
                        key: "USER",
                    },
                    {
                        name: this.$t("scope_filter.system", {label: this.label}),
                        key: "SYSTEM",
                    },
                ],
            };
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", value);
            },
        },
    };
</script>
