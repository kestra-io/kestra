<template>
    <div data-component="FILENAME_PLACEHOLDER" class="position-relative">
        <div v-if="hasSelection && data.length" class="bulk-select-header">
            <slot name="select-actions" />
        </div>

        <NoData v-if="data.length === 0 && infiniteScrollLoad === undefined" />

        <template v-else>
            <el-table
                ref="table"
                v-bind="$attrs"
                :data="data"
                @selection-change="selectionChanged"
                v-el-table-infinite-scroll="infiniteScrollLoadWithDisableHandling"
                :infinite-scroll-disabled="infiniteScrollLoad === undefined ? true : infiniteScrollDisabled"
                :infinite-scroll-delay="0"
                :height="tableHeight"
            >
                <slot name="expand" v-if="expandable" />
                <el-table-column type="selection" v-if="selectable" />
                <slot name="default" />
            </el-table>
        </template>
    </div>
</template>

<script>
    import NoData from "./NoData.vue";
    import elTableInfiniteScroll from "el-table-infinite-scroll";

    export default {
        components: {NoData},
        data() {
            return {
                hasSelection: false,
                infiniteScrollDisabled: false,
                tableHeight: this.infiniteScrollLoad === undefined ? "auto" : "100%"
            }
        },
        expose: ["resetInfiniteScroll"],
        computed: {
            scrollWrapper() {
                if (this.data) {
                    return this.$refs.table?.$el?.querySelector(".el-scrollbar__wrap");
                }

                return undefined;
            },
            tableView() {
                if (this.data) {
                    return this.scrollWrapper?.querySelector(".el-scrollbar__view");
                }

                return undefined;
            },
            stillHaveDataToFetch() {
                return this.infiniteScrollDisabled === false;
            }
        },
        directives: {
            elTableInfiniteScroll
        },
        methods: {
            async resetInfiniteScroll() {
                this.infiniteScrollDisabled = false;
                this.tableHeight = await this.computeTableHeight();
            },
            async waitTableRender() {
                if (this.tableView === undefined) {
                    return Promise.resolve();
                }

                if (this.tableView.querySelectorAll(".el-table__body > tbody > *")?.length === this.data?.length) {
                    return Promise.resolve();
                }

                return new Promise(resolve => {
                    const observer = new MutationObserver(([{target}]) => {
                        if (target.childElementCount === this.data?.length) {
                            observer.disconnect();
                            resolve();
                        }
                    });

                    observer.observe(this.tableView.querySelector(".el-table__body > tbody"), {childList: true});
                });
            },
            selectionChanged(selection) {
                this.hasSelection = selection.length > 0;
                this.$emit("selection-change", selection);
            },
            computeHeaderSize() {
                const tableElement = this.$refs.table?.$el;

                if(!tableElement) return;

                this.$el.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`);
                this.$el.style.setProperty("--table-header-height", `${tableElement.querySelector("thead").clientHeight}px`);
            },
            async computeTableHeight()  {
                await this.waitTableRender();

                if (this.infiniteScrollLoad === undefined || this.scrollWrapper === undefined) {
                    return "auto";
                }

                if (!this.stillHaveDataToFetch && this.data.length === 0) {
                    return "calc(var(--table-header-height) + 60px)";
                }

                return this.stillHaveDataToFetch || this.tableView === undefined ? "100%" : `min(${this.tableView.scrollHeight}px, 100%)`;
            },
            async infiniteScrollLoadWithDisableHandling() {
                let load = await this.infiniteScrollLoad();
                while (load !== undefined && load.length === 0) {
                    load = await this.infiniteScrollLoad();
                }

                this.infiniteScrollDisabled = load === undefined;

                return load;
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
            },
            infiniteScrollLoad: {
                type: Function,
                default: undefined
            }
        },
        emits: [
            "selection-change"
        ],
        async mounted() {
            window.addEventListener("resize", this.computeHeaderSize);
        },
        unmounted() {
            window.removeEventListener("resize", this.computeHeaderSize);
        },
        updated() {
            this.computeHeaderSize();
        },
        watch: {
            data: {
                async handler() {
                    this.tableHeight = await this.computeTableHeight();
                },
                immediate: true
            },
            async stillHaveDataToFetch(newVal, oldVal) {
                if (oldVal !== newVal) {
                    this.tableHeight = await this.computeTableHeight();
                }
            }
        }
    }
</script>

<style scoped lang="scss">
    .bulk-select-header {
        z-index: 1;
        position: absolute;
        height: var(--table-header-height);
        width: var(--table-header-width);
        background-color: var(--ks-background-table-header);
        border-radius: var(--bs-border-radius-lg) var(--bs-border-radius-lg) 0 0;
        border-bottom: 1px solid var(--ks-border-primary);
        overflow-x: auto;

        & ~ .el-table {
            z-index: 0;
        }
    }
</style>
