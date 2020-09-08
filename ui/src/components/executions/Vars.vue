<template>
    <b-table
        striped
        hover
        bordered
        small
        show-empty
        :items="variables"
        :fields="fields"
        class="mb-0"
    >
        <template v-slot:cell(value)="row">
            <var-value :execution="execution" :value="row.item.value" />
        </template>

        <template v-slot:thead-top v-if="title">
            <b-tr class="top">
                <b-th colspan="2">{{ title }}</b-th>
            </b-tr>
        </template>

        <template v-slot:empty>
            <div class="alert alert-info mb-0" role="alert">
                {{ $t("no data current task")}}
            </div>
        </template>

        <template v-slot:cell(key)="row">
            <code>{{ row.item.key }}</code>
        </template>
    </b-table>
</template>

<script>
    import Utils from "../../utils/utils";
    import VarValue from "./VarValue";

    export default {
        components: {
            VarValue,
        },
        props: {
            data: {
                type: Object,
                required: true
            },
            title: {
                type: String,
                required: false
            },
            execution: {
                type: Object,
                required: true
            }
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
/deep/ thead tr:not(.top) {
    display: none;
}

/deep/ td.key {
    width: 150px;
}
</style>
