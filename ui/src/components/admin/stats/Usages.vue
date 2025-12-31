<template>
    <div v-if="usages" class="usage-card">
        <div class="usage-card-header">
            <span>{{ $t('usage') }}</span>
            <slot name="button" />
        </div>
        <div class="usage-card-body">
            <div v-for="item in usageItems" :key="item.key" class="usage-row">
                <component :is="item.icon" class="usage-icon" />
                <el-text size="small" class="usage-label">
                    {{ $t(item.labelKey) }}
                </el-text>
                <div class="usage-divider" />
                <el-text size="small" class="usage-value">
                    {{ item.value }}
                </el-text>
                <router-link :to="{name: item.route}">
                    <el-button class="wh-15" :icon="TextSearchVariant" link />
                </router-link>
            </div>
            <slot name="additional-usages" />
        </div>
    </div>
</template>
<script setup lang="ts">
    import {ref, computed, onBeforeMount} from "vue";
    import {useRouter} from "vue-router";
    import {useMiscStore} from "override/stores/misc";
    import TextSearchVariant from "vue-material-design-icons/TextSearchVariant.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import PlayOutline from "vue-material-design-icons/PlayOutline.vue";
    import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue";
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import {useI18n} from "vue-i18n";

    const props = defineProps<{
        fetchedUsages?: Record<string, any>;
    }>();
    const emit = defineEmits<{
        (e: "loaded"): void;
    }>();

    const miscStore = useMiscStore();
    const router = useRouter();

    const usages = ref<Record<string, any> | undefined>(undefined);

    onBeforeMount(async () => {
        if (props.fetchedUsages) {
            usages.value = props.fetchedUsages;
        } else {
            usages.value = await miscStore.loadAllUsages();
        }
        emit("loaded");
    });

    function aggregateValues(object: any) {
        return aggregateValuesFromList(object ? Object.values(object) : object);
    }
    function aggregateValuesFromList(list: any) {
        return aggregateValuesFromListWithGetter(list, (item: any) => item);
    }
    function aggregateValuesFromListWithGetter(list: any, valueGetter: (item: any) => any) {
        return aggregateValuesFromListWithGetterAndAggFunction(list, valueGetter, (list: any[]) => list.reduce((a, b) => a + b, 0));
    }
    function aggregateValuesFromListWithGetterAndAggFunction(list: any, valueGetter: (item: any) => any, aggFunction: (list: any[]) => any) {
        if (!list) return 0;
        return aggFunction(list.map(valueGetter));
    }

    const namespaces = computed(() => usages.value?.flows?.namespacesCount ?? 0);
    const flows = computed(() => usages.value?.flows?.count ?? 0);
    const tasks = computed(() => aggregateValues(usages.value?.flows?.taskTypeCount));
    const triggers = computed(() => aggregateValues(usages.value?.flows?.triggerTypeCount));

    const namespaceRoute = computed(() => {
        try {
            router.resolve({name: "namespaces/list"});
            return "namespaces/list";
        } catch {
            return "flows/list";
        }
    });

    const executionsPerDay = computed(() =>
        (usages.value?.executions?.dailyExecutionsCount ?? []).filter((item: any) => item.groupBy === "day")
    );

    const executionsOverTwoDays = computed(() =>
        aggregateValuesFromListWithGetter(executionsPerDay.value, (item: any) => item.duration.count ?? 0)
    );

    const executionsDurationOverTwoDays = computed(() => {
        // Use $moment from global context
        const moment = (window as any).$moment;
        if (!moment) return 0;
        const sum = aggregateValuesFromListWithGetterAndAggFunction(
            executionsPerDay.value,
            (item: any) => item.duration.sum ?? moment.duration("PT0S"),
            (list: any[]) => list.reduce((a, b) => moment.duration(a).add(moment.duration(b)), moment.duration("PT0S"))
        );
        return sum.minutes();
    });

    const {t} = useI18n();

    const usageItems = computed(() => [
        {
            key: "namespaces",
            icon: FolderOpenOutline,
            labelKey: "namespaces",
            value: namespaces.value,
            route: namespaceRoute.value,
        },
        {
            key: "flows",
            icon: FileTreeOutline,
            labelKey: "flows",
            value: flows.value,
            route: "flows/list",
        },
        {
            key: "tasks",
            icon: TimelineTextOutline,
            labelKey: "tasks",
            value: tasks.value,
            route: "flows/list",
        },
        {
            key: "triggers",
            icon: LightningBolt,
            labelKey: "triggers",
            value: triggers.value,
            route: "admin/triggers",
        },
        {
            key: "executions",
            icon: PlayOutline,
            labelKey: "executions",
            value: `${executionsOverTwoDays.value} (${t("last 48 hours")})`,
            route: "executions/list",
        },
        {
            key: "executionsDuration",
            icon: CalendarMonth,
            labelKey: "executions duration (in minutes)",
            value: `${executionsDurationOverTwoDays.value} (${t("last 48 hours")})`,
            route: "executions/list",
        },
    ]);
</script>
<style scoped lang="scss">
.usage-card {
    background-color: transparent;
    // min-height: 432px;
    padding: 1.5rem;
    border: 1px solid var(--ks-border-primary);
    border-radius: 8px;
    box-shadow: 0 2px 4px var(--ks-card-shadow);

    .usage-card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
        margin-bottom: 1.5rem;

        span {
            font-size: 1.25rem;
            font-weight: 600;
        }
    }

    .usage-card-body {
        display: flex;
        flex-direction: column;
        gap: 0.25rem
    }

    .usage-row {
        display: flex;
        align-items: center;
        gap: 1rem;
        min-height: 2rem;
        padding-top: 0.25rem;
        padding-bottom: 0.25rem;

        &:first-child {
            padding-top: 0;
        }
        &:last-child { 
            padding-bottom: 0;
        }

        .usage-icon {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 24px;
            height: 24px;
            flex-shrink: 0;

            :deep(.material-design-icon__svg) {
                font-size: 24px;
                color: var(--ks-content-secondary);
                vertical-align: middle;
            }
        }

        .usage-label {
            font-size: 14px;
            color: var(--ks-content-primary);
            line-height: 1.2;
        }

        .usage-divider {
            flex: 1;
            height: 1px;
            border-top: 1px dashed var(--ks-border-primary);
        }

        .usage-value {
            font-size: 14px;
            line-height: 1.2;
            white-space: nowrap;
        }

        .el-button {
            color: var(--ks-content-primary);
            display: flex;
            align-items: center;
            flex-shrink: 0;
        }
    }
}

:deep(.text-search-variant-icon) {
    color: var(--ks-content-tertiary) !important;
}
</style>