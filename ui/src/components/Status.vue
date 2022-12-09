<template>
    <el-button @click="$emit('click', $event)" class="status" :icon="icon" :size="this.size" :type="this.type" :class="cls">
        <template v-if="label">
            {{ $filters.cap($filters.lower(status)) }}
        </template>
    </el-button>
</template>

<script>
    import State from "../utils/state";

    export default {
        components: {

        },
        props: {
            status: {
                type: String,
                required: true
            },
            size: {
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
                return {
                    "no-label": !this.label
                }
            },
            icon () {
                return State.icon()[this.status];
            },
            type () {
                return State.type()[this.status];
            }
        }
    };
</script>
<style scoped lang="scss">
    .el-button {
        white-space: nowrap;

        &.no-label {
            padding: 8px;
            line-height: 1;
            :deep([class*=el-icon] + span) {
                margin-left: 0;
            }
        }
    }
</style>
