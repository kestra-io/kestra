<template>
    <el-button data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" @click="$emit('click', $event)" class="status" :size="size" :style="style">
        <template v-if="label">
            {{ title || $filters.cap(status) }}
        </template>
    </el-button>
</template>

<script>
    import State from "../utils/state";

    export default {
        props: {
            status: {
                type: String,
                required: true
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
                return {
                    color: `var(--content-color-${this.status.toLowerCase()}) !important`,
                    "border-color": `var(--border-color-${this.status.toLowerCase()}) !important`,
                    "background-color": `var(--background-color-${this.status.toLowerCase()}) !important`
                }
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
