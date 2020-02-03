<template>
    <div>
        <time-line class="time-line" />
        <date-picker
            @input="onDate"
            v-model="start"
            :required="false"
            type="datetime"
            :placeholder="$t('start datetime')"
        ></date-picker>
        <date-picker
            @input="onDate"
            v-model="end"
            :required="false"
            type="datetime"
            :placeholder="$t('end datetime')"
        ></date-picker>
    </div>
</template>
<script>
import DatePicker from "vue2-datepicker";
import TimeLine from "vue-material-design-icons/TimelineClockOutline";
export default {
    components: { DatePicker, TimeLine },
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
            const query = { ...this.$route.query };
            query.start = start ? start.getTime() : null;
            query.end = end ? end.getTime() : null;
            this.$router.push({ query });
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