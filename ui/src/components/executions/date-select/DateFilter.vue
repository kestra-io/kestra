<template>
    <el-radio-group
        v-model="selectedFilterType"
        @change="onSelectedFilterType()"
    >
        <el-radio-button :label="filterType.RELATIVE">
            {{ $t("relative") }}
        </el-radio-button>
        <el-radio-button :label="filterType.ABSOLUTE">
            {{ $t("absolute") }}
        </el-radio-button>
    </el-radio-group>
    <date-range
        v-if="selectedFilterType === filterType.ABSOLUTE"
        :start-date="startDate"
        :end-date="endDate"
        @update:model-value="onAbsFilterChange($event)"
    />
    <relative-date-select
        v-if="selectedFilterType === filterType.RELATIVE"
        :start-range="startRange"
        :end-range="endRange"
        @update:model-value="onRelFilterChange($event)"
    />
</template>

<script>
    import DateRange from "../../layout/DateRange.vue";
    import RelativeDateSelect from "./RelativeDateSelect.vue";

    export default {
        components: {
            DateRange,
            RelativeDateSelect
        },
        emits: [
            "update:isRelative",
            "update:filterValue"
        ],
        created() {
            this.filterType = {
                RELATIVE: "REL",
                ABSOLUTE: "ABS"
            };

            this.selectedFilterType = (this.$route.query.startDate || this.$route.query.endDate) ? this.filterType.ABSOLUTE : this.filterType.RELATIVE;
        },
        data() {
            return {
                selectedFilterType: undefined
            }
        },
        computed: {
            startRange() {
                return this.$route.query.startDateRange ? this.$route.query.startDateRange : "P1M";
            },
            endRange() {
                return this.$route.query.endDateRange ? this.$route.query.endDateRange : "PT0";
            },
            startDate() {
                return this.$route.query.startDate ? this.$route.query.startDate : this.$moment(this.endDate).add(-30, "days").toISOString(true);
            },
            endDate() {
                return this.$route.query.endDate ? this.$route.query.endDate : undefined;
            }
        },
        methods: {
            onSelectedFilterType() {
                this.$emit("update:isRelative", this.selectedFilterType === this.filterType.RELATIVE);
            },
            onAbsFilterChange(event) {
                const filter = {
                    "startDate": event.startDate,
                    "endDate": event.endDate,
                    "startDateRange": undefined,
                    "endDateRange": undefined
                };
                this.updateFilter(filter);
            },
            onRelFilterChange(event) {
                const filter = {
                    "startDate": undefined,
                    "endDate": undefined,
                    "startDateRange": event.startDateRange,
                    "endDateRange": event.endDateRange
                };
                this.updateFilter(filter);
            },
            updateFilter(filter) {
                this.$emit("update:filterValue", filter);
            }
        }
    }
</script>