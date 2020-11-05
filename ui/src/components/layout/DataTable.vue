<template>
    <div>
        <b-navbar toggleable="lg" type="light" variant="light" v-if="hasNavBar">
            <b-navbar-toggle target="nav-collapse"></b-navbar-toggle>
            <b-collapse id="nav-collapse" is-nav>
                <b-nav-form>
                    <slot name="navbar"></slot>
                </b-nav-form>
            </b-collapse>
        </b-navbar>

        <slot name="top"></slot>

        <slot name="table"></slot>
        <pagination :total="total" :max="max" @onPageChanged="onPageChanged" />
    </div>
</template>

<script>
import Pagination from "./Pagination";
export default {
    components: { Pagination },
    computed: {
        hasNavBar() {
            return !!this.$slots["navbar"];
        },
    },
    props: {
        total: { type: Number, required: true },
        max: {type: Number, required:false}
    },
    methods: {
        onPageChanged(pagination) {
            this.$emit("onPageChanged", pagination);
        },
    },
};
</script>

<style scoped lang="scss">
@import "../../styles/variable";

small {
    padding: $pagination-padding-y-sm $pagination-padding-x-sm;
    white-space: nowrap;
}

/deep/ th {
    white-space: nowrap;
}

/deep/ .badge {
    font-size: 100%;
    margin-right: $spacer/4;
    margin-bottom: $spacer/4;
    padding: $badge-padding-y $badge-padding-y;

    .badge {
        margin: 0;
        @include font-size($badge-font-size);
    }
}
</style>
