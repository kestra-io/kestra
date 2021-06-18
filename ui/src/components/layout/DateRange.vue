<template>
    <div class="date-range">
        <date-picker
            @input="onDate('start', $event)"
            :value="startDate"
            :required="false"
            type="datetime"
            class="sm"
            :placeholder="$t('start datetime')"
        />
        <date-picker
            @input="onDate('end', $event)"
            :value="endDate"
            :required="false"
            type="datetime"
            class="sm"
            :placeholder="$t('end datetime')"
        />
    </div>
</template>
<script>
    import DatePicker from "vue2-datepicker";
    import moment from "moment";

    export default {
        components: {DatePicker},
        props: {
            start: {
                type: String,
                default: undefined
            },
            end: {
                type: String,
                default: undefined
            }
        },
        methods: {
            onDate(event, value) {
                this.$emit(event, value ? moment(value).toISOString(true) : undefined);
            }
        },
        computed: {
            startDate() {
                return new Date(this.start);
            },
            endDate() {
                return new Date(this.end);
            }
        }
    };
</script>
<style scoped lang="scss">
.time-line {
    margin-left: 10px;
    margin-right: 10px;
}
/deep/ .mx-datepicker {
    margin-right: 5px;
}
</style>
