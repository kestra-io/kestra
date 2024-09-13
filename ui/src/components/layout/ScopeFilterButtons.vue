<template>
    <el-select
        v-model="scope"
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
            label: {type: String, required: true},
            system: {type: Boolean, default: false},
        },
        emits: ["update:modelValue"],
        data() {
            return {
                scope: [],
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
        created() {
            const QUERY = this.$route.query.scope;
            this.scope = this.system
                ? ["SYSTEM"]
                : QUERY
                    ? [].concat(QUERY)
                    : ["USER"];
        },
    };
</script>
