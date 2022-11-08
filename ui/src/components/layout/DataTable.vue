<template>
    <div>
        <b-navbar toggleable="lg" class="nav-filter mb-4" v-if="hasNavBar">
            <b-navbar-toggle target="nav-collapse" />
            <b-collapse id="nav-collapse" is-nav>
                <b-nav-form @submit.prevent="prevent">
                    <slot name="navbar" />
                </b-nav-form>
            </b-collapse>
        </b-navbar>

        <b-overlay :show="isLoading" variant="transparent">
            <slot name="top" />

            <slot name="table" />
            <pagination :size="size" :page="page" :total="total" :max="max" @page-changed="onPageChanged" />
        </b-overlay>
    </div>
</template>

<script>
    import Pagination from "./Pagination";
    export default {
        components: {Pagination},
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
            page: {type: Number, default: 1}
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
@use "sass:math";
@import "../../styles/variable";

small {
    padding: $pagination-padding-y-sm $pagination-padding-x-sm;
    white-space: nowrap;
}


:deep(th) {
    white-space: nowrap;
}

:deep(.badge) {
    font-size: 100%;
    margin-right: math.div($spacer, 4);
    margin-bottom: math.div($spacer, 4);
    padding: $badge-padding-y $badge-padding-y;

    .badge {
        margin: 0;
        @include font-size($badge-font-size);
    }
}
</style>
