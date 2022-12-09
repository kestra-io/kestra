<template>
    <el-tooltip v-if="hasTooltip" transition="" :hide-after="0" :persistent="false" placement="top">
        <template #content><code>{{ value }}</code></template>
        <code :id="uuid" class="text-nowrap">
            {{ transformValue }}
        </code>
    </el-tooltip>
    <code v-else :id="uuid" class="text-nowrap">
        {{ transformValue }}
    </code>
</template>

<script>
    import Utils from "../utils/utils";

    export default {
        components: {

        },
        props: {
            value: {
                type: String,
                default: undefined
            },
            shrink: {
                type: Boolean,
                default: true
            },
            size: {
                type: Number,
                default: 8
            },
        },
        data() {
            return {
                uuid: Utils.uid(),
            };
        },
        computed: {
            hasTooltip() {
                return this.shrink && this.value && this.value.length > this.size;
            },
            transformValue() {
                if (!this.value) {
                    return "";
                }

                if (!this.shrink) {
                    return this.value;
                }

                return this.value.toString().substr(0, this.size) +
                    (this.value.length > this.size && this.size !== 8 ? "…" : "");
            },
        }
    };
</script>
