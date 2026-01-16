<template>
    <div v-if="wrap">
        <el-radio-group
            v-model="selectedFilterType"
            @change="onSelectedFilterType()"
            class="filter"
        >
            <el-radio-button
                :value="filterType.RELATIVE"
            >
                {{ $t("relative") }}
            </el-radio-button>
            <el-radio-button
                :value="filterType.ABSOLUTE"
            >
                {{ $t("absolute") }}
            </el-radio-button>
        </el-radio-group>
        <DateRange
            v-if="selectedFilterType === filterType.ABSOLUTE"
            :startDate="startDate"
            :endDate="endDate"
            @update:model-value="onAbsFilterChange"
            class="w-auto"
        />
        <TimeSelect
            v-if="selectedFilterType === filterType.RELATIVE"
            :timeRange="timeRange"
            @update:model-value="onRelFilterChange"
        />
    </div>
    <template v-else>
        <el-radio-group
            v-model="selectedFilterType"
            @change="onSelectedFilterType()"
            class="filter"
        >
            <el-radio-button
                :value="filterType.RELATIVE"
            >
                {{ $t("relative") }}
            </el-radio-button>
            <el-radio-button
                :value="filterType.ABSOLUTE"
            >
                {{ $t("absolute") }}
            </el-radio-button>
        </el-radio-group>
        <DateRange
            v-if="selectedFilterType === filterType.ABSOLUTE"
            :startDate="startDate"
            :endDate="endDate"
            @update:model-value="onAbsFilterChange"
            class="w-auto"
        />
        <TimeSelect
            v-if="selectedFilterType === filterType.RELATIVE"
            :timeRange="timeRange"
            @update:model-value="onRelFilterChange"
        />
    </template>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import {useRoute} from "vue-router";
    // @ts-expect-error types to be done
    import DateRange from "../../layout/DateRange.vue";
    // @ts-expect-error types to be done
    import TimeSelect from "./TimeSelect.vue";
    import moment from "moment";

    interface FilterValue {
        startDate?: string;
        endDate?: string;
        timeRange?: string;
    };

    interface AbsoluteEvent {
        startDate?: string;
        endDate?: string;
    };

    interface RelativeEvent {
        timeRange?: string;
    };

    const filterType = {
        RELATIVE: "REL",
        ABSOLUTE: "ABS",
    } as const;

    type FilterType = typeof filterType[keyof typeof filterType];

    const props = withDefaults(defineProps<{ absolute?: boolean; wrap?: boolean }>(), {
        absolute: false,
        wrap: false,
    });

    const emit = defineEmits<{
        (e: "update:isRelative", value: boolean): void;
        (e: "update:filterValue", value: FilterValue): void;
    }>();

    const route = useRoute();

    const normalizedQuery = computed<Record<string, string>>(() => {
        const entries = Object.entries(route.query).map(([key, value]) => [
            key,
            Array.isArray(value) ? value[0] ?? "" : value ?? "",
        ]);
        return Object.fromEntries(entries);
    });

    const selectedFilterType = ref<FilterType | undefined>(undefined);

    const endDate = computed<string | undefined>(() => {
        return normalizedQuery.value.endDate ? String(normalizedQuery.value.endDate) : undefined;
    });

    const timeRange = computed<string>(() => {
        return normalizedQuery.value.timeRange ? String(normalizedQuery.value.timeRange) : "P30D";
    });

    const startDate = computed<string | undefined>(() => {
        if (normalizedQuery.value.startDate) return String(normalizedQuery.value.startDate);
        if (moment && endDate.value) {
            return moment(endDate.value).add(-30, "days").toISOString(true);
        }
        return undefined;
    });

    selectedFilterType.value = props.absolute
        ? filterType.ABSOLUTE
        : (normalizedQuery.value.startDate || normalizedQuery.value.endDate)
            ? filterType.ABSOLUTE
            : filterType.RELATIVE;

    onMounted(() => {
        emit("update:isRelative", selectedFilterType.value === filterType.RELATIVE);
    });

    function updateFilter(filter: FilterValue) {
        emit("update:filterValue", filter);
    }

    function onAbsFilterChange(event: AbsoluteEvent) {
        const filter: FilterValue = {
            startDate: event.startDate,
            endDate: event.endDate,
            timeRange: undefined,
        };
        updateFilter(filter);
    }

    function onRelFilterChange(event: RelativeEvent) {
        const filter: FilterValue = {
            startDate: undefined,
            endDate: undefined,
            timeRange: event.timeRange,
        };
        updateFilter(filter);
    }

    function tryOverrideAbsFilter(relativeFilterSelected: boolean) {
        const q = normalizedQuery.value;
        if (relativeFilterSelected && (q.startDate || q.endDate)) {
            onRelFilterChange({timeRange: undefined});
        }
    }

    function onSelectedFilterType() {
        const relativeFilterSelected = selectedFilterType.value === filterType.RELATIVE;
        emit("update:isRelative", relativeFilterSelected);
        tryOverrideAbsFilter(relativeFilterSelected);
    }
</script>
