<template>
    <el-tooltip v-if="hasTooltip" transition="" :hide-after="0" :persistent="false" placement="top" effect="light">
        <template #content>
            <code>{{ value }}</code>
        </template>
        <code :id="uuid" @click="onClick" class="text-nowrap" :class="{'link': hasClickListener}">
            {{ transformValue }}
        </code>
    </el-tooltip>
    <code v-else :id="uuid" class="text-nowrap" @click="onClick">
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
        methods: {
            onClick() {
                if (this.hasClickListener) {
                    this.$attrs.onClick();
                }
            }
        },
        computed: {
            hasClickListener() {
                return (this.$attrs && this.$attrs.onClick)
            },
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

<style lang="scss" scoped>
    code.link {
        cursor: pointer;
        &:hover {
            color: rgba(var(--bs-link-color-rgb), var(--bs-link-opacity, 1));
        }
    }
</style>
