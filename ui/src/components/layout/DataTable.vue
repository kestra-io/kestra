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
                    v-model="pagination.size"
                    @change="changed"
                    size="sm"
                    :options="pageOptions"
                ></b-form-select>
            </div>
            <div>
                <b-pagination
                    @change="changed"
                    v-model="pagination.page"
                    :total-rows="total"
                    hide-ellipsis
                    :per-page="pagination.size"
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
            pagination: {
                page: 1,
                size: 25
            },
            pageOptions: [
                { value: 25, text: 25 + " " + this.$i18n.t("Per page") },
                { value: 50, text: 50 + " " + this.$i18n.t("Per page") },
                { value: 100, text: 100 + " " + this.$i18n.t("Per page") }
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
        changed(page) {
            this.$emit("onPageChanged", {
                size: this.pagination.size,
                page
            });
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
</style>
