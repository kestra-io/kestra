<template>
    <div v-if="usages" class="usages">
        <div class="header">
            <span>{{ $t("usage") }}</span>
            <slot name="button" />
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
    import moment from "moment"

    interface DailyExecution {
        groupBy: string;
        duration: {
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

    const router = useRouter()
    const route = useRoute()
    const miscStore = useMiscStore()
    const {t} = useI18n()

    const usages = ref<UsageData>()

    const isInstance = computed(() => route.params.type === "instance")

    const namespaces = computed(() => usages.value?.flows?.namespacesCount ?? 0)
    const flows = computed(() => usages.value?.flows?.count ?? 0)
    const tasks = computed(() => sumValues(usages.value?.flows?.taskTypeCount))
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

    const executionCount = computed(() =>
        dailyExecutions.value.reduce((total, day) => total + (day.duration.count ?? 0), 0),
    )

    const executionDurationMinutes = computed(() => {
        const total = dailyExecutions.value.reduce(
            (acc, day) => acc.add(moment.duration(day.duration.sum ?? "PT0S")),
            moment.duration("PT0S"),
        )
        return total.minutes()
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
            key: "triggers",
            labelKey: "triggers",
            value: triggers.value,
            route: "admin/triggers",
        },
        {
            key: "executions",
            labelKey: "executions",
            value: executionCount.value,
            subtitle: t("last 48 hours"),
            route: "executions/list",
        },
        {
            key: "executionsDuration",
            labelKey: "executions duration (in minutes)",
            value: executionDurationMinutes.value,
            subtitle: t("last 48 hours"),
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
            usages.value = next ?? await miscStore.loadAllUsages()
            emit("loaded")
        },
        {immediate: true},
    )
</script>

<style scoped lang="scss">
    .usages {
        .header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            width: 100%;
            margin-bottom: var(--ks-spacing-4);

            span {
                font-size: var(--ks-font-size-md);
                font-weight: var(--ks-font-weight-bold);
                color: var(--ks-text-primary);
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