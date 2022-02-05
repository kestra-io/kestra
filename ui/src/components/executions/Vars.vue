<template>
    <b-table
        striped
        hover
        small
        show-empty
        :stacked="stacked"
        :responsive="true"
        :items="variables"
        :fields="fields"
        class="mb-0"
    >
        <template #thead-top v-if="title">
            <b-tr class="top">
                <b-th colspan="2">
                    {{ title }}
                </b-th>
            </b-tr>
        </template>

        <template #empty>
            <div class="alert alert-info mb-0" role="alert">
                {{ $t("no data current task") }}
            </div>
        </template>

        <template #cell(key)="row">
            <code>{{ row.item.key }}</code>
        </template>

        <template #cell(value)="row">
            <template v-if="row.item.date">
                <date-ago :inverted="true" :date="row.item.value" />
            </template>
            <template v-else-if="row.item.subflow">
                {{ row.item.value }}
                <sub-flow-link
                    class="btn-xs"
                    :execution-id="row.item.value"
                />
            </template>
            <template v-else>
                <var-value :execution="execution" :value="row.item.value" />
            </template>
        </template>
    </b-table>
</template>

<script>
    import Utils from "../../utils/utils";
    import VarValue from "./VarValue";
    import DateAgo from "../../components/layout/DateAgo";
    import SubFlowLink from "../flows/SubFlowLink"

    export default {
        components: {
            DateAgo,
            VarValue,
            SubFlowLink
        },
        props: {
            data: {
                type: Object,
                required: true
            },
            title: {
                type: String,
                required: false,
                default: undefined
            },
            execution: {
                type: Object,
                required: false,
                default: undefined
            },
            stacked: {
                type: Boolean,
                default: false
            },
        },
        computed: {
            fields() {
                return [
                    {
                        key: "key",
                        label: this.$t("name"),
                        class: "key"
                    },
                    {
                        key: "value",
                        label: this.$t("value")
                    }
                ];
            },
            variables() {
                return Utils.executionVars(this.data);
            },
        }
    };
</script>

<style scoped lang="scss">
::v-deep thead tr:not(.top) {
    display: none;
}

::v-deep td.key {
    width: 150px;
}

::v-deep .b-table-stacked {
    td.key {
        width: 100%;
    }

    td:before {
        display: none;
    }
}

</style>
