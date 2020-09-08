<template>
    <b-table
        striped
        hover
        bordered
        small
        :items="variables"
        :fields="fields"
        class="mb-0"
    >
        <template v-slot:cell(value)="row">
            <var-value :execution="execution" :value="row.item.value" />
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
/deep/ thead {
    display: none;
}

/deep/ td.key {
    width: 250px;
}
</style>
