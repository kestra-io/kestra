<template>
    <div data-component="FILENAME_PLACEHOLDER" class="position-relative">
        <div v-if="hasSelection && data.length" class="bulk-select-header">
            <slot name="select-actions" />
        </div>

        <template v-if="data.length">
            <el-table
                ref="table"
                v-bind="$attrs"
                :data="data"
                @selection-change="selectionChanged"
            >
                <slot name="expand" v-if="expandable" />
                <el-table-column type="selection" v-if="selectable" />
                <slot name="default" />
            </el-table>
        </template>

        <NoData v-else />
    </div>
</template>

<script>
    import NoData from "./NoData.vue";

    export default {
        components: {NoData},
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
                const tableElement = this.$refs.table?.$el;

                if(!tableElement) return;

                this.$el.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`);
                this.$el.style.setProperty("--table-header-height", `${tableElement.querySelector("thead").clientHeight}px`);
            }
        },
        props: {
            selectable: {
                type: Boolean,
                default: true
            },
            expandable: {
                type: Boolean,
                default: false
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
        border-bottom: 1px solid var(--ks-border-primary);
        overflow-x: auto;

        & ~ .el-table {
            z-index: 0;
        }
    }
</style>
