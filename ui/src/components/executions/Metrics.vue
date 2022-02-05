<template>
    <b-table
        striped
        hover
        small
        show-empty
        :responsive="true"
        :items="data"
        :fields="fields"
        class="mb-0"
    >
        <template #thead-top>
            <b-tr class="top">
                <b-th colspan="3">
                    {{ $t('metrics') }}
                </b-th>
            </b-tr>
        </template>

        <template #empty>
            <div class="alert alert-info mb-0" role="alert">
                {{ $t("no data current task") }}
            </div>
        </template>

        <template #cell(name)="row">
            <template v-if="row.item.type === 'timer'">
                <kicon><timer /></kicon>
            </template>
            <template v-else>
                <kicon><counter /></kicon>
            </template>
            &nbsp;<code>{{ row.item.name }}</code>
        </template>


        <template #cell(value)="row">
            <span v-if="row.item.type === 'timer'">
                {{ row.item.value | humanizeDuration }}
            </span>
            <span v-else>
                {{ row.item.value | humanizeNumber }}
            </span>
        </template>

        <template #cell(tags)="row">
            <b-badge
                v-for="(value, key) in row.item.tags"
                variant="primary"
                :key="key"
                class="mr-1"
                pill
            >
                {{ key }}: <strong>{{ value }}</strong>
            </b-badge>
        </template>
    </b-table>
</template>

<script>
    import Kicon from "../Kicon";
    import Timer from "vue-material-design-icons/Timer";
    import Counter from "vue-material-design-icons/Numeric";

    export default {
        components: {
            Kicon,
            Timer,
            Counter,
        },
        props: {
            data: {
                type: Array,
                required: true
            }
        },
        computed: {
            fields() {
                return [
                    {
                        key: "name",
                        label: this.$t("name"),
                        class: "key"
                    },
                    {
                        key: "tags",
                        label: this.$t("tags")
                    },
                    {
                        key: "value",
                        label: this.$t("value")
                    }
                ];
            }
        }
    };
</script>

<style scoped lang="scss">
::v-deep thead tr:not(.top) {
    display: none;
}

</style>
