<template>
    <section class="d-inline-flex w-100 filters">
        <History :prefix @search="handleHistoryItems" />

        <el-select
            ref="select"
            :model-value="current"
            :placeholder="t('filters.label')"
            allow-create
            filterable
            multiple
            @change="(value) => textSearch(value)"
            @remove-tag="(item) => removeItem(item)"
            @visible-change="(visible) => dropdownClosedCallback(visible)"
        >
            <template #label="{value}">
                <span>{{ formatLabel(value) }} </span>
            </template>
            <template v-if="dropdowns.first.shown">
                <el-option
                    v-for="option in includedOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                    @click="() => filterCallback(option)"
                />
            </template>
            <template v-else-if="dropdowns.second.shown">
                <el-option
                    v-for="comparator in dropdowns.first.value.comparators"
                    :key="comparator"
                    :value="comparator"
                    :label="comparator"
                    @click="() => comparatorCallback(comparator)"
                    @keydown.enter="() => comparatorCallback(comparator)"
                />
            </template>
            <template v-else-if="dropdowns.third.shown">
                <el-option
                    v-for="filter in valueOptions"
                    :key="filter"
                    :value="filter"
                    :label="filter"
                    @click="() => valueCallback(filter)"
                />
            </template>
        </el-select>

        <el-button-group class="d-inline-flex">
            <el-button @click="triggerSearch" :icon="Magnify" />
            <Save :disabled="!current.length" :prefix :current />
            <Refresh
                v-if="refresh.shown"
                :can-auto-refresh="refresh.canAutoRefresh"
                @refresh="refresh.callback"
            />
        </el-button-group>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import {ElSelect} from "element-plus";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    import Refresh from "../layout/RefreshButton.vue";

    import History from "./components/history/History.vue";
    import Save from "./components/Save.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    import State from "../../utils/state.js";

    const props = defineProps({
        prefix: {type: String, required: true},
        include: {type: Array, required: true},
        refresh: {
            type: Object,
            default: () => ({
                shown: false,
                canAutoRefresh: true,
                callback: () => {},
            }),
        },
    });

    import {formatLabel, useFilters} from "./filters.js";
    const {getRecentItems, setRecentItems, OPTIONS} = useFilters(props.prefix);

    const select = ref<InstanceType<typeof ElSelect> | null>(null);
    const INITIAL_DROPDOWNS = {
        first: {shown: true, value: {}},
        second: {shown: true, index: -1},
        third: {shown: true, index: -1},
    };
    const dropdowns = ref({...INITIAL_DROPDOWNS});
    const filterCallback = (value) => {
        dropdowns.value.first = {shown: false, value};
        dropdowns.value.second = {shown: true, index: current.value.length};

        current.value.push(value.value);
    };
    const comparatorCallback = (value) => {
        current.value[dropdowns.value.second.index].comparator = value;

        dropdowns.value.second = {shown: false, index: -1};
        dropdowns.value.third = {shown: true, index: current.value.length - 1};
    };
    const dropdownClosedCallback = (visible) => {
        if (!visible) dropdowns.value = {...INITIAL_DROPDOWNS};
    };
    const valueCallback = (value) => {
        current.value[dropdowns.value.third.index].value.push(value);
    };

    // TODO: Fetch namespaces if not present in store
    const namespaces = computed(() => store.state.namespace.datatypeNamespaces);
    const namespaceOptions = () => {
        let result = new Set();

        namespaces.value.forEach((namespace) => {
            let parts = namespace.split(".");
            let current = "";

            parts.forEach((part) => {
                current = current ? current + "." + part : part;
                result.add(current);
            });
        });

        return Array.from(result);
    };

    const scopeOptions = [
        t("scope_filter.user", {label: props.prefix}),
        t("scope_filter.system", {label: props.prefix}),
    ];

    const valueOptions = computed(() => {
        const type = current.value.at(-1)?.label;

        switch (type) {
        case "namespace":
            return namespaceOptions();

        case "scope":
            return scopeOptions;

        case "state":
            return State.arrayAllStates().map((s) => s.name);

        default:
            return [];
        }
    });

    type CurrentItem = {
        label: string;
        value: Array<any>;
        comparator?: string;
    };
    const current = ref<CurrentItem[]>([]);
    const includedOptions = computed(() => {
        return OPTIONS.filter((o) => props.include.includes(o.value?.label));
    });

    const textSearch = (v) => {
        if (Array.isArray(v) && typeof v.at(-1) === "string") {
            const label = t("filters.options.text");
            const index = current.value.findIndex((i) => i.label === label);

            // TODO: Make sure it is only done for text
            if (index !== -1) current.value[index].value = [v.at(-1)];
            else current.value.push({label, value: [v.at(-1)]});
        }
    };

    const removeItem = (value) => {
        current.value = current.value.filter(
            (item) => JSON.stringify(item) !== JSON.stringify(value),
        );
    };

    const triggerSearch = () => {
        setRecentItems([...getRecentItems(), {value: current.value}]);
    };

    const handleHistoryItems = (value) => {
        if (value) current.value = value;
        select.value?.focus();
    };
</script>

<style lang="scss">
.filters {
    & .el-select__wrapper {
        border-radius: 0;
        box-shadow:
            0 -1px 0 0 var(--el-border-color) inset,
            0 1px 0 0 var(--el-border-color) inset;

        & .el-tag {
            background: var(--bs-border-color) !important;
            color: var(--bs-gray-900);

            & .el-tag__close {
                color: var(--bs-gray-900);
            }
        }
    }

    & .el-button-group {
        > .el-button {
            border-radius: 0;
        }

        > .el-button:last-child {
            border-top-right-radius: var(--bs-border-radius);
            border-bottom-right-radius: var(--bs-border-radius);
        }
    }
}

.el-button-group .el-button--primary:last-child {
    border-left: none;
}
</style>
