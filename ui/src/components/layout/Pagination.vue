<template>
    <div :data-component="'FILENAME_PLACEHOLDER' + (top ? '#top' : '#not-top')" class="d-flex pagination" :class="{'top': top}">
        <slot name="search" />
        <div class="flex-grow-1 d-sm-none d-md-inline-block page-size">
            <el-select
                v-if="!top"
                size="small"
                :model-value="internalSize"
                @update:model-value="pageSizeChange"
            >
                <el-option
                    v-for="item in pageOptions"
                    :key="item.value"
                    :label="item.text"
                    :value="item.value"
                />
            </el-select>
        </div>
        <div v-if="isPaginationDisplayed">
            <el-pagination
                v-model:current-page="internalPage"
                :page-size="internalSize"
                v-model:page-size="internalSize"
                size="small"
                layout="prev, pager, next"
                :pager-count="5"
                :total="total"
                @current-change="pageChanged"
                class="my-0"
            />
        </div>

        <small class="total text-total ms-2">
            {{ $t('Total') }}: {{ total }}
        </small>
    </div>
</template>
<script>
    import {storageKeys} from "../../utils/constants";

    export default {
        props: {
            total: {type: Number, default: 0},
            size: {type: Number, required: true, default: 25},
            page: {type: Number, required: true},
            top: {type: Boolean, required: false, default: false}
        },
        emits: ["page-changed"],
        data() {
            return {
                ...this.initState(),
                pageOptions: [
                    {value: 10, text: `10 ${this.$t("Per page")}`},
                    {value: 25, text: `25 ${this.$t("Per page")}`},
                    {value: 50, text: `50 ${this.$t("Per page")}`},
                    {value: 100, text: `100 ${this.$t("Per page")}`},
                ],
            };
        },
        methods: {
            initState() {
                let internalSize = parseInt(localStorage.getItem(storageKeys.PAGINATION_SIZE) || this.$route.query.size || this.size)
                let internalPage = parseInt(this.$route.query.page || this.page)
                this.$emit("page-changed", {
                    page: internalPage,
                    size: internalSize,
                });

                return {
                    internalSize: internalSize,
                    internalPage: internalPage
                }
            },
            pageSizeChange: function (value) {
                this.internalPage = 1;
                this.internalSize = value;
                localStorage.setItem(storageKeys.PAGINATION_SIZE, value);
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
        computed: {
            isPaginationDisplayed() {
                if (this.internalPage === 1 && this.total < this.internalSize) {
                    return false;
                }

                return true;
            },
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    Object.assign(this, this.initState());
                }
            },
        }
    };
</script>
<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .pagination {
        margin-top: 1rem;

        &.top {
            margin-bottom: 1rem;
            margin-top: 0;
        }

        .el-select {
            width: 105px;
        }

        .page-size {
            @include res(xs) {
                display: none;
            }
        }

        .text-total {
            color: var(--ks-content-primary);
            font-weight: normal;
        }

        .total {
            padding: 0 4px;
            line-height: 1.85;
            font-size: var(--el-font-size-extra-small);
            color: var(--ks-content-link);
            white-space: nowrap;
        }
    }
</style>