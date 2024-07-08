<template>
    <el-button data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" @click="$emit('click', $event)" class="status" :icon="icon" :size="size" :class="cls">
        <template v-if="label">
            {{ title || $filters.cap($filters.lower(status)) }}
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
            cls() {
                const bg = "status-" + State.colorClass()[this.status];
                return {
                    "no-label": !this.label,
                    [bg]: true,
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

        &.no-label {
            padding: 8px;
            line-height: 1;
        }

        &:not(.no-label) {
            border-radius: var(--bs-border-radius-pill);
        }
    }
</style>
