<template>
    <div class="d-flex">
        <div class="flex-grow-1">
            <b-form-select
                v-model="internalSize"
                @change="pageSizeChange"
                size="sm"
                :options="pageOptions"
            />
        </div>
        <div>
            <b-pagination
                @change="pageChanged"
                v-model="internalPage"
                :total-rows="Math.min((max || total ),total)"
                hide-ellipsis
                :per-page="internalSize"
                size="sm"
                class="my-0"
                align="right"
            />
        </div>

        <small v-if="max" class="btn btn-sm btn-outline-light text-muted">
            {{ $t('Max displayable') }}: {{ max }}
        </small>

        <small class="total btn btn-sm btn-outline-light text-muted">
            {{ $t('Total') }}: {{ total }}
        </small>
    </div>
</template>
<script>
    export default {
        props: {
            total: {type: Number, default: 0},
            max: {type: Number, default: undefined},
            size: {type: Number, required: true},
            page: {type: Number, required: true}
        },
        data() {
            return {
                internalSize: parseInt(this.$route.query.size || this.size),
                internalPage: parseInt(this.$route.query.page || this.page),
                pageOptions: [
                    {value: 10, text: `10 ${this.$t("Per page")}`},
                    {value: 25, text: `25 ${this.$t("Per page")}`},
                    {value: 50, text: `50 ${this.$t("Per page")}`},
                    {value: 100, text: `100 ${this.$t("Per page")}`},
                ],
            };
        },
        methods: {
            pageSizeChange() {
                this.$emit("onPageChanged", {
                    page: 1,
                    size: this.internalSize,
                });
            },
            pageChanged(page) {
                this.internalPage = page;
                this.$emit("onPageChanged", {
                    page: page,
                    size: this.internalSize,
                });
            },
        },
    };
</script>
<style scoped lang="scss">
@import "../../styles/_variable.scss";

select {
    width: auto;
}

@media (max-width: map-get($grid-breakpoints, "sm")) {
    select {
        display: none;
    }
}

.total {
    white-space: nowrap;
}
</style>