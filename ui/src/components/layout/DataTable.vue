<template>
    <div>
        <b-navbar type="light" variant="light" v-if="hasNavBar">
            <b-nav-form>
                <slot name="navbar"></slot>
            </b-nav-form>
        </b-navbar>

        <slot name="table"></slot>
        <div class="d-flex">
            <div class="flex-grow-1">
                <b-form-select
                    v-model="size"
                    @change="perPageChange"
                    size="sm"
                    :options="pageOptions"
                ></b-form-select>
            </div>
            <div>
                <b-pagination
                    @change="changed"
                    v-model="page"
                    :total-rows="total"
                    hide-ellipsis
                    :per-page="size"
                    size="sm"
                    class="my-0"
                    align="right"
                ></b-pagination>
            </div>
            <small class="btn btn-sm btn-outline-light text-muted">Total: {{total}}</small>
        </div>
    </div>
</template>

<script>
export default {
    data() {
        return {
            size: parseInt(this.$route.query.size || 25),
            page: parseInt(this.$route.query.size || 1),
            pageOptions: [
                { value: 25, text: `25 ${this.$t("Per page")}` },
                { value: 50, text: `50 ${this.$t("Per page")}` },
                { value: 100, text: `100 ${this.$t("Per page")}` }
            ]
        };
    },
    computed: {
        hasNavBar() {
            return !!this.$slots["navbar"];
        }
    },
    props: {
        total: { type: Number, required: true }
    },
    methods: {
        perPageChange() {
            this.$router.push({
                query: {
                    ...this.$route.query,
                    size: this.size
                }
            });
            this.$emit("onPageChanged");
        },
        changed(page) {
            this.$router.push({
                query: {
                    ...this.$route.query,
                    page: page
                }
            });
            this.$emit("onPageChanged");
        }
    }
};
</script>

<style scoped lang="scss">
@import "../../styles/variable";

select {
    width: auto;
}

.navbar {
    border: 1px solid $table-border-color;
    border-bottom: 0;
}

small {
    padding: $pagination-padding-y-sm $pagination-padding-x-sm;
    white-space: nowrap;
}

/deep/ th {
    white-space: nowrap;
}
</style>
