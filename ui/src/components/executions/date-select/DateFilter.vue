<template>
    <el-radio-group
        v-model="selectedFilterType"
        @change="onSelectedFilterType()"
        class="filter"
    >
        <el-radio-button :value="filterType.RELATIVE">
            {{ $t("relative") }}
        </el-radio-button>
        <el-radio-button :value="filterType.ABSOLUTE">
            {{ $t("absolute") }}
        </el-radio-button>
    </el-radio-group>
    <date-range
        v-if="selectedFilterType === filterType.ABSOLUTE"
        :start-date="startDate"
        :end-date="endDate"
        @update:model-value="onAbsFilterChange"
        class="w-auto"
    />
    <time-select
        v-if="selectedFilterType === filterType.RELATIVE"
        :time-range="timeRange"
        @update:model-value="onRelFilterChange"
    />
</template>

<script>
    import DateRange from "../../layout/DateRange.vue";
    import TimeSelect from "./TimeSelect.vue";

    export default {
        components: {
            DateRange,
            TimeSelect
        },
        emits: [
            "update:isRelative",
            "update:filterValue"
        ],
        created() {
            this.selectedFilterType = (this.$route.query.startDate || this.$route.query.endDate) ? this.filterType.ABSOLUTE : this.filterType.RELATIVE;
        },
        mounted() {
            this.$emit("update:isRelative", this.selectedFilterType === this.filterType.RELATIVE);
        },
        data() {
            return {
                selectedFilterType: undefined,
                filterType: {
                    RELATIVE: "REL",
                    ABSOLUTE: "ABS"
                }
            }
        },
        computed: {
            timeRange() {
                return this.$route.query.timeRange ? this.$route.query.timeRange : "P30D";
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
                const relativeFilterSelected = this.selectedFilterType === this.filterType.RELATIVE;

                this.$emit("update:isRelative", relativeFilterSelected);

                this.tryOverrideAbsFilter(relativeFilterSelected);
            },
            onAbsFilterChange(event) {
                const filter = {
                    "startDate": event.startDate,
                    "endDate": event.endDate,
                    "timeRange": undefined
                };
                this.updateFilter(filter);
            },
            onRelFilterChange(event) {
                const filter = {
                    "startDate": undefined,
                    "endDate": undefined,
                    "timeRange": event.timeRange
                };
                this.updateFilter(filter);
            },
            updateFilter(filter) {
                this.$emit("update:filterValue", filter);
            },
            tryOverrideAbsFilter(relativeFilterSelected) {
                if (relativeFilterSelected && (this.$route.query.startDate || this.$route.query.endDate)) {
                    const forcedDefaultRelativeFilter = {timeRange: undefined};
                    this.onRelFilterChange(forcedDefaultRelativeFilter);
                }
            }
        }
    }
</script>
