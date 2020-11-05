<template>
    <div class="date-range">
        <date-picker
            @input="onDate"
            v-model="start"
            :required="false"
            type="datetime"
            class="sm"
            :placeholder="$t('start datetime')"
        />
        <date-picker
            @input="onDate"
            v-model="end"
            :required="false"
            type="datetime"
            class="sm"
            :placeholder="$t('end datetime')"
        />
    </div>
</template>
<script>
    import DatePicker from "vue2-datepicker";
    export default {
        components: {DatePicker},
        data() {
            return {
                start: null,
                end: null
            };
        },
        created() {
            if (this.$route.query.start) {
                this.start = new Date(parseInt(this.$route.query.start));
            }
            if (this.$route.query.end) {
                this.end = new Date(parseInt(this.$route.query.end));
            }
        },
        methods: {
            onDate() {
                const start = this.start,
                      end = this.end;
                const dateRange = {
                    start: start ? start.toISOString() : null,
                    end: end ? end.toISOString() : null
                };
                const query = {...this.$route.query};
                query.start = start ? start.getTime() : undefined;
                query.end = end ? end.getTime() : undefined;
                this.$router.push({query});
                this.$emit("onDate", dateRange);
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
