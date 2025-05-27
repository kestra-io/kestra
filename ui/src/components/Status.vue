<template>
    <el-button data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" @click="$emit('click', $event)" class="status" :size="size" :style="style">
        {{ title || $filters.cap(status) }}
    </el-button>
</template>

<script>
    export default {
        props: {
            status: {
                type: String,
                required: true,
                default: undefined
            },
            size: {
                type: String,
                default: ""
            },
            title: {
                type: String,
                default: ""
            },
            label: {
                type: Boolean,
                default: true
            },
        },
        emits: ["click"],
        computed: {
            style() {
                const statusVarname = this.status?.toLowerCase()
                return {
                    color: `var(--ks-content-${statusVarname}) !important`,
                    "border-color": `var(--ks-border-${statusVarname}) !important`,
                    "background-color": `var(--ks-background-${statusVarname}) !important`
                };
            },
        }
    };
</script>
<style scoped lang="scss">
    .el-button {
        white-space: nowrap;
        border-radius: var(--el-border-radius-base);
        width: 7rem;
        cursor: default;

        &.no-label {
            padding: 0.5rem;
            line-height: 1;
        }
    }
</style>
