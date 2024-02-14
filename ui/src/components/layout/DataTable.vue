<template>
    <div>
        <nav v-if="hasNavBar">
            <collapse>
                <slot name="navbar" />
            </collapse>
        </nav>

        <el-container direction="vertical" v-loading="isLoading">
            <slot name="top" />

            <pagination :size="size" :top="true" :page="page" :total="total" :max="max" @page-changed="onPageChanged" />

            <slot name="table" />

            <pagination v-if="total > 0" :size="size" :page="page" :total="total" :max="max" @page-changed="onPageChanged" />
        </el-container>
    </div>
</template>

<script>
    import Pagination from "./Pagination.vue";
    import Collapse from "./Collapse.vue";

    export default {
        components: {Pagination, Collapse},
        emits: ["page-changed"],
        computed: {
            hasNavBar() {
                return !!this.$slots["navbar"];
            },
        },
        data() {
            return {
                isLoading: false,
            };
        },
        props: {
            total: {type: Number, required: true},
            max: {type: Number, required: false, default: undefined},
            size: {type: Number, default: 25},
            page: {type: Number, default: 1},
        },

        methods: {
            prevent(event) {
                event.preventDefault();
            },
            onPageChanged(pagination) {
                this.$emit("page-changed", pagination);
            },
        },
    };
</script>

<style scoped lang="scss">
    :deep(.el-table) {
        td {
            .el-tag {
                margin-right: calc(var(--spacer) / 3);
            }
        }
    }
</style>
