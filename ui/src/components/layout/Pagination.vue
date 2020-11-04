<template>
    <div class="d-flex">
        <div class="flex-grow-1">
            <b-form-select
                v-model="size"
                @change="pageSizeChange"
                size="sm"
                :options="pageOptions"
            />
        </div>
        <div>
            <b-pagination
                @change="pageChanged"
                v-model="page"
                :total-rows="max || total"
                hide-ellipsis
                :per-page="size"
                size="sm"
                class="my-0"
                align="right"
            />
        </div>

        <small v-if="max" class="btn btn-sm btn-outline-light text-muted">
            {{ $t('Max displayed') }}: {{ max }}
        </small>

        <small class="btn btn-sm btn-outline-light text-muted">
            {{ $t('Total') }}: {{ total }}
        </small>
    </div>
</template>
<script>
    export default {
        props: {
            total: {type: Number, required: true},
            max: {type: Number, required:false, default: undefined}},
        data() {
            return {
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
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
                    size: this.size,
                });
            },
            pageChanged(page) {
                this.$emit("onPageChanged", {
                    page: page,
                    size: this.size,
                });
            },
        },
    };
</script>
<style scoped>
select {
    width: auto;
}
</style>