<template>
    <div>
        <nav data-component="FILENAME_PLACEHOLDER#nav" v-if="hasNavBar">
            <collapse>
                <slot name="navbar" />
            </collapse>
        </nav>

        <el-container data-component="FILENAME_PLACEHOLDER#container" direction="vertical" v-loading="isLoading">
            <slot name="top" data-component="FILENAME_PLACEHOLDER#top" />

            <pagination v-if="!embed" :size="size" :top="true" :page="page" :total="total" @page-changed="onPageChanged">
                <template #search>
                    <slot name="search" />
                </template>
            </pagination>

            <slot name="table" data-component="FILENAME_PLACEHOLDER#table" />

            <pagination v-if="total > 0" :size="size" :page="page" :total="total" @page-changed="onPageChanged" />
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
            size: {type: Number, default: 25},
            page: {type: Number, default: 1},
            embed: {type: Boolean, default: false},
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
