<template>
    <div v-if="usages" class="usages">
        <div class="header">
            <span>{{ $t("usage") }}</span>
            <div class="actions">
                <KsSelect
                    v-model="period"
                    size="small"
                    class="period"
                    :aria-label="$t('usage period')"
                    data-test="usage-period"
                >
                    <KsOption
                        v-for="option in periodOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                    />
                </KsSelect>
                <slot name="button" />
            </div>
        </div>
        <div class="grid">
            <UsageCard
                v-for="item in usageItems"
                :key="item.key"
                :label="$t(item.labelKey)"
                :value="item.value"
                :subtitle="item.subtitle"
                :to="linkFor(item.route)"
            />
            <slot name="additional-usages" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute, useRouter, type RouteLocationRaw} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useMiscStore} from "override/stores/misc"
    import UsageCard from "./UsageCard.vue"
    import {dayjs, KsOption, KsSelect} from "@kestra-io/design-system"

    type UsagePeriod = 1 | 7 | 30

    interface DailyExecution {
        groupBy: string;
        startDate?: string;
        duration: {
            count?: number;
            sum?: string;
        };
        taskRunsDuration?: {
            count?: number;
            sum?: string;
        };
    }

    interface UsageData {
        flows?: {
            namespacesCount?: number;
            count?: number;
            taskTypeCount?: Record<string, number>;
            triggerTypeCount?: Record<string, number>;
            taskRunnerTypeCount?: Record<string, number>;
        };
        executions?: {
            dailyExecutionsCount?: DailyExecution[];
        };
    }

    interface UsageItem {
        key: string;
        labelKey: string;
        value: number;
        subtitle?: string;
        route: string;
    }

    const props = defineProps<{
        fetchedUsages?: UsageData;
    }>()

    const emit = defineEmits<{
        (e: "loaded"): void;
    }>()

    const period = defineModel<UsagePeriod>("period", {default: 1})

    const router = useRouter()
    const route = useRoute()
    const miscStore = useMiscStore()
    const {t, locale} = useI18n()

    const usages = ref<UsageData>()

    const periodOptions = computed(() => [
        {value: 1 as UsagePeriod, label: t("datepicker.24hours")},
        {value: 7 as UsagePeriod, label: t("datepicker.7days")},
        {value: 30 as UsagePeriod, label: t("datepicker.30days")},
    ])

    const periodLabel = computed(() => t(period.value === 1 ? "last 24 hours" : `last ${period.value} days`))

    const isInstance = computed(() => route.params.type === "instance")

    const namespaces = computed(() => usages.value?.flows?.namespacesCount ?? 0)
    const flows = computed(() => usages.value?.flows?.count ?? 0)
    const tasks = computed(() => sumValues(usages.value?.flows?.taskTypeCount))
    const taskRunners = computed(() => sumValues(usages.value?.flows?.taskRunnerTypeCount))
    const triggers = computed(() => sumValues(usages.value?.flows?.triggerTypeCount))

    const namespaceRoute = computed(() => {
        try {
            router.resolve({name: "namespaces/list"})
            return "namespaces/list"
        } catch {
            return "flows/list"
        }
    })

    const dailyExecutions = computed<DailyExecution[]>(() =>
        (usages.value?.executions?.dailyExecutionsCount ?? []).filter((entry) => entry.groupBy === "day"),
    )

    // The window starts `period` days ago. When the first day with any execution comes noticeably later,
    // the instance (or tenant) is younger than the window, so the figures only cover what exists since then.
    const dataStart = computed(() => {
        const firstDay = dailyExecutions.value
            .filter((day) => (day.duration.count ?? 0) > 0 && day.startDate)
            .map((day) => dayjs(day.startDate).startOf("day"))
            .sort((a, b) => a.valueOf() - b.valueOf())[0]

        const windowStart = dayjs().subtract(period.value, "day").startOf("day")

        return firstDay && firstDay.diff(windowStart, "day") > 1 ? firstDay : undefined
    })

    const subtitle = computed(() => {
        if (!dataStart.value) {
            return periodLabel.value
        }

        const intlLocale = locale.value === "en" ? "en-GB" : locale.value.replace("_", "-")
        const date = dataStart.value.toDate().toLocaleDateString(intlLocale, {day: "numeric", month: "short"})
        return t("since {date}", {date})
    })

    const executionCount = computed(() =>
        dailyExecutions.value.reduce((total, day) => total + (day.duration.count ?? 0), 0),
    )

    const executionDurationMinutes = computed(() => {
        const total = dailyExecutions.value.reduce(
            (acc, day) => acc.add(dayjs.duration(day.duration.sum ?? "PT0S")),
            dayjs.duration("PT0S"),
        )
        return Math.floor(total.asMinutes())
    })

    const taskRunCount = computed(() =>
        dailyExecutions.value.reduce((total, day) => total + (day.taskRunsDuration?.count ?? 0), 0),
    )

    const taskRunDurationMinutes = computed(() => {
        const total = dailyExecutions.value.reduce(
            (acc, day) => acc.add(dayjs.duration(day.taskRunsDuration?.sum ?? "PT0S")),
            dayjs.duration("PT0S"),
        )
        return Math.floor(total.asMinutes())
    })

    const usageItems = computed<UsageItem[]>(() => [
        {
            key: "namespaces",
            labelKey: "namespaces",
            value: namespaces.value,
            route: namespaceRoute.value,
        },
        {
            key: "flows",
            labelKey: "flows",
            value: flows.value,
            route: "flows/list",
        },
        {
            key: "tasks",
            labelKey: "tasks",
            value: tasks.value,
            route: "flows/list",
        },
        {
            key: "taskRunners",
            labelKey: "taskRunners",
            value: taskRunners.value,
            route: "flows/list",
        },
        {
            key: "triggers",
            labelKey: "triggers",
            value: triggers.value,
            route: "admin/triggers",
        },
        {
            key: "executions",
            labelKey: "executions",
            value: executionCount.value,
            subtitle: subtitle.value,
            route: "executions/list",
        },
        {
            key: "executionsDuration",
            labelKey: "executions duration (in minutes)",
            value: executionDurationMinutes.value,
            subtitle: subtitle.value,
            route: "executions/list",
        },
        {
            key: "taskRuns",
            labelKey: "task runs",
            value: taskRunCount.value,
            subtitle: subtitle.value,
            route: "executions/list",
        },
        {
            key: "taskRunsDuration",
            labelKey: "task runs duration (in minutes)",
            value: taskRunDurationMinutes.value,
            subtitle: subtitle.value,
            route: "executions/list",
        },
    ])

    function sumValues(record?: Record<string, number>): number {
        return Object.values(record ?? {}).reduce((total, value) => total + value, 0)
    }

    function linkFor(routeName: string): RouteLocationRaw | undefined {
        if (isInstance.value) {
            return undefined
        }
        return {name: routeName}
    }

    watch(
        () => props.fetchedUsages,
        async (next) => {
            usages.value = next ?? await miscStore.loadAllUsages(period.value) as UsageData
            emit("loaded")
        },
        {immediate: true},
    )

    // Without usages handed in, this component loads them itself, so it also reloads them for the new window.
    watch(period, async (next) => {
        if (props.fetchedUsages === undefined) {
            usages.value = await miscStore.loadAllUsages(next) as UsageData
        }
    })
</script>

<style scoped>
    .usages {
        .header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            width: 100%;
            margin-bottom: var(--ks-spacing-4);

            > span {
                font-size: var(--ks-font-size-md);
                font-weight: var(--ks-font-weight-bold);
                color: var(--ks-text-primary);
            }

            .actions {
                display: flex;
                align-items: center;
                gap: var(--ks-spacing-2);
            }

            .period {
                width: 120px;
            }
        }

        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
            grid-auto-rows: 1fr;
            gap: var(--ks-spacing-2);
            max-width: 593px;
        }
    }
</style>