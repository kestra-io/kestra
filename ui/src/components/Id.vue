<template>
    <code :id="uuid" class="text-nowrap">
        {{ transformValue }}
        <b-tooltip
            custom-class="auto-width"
            v-if="hasTooltip"
            :target="uuid"
            triggers="hover"
        >
            <code>{{ value }}</code>
        </b-tooltip>
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
