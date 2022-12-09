<template>
    <div class="d-flex pagination">
        <div class="flex-grow-1 d-sm-none d-md-inline-block page-size">
            <el-select
                size="small"
                :model-value="internalSize"
                @update:model-value="pageSizeChange"
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
        <div>
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

        <small v-if="max" class="d-md-none d-lg-block total ms-2">
            {{ $t('Max displayable') }}: {{ max }}
        </small>

        <small class="total text-total ms-2">
            {{ $t('Total') }}: {{ total }}
        </small>
    </div>
</template>
<script>
    export default {
        props: {
            total: {type: Number, default: 0},
            max: {type: Number, default: undefined},
            size: {type: Number, required: true, default: 25},
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
            pageSizeChange(value) {
                this.internalSize = value;
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

    .pagination {
        margin-top: var(--spacer);

        .el-select {
            width: 100px;
        }

        .page-size {
            @include res(xs) {
                display: none;
            }
        }

        .text-total {
            color: var(--el-text-primary);
            font-weight: normal;
        }

        .total {
            padding: 0 4px;
            line-height: 1.85;
            font-size: var(--el-font-size-extra-small);
            border-radius: var(--bs-border-radius-sm);
            border: 1px solid var(--bs-border-color);
            white-space: nowrap;
        }
    }
</style>