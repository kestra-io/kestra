<template>
    <div class="d-flex">
        <div class="flex-grow-1 d-sm-none d-md-inline-block page-size">
            <el-select
                size="small"
                v-model="internalSize"
                @change="pageSizeChange"
                :persistent="false"
            >
                <el-option
                    v-for="item in pageOptions"
                    :key="item.value"
                    :label="item.text"
                    :value="item.value"
                />
            </el-select>
        </div>
        <div class="mr-auto">
            <el-pagination
                v-model:current-page="internalPage"
                :page-size="internalSize"
                v-model:page-size="internalSize"
                small
                background
                layout="prev, pager, next"
                :pager-count="5"
                :total="Math.min((max || total ),total)"
                @current-change="pageChanged"
                class="my-0"
            />

        </div>

        <small v-if="max" class="d-md-none d-lg-block total mr-1">
            {{ $t('Max displayable') }}: {{ max }}
        </small>

        <small class="total text-total">
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
        emits: ["page-changed"],
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
                this.$emit("page-changed", {
                    page: 1,
                    size: this.internalSize,
                });
            },
            pageChanged(page) {
                this.internalPage = page;
                this.$emit("page-changed", {
                    page: page,
                    size: this.internalSize,
                });
            },
        },
    };
</script>
<style scoped lang="scss">
@use 'element-plus/theme-chalk/src/mixins/mixins' as *;

.el-select {
    width: auto;
}

.page-size {

    @include res(xs) {
        display: none;
    }
}

.text-total {
    color: getCssVar('text-color', 'primary');
    font-weight: normal;
}

.total {
    margin-top: 2px;
    margin-bottom: 2px;
    padding: 0 4px;
    line-height: 1.8;
    font-size: getCssVar('font-size', 'extra-small');
    border-radius: getCssVar('border-radius', 'small');
    border: 1px solid getCssVar('border-color');
    white-space: nowrap;
}
</style>