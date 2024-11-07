<template>
    <section class="d-inline-flex pb-3 global-filters">
        <History :prefix @search="handleHistoryItems" />

        <el-select
            ref="select"
            :model-value="current"
            :placeholder="t('filters.label')"
            allow-create
            filterable
            multiple
            @change="(value) => changeCallback(value)"
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
                    :key="comparator.value"
                    :value="comparator"
                    :label="comparator.label"
                    @click="() => comparatorCallback(comparator)"
                />
            </template>
            <template v-else-if="dropdowns.third.shown">
                <el-option
                    v-for="filter in valueOptions"
                    :key="filter.value"
                    :value="filter"
                    :label="filter.label"
                    @click="() => valueCallback(filter.value)"
                />
            </template>
        </el-select>

        <el-button-group class="d-inline-flex">
            <el-button :icon="Magnify" @click="triggerSearch" />
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
// TODO: Allow selection of values on Enter key
// TODO: Improve highlighting of already selected items in second and third dropdowns
// TODO: Add remaining filter options for Executions context first
// TODO: Add button to handle the table options (show charts, selection of visible columns)
// TODO: Submit search on Enter key press
// TODO: On mounted, make sure that existing route parameters are loaded into the filters

    import {ref, computed} from "vue";
    import {ElSelect} from "element-plus";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

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

    import {
        formatLabel,
        encodeParams,
        decodeParams,
        useFilters,
    } from "./filters.js";
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
        const values = current.value[dropdowns.value.third.index].value;
        const index = values.indexOf(value);

        if (index === -1) values.push(value);
        else values.splice(index, 1);

        if (!current.value[dropdowns.value.third.index].comparator?.multiple) {
            // If selection is not multiple, close the dropdown
            select.value.dropdownMenuVisible = false;
        }
    };

    // TODO: Fetch namespaces if not present in store
    const namespaces = computed(() => store.state.namespace.datatypeNamespaces);
    const namespaceOptions = () => {
        let result = new Set();

        namespaces.value.forEach((namespace) => {
            let parts = namespace.split(".");
            let current = "";

            parts.forEach((part) => {
                current = current ? `${current}.${part}` : part;
                result.add({label: current, value: current});
            });
        });

        return Array.from(result);
    };

    const scopeOptions = [
        {
            label: t("scope_filter.user", {label: props.prefix}),
            value: "USER",
        },
        {
            label: t("scope_filter.system", {label: props.prefix}),
            value: "SYSTEM",
        },
    ];

    const valueOptions = computed(() => {
        const type = current.value.at(-1)?.label;

        switch (type) {
        case "namespace":
            return namespaceOptions();

        case "scope":
            return scopeOptions;

        case "state":
            return State.arrayAllStates().map((s) => ({
                label: s.name,
                value: s.name,
            }));

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

    const changeCallback = (v) => {
        // Clearing the input field after value is being submitted
        select.value.states.inputValue = "";

        // Adding of text search string
        if (Array.isArray(v) && typeof v.at(-1) === "string") {
            const label = t("filters.options.text");
            const index = current.value.findIndex((i) => i.label === label);

            if (index !== -1) current.value[index].value = [v.at(-1)];
            else current.value.push({label, value: [v.at(-1)]});
        }
    };

    const removeItem = (value) => {
        current.value = current.value.filter(
            (item) => JSON.stringify(item) !== JSON.stringify(value),
        );
    };

    const handleHistoryItems = (value) => {
        if (value) current.value = value;
        select.value?.focus();
    };

    const triggerSearch = () => {
        setRecentItems([...getRecentItems(), {value: current.value}]);
        router.push({query: encodeParams(current.value)});
    };

    // Include paramters from URL directly to filter
    current.value = decodeParams(route.query, props.include);
</script>

<style lang="scss">
.global-filters {
    width: -webkit-fill-available;

    & .el-select {
        // Combined width of buttons on the sides of select
        width: calc(100% - 237px);
    }

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

    & .el-select__selection {
        flex-wrap: nowrap;
        overflow-x: auto;

        &::-webkit-scrollbar {
            height: 0px;
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
