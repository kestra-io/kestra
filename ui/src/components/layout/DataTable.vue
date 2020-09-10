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
        <div class="d-flex">
            <div class="flex-grow-1">
                <b-form-select
                    v-model="size"
                    @change="pageSizeChange"
                    size="sm"
                    :options="pageOptions"
                ></b-form-select>
            </div>
            <div>
                <b-pagination
                    @change="pageChanged"
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
            page: parseInt(this.$route.query.page || 1),
            pageOptions: [
                {value: 10, text: `10 ${this.$t("Per page")}`},
                {value: 25, text: `25 ${this.$t("Per page")}`},
                {value: 50, text: `50 ${this.$t("Per page")}`},
                {value: 100, text: `100 ${this.$t("Per page")}`}
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
        pageSizeChange() {
            this.$emit("onPageChanged", {
                page: 1,
                size: this.size
            });
        },
        pageChanged(page) {
            this.$emit("onPageChanged", {
                page: page,
                size: this.size
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

/deep/ .navbar {
    border: 1px solid $table-border-color;
    border-bottom: 0;

    .navbar-collapse {
        input, .v-select, .btn-group, select, .date-range {
            margin-right: $spacer / 2;
        }

        &.collapse.show {
            padding-top: $spacer/2;

            form.inline, fieldset, .date-range {
                width: 100%;
            }

            .date-range {
                display: table;
                > div {
                    display: table-cell;
                    &:first-child {
                        padding-right: $spacer / 2;
                    }
                }
            }

            li.form-inline {
                width: 100%;
                display: block;
            }

            input, .v-select, .btn-group, select, .date-range {
                width: 100%;
                margin-right: 0;
            }

            input, .v-select, .btn-group, select {
                display: block;
                margin-bottom: $spacer/2;

                & input {
                    margin-bottom: 0;
                    width: 0;
                }
            }

        }
    }

}

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
