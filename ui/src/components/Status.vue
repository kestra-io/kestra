<template>
    <div data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" @click="$emit('click', $event)" class="status" :size="size" :class="cls" :style="style">
        <template v-if="label">
            {{ title || $filters.cap(status) }}
        </template>
    </div>
</template>

<script>
    import State from "../utils/state";
    import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

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
                    color: cssVariable(`--content-color-${this.status.toLowerCase()}`),
                    "border-color": cssVariable(`--border-color-${this.status.toLowerCase()}`),
                    "background-color": cssVariable(`--background-color-${this.status.toLowerCase()}`)
                }
            },
            icon() {
                return State.icon()[this.status];
            },
        }
    };
</script>
<style scoped lang="scss">
    .status {
        display: flex;
        justify-content: center;
        border: 1px solid;
        border-radius: 4px;
        width: 6rem;
    }
</style>
