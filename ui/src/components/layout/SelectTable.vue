<template>
    <div :data-component="dataComponent" class="position-relative">
        <div v-if="hasSelection" class="bulk-select-header">
            <slot name="select-actions" />
        </div>
        <el-table ref="table" v-bind="$attrs" :data="data" @selection-change="selectionChanged">
            <el-table-column type="selection" v-if="selectable" />
            <slot name="default" />
        </el-table>
    </div>
</template>

<script>
    import BaseComponents from "../BaseComponents.vue"

    export default {
        extends: BaseComponents,
        data() {
            return {
                hasSelection: false
            }
        },
        methods: {
            selectionChanged(selection) {
                this.hasSelection = selection.length > 0;
                this.$emit("selection-change", selection);
            },
            computeHeaderSize() {
                const tableElement = this.$refs.table.$el;
                this.$el.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`);
                this.$el.style.setProperty("--table-header-height", `${tableElement.querySelector("thead").clientHeight}px`);
            }
        },
        props: {
            selectable: {
                type: Boolean,
                default: true
            },
            data: {
                type: Array,
                default: () => []
            }
        },
        emits: [
            "selection-change"
        ],
        mounted() {
            window.addEventListener("resize", this.computeHeaderSize);
        },
        unmounted() {
            window.removeEventListener("resize", this.computeHeaderSize);
        },
        updated() {
            this.computeHeaderSize();
        }
    }
</script>

<style scoped lang="scss">
    .bulk-select-header {
        z-index: 1;
        position: absolute;
        height: var(--table-header-height);
        width: var(--table-header-width);
        background-color: var(--bs-gray-100-darken-3);
        border-radius: var(--bs-border-radius-lg) var(--bs-border-radius-lg) 0 0;
        border-bottom: 1px solid var(--bs-border-color);

        & ~ .el-table {
            z-index: 0;
        }
    }
</style>
