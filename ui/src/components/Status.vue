<template>
    <el-button data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" @click="$emit('click', $event)" class="status" :size="size" :style="style">
        <template v-if="label">
            {{ title || $filters.cap(status) }}
        </template>
    </el-button>
</template>

<script>
    import {State} from "@kestra-io/ui-libs"

    const StatusRemap = {
        "failed": "error",
        "warn": "warning"
    }

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
                const statusVarname = (StatusRemap[this.status.toLowerCase()] ?? this.status)?.toLowerCase()
                return {
                    color: `var(--ks-content-${statusVarname}) !important`,
                    "border-color": `var(--ks-border-${statusVarname}) !important`,
                    "background-color": `var(--ks-background-${statusVarname}) !important`
                };
            },
            icon() {
                return State.icon()[this.status];
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
