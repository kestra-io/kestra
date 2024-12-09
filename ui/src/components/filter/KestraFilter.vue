<template>
    <section class="d-inline-flex pb-3 filters">
        <History :prefix @search="handleHistoryItems" />

        <el-select
            ref="select"
            :model-value="current"
            value-key="label"
            :placeholder="t('filters.label')"
            allow-create
            default-first-option
            filterable
            multiple
            placement="bottom"
            :show-arrow="false"
            fit-input-width
            popper-class="filters-select"
            :class="{settings: settings.shown, refresh: refresh.shown}"
            @change="(value) => changeCallback(value)"
            @keyup.enter="() => handleEnterKey(select?.hoverOption?.value)"
            @remove-tag="(item) => removeItem(item)"
            @visible-change="(visible) => dropdownClosedCallback(visible)"
        >
            <template #label="{value}">
                <Label :option="value" />
            </template>
            <template #empty>
                <span v-if="!isDatePickerShown">{{ emptyLabel }}</span>
                <DateRange
                    v-else
                    @update:model-value="(v) => valueCallback(v, true)"
                />
            </template>
            <template v-if="dropdowns.first.shown">
                <el-option
                    v-for="option in includedOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                    @click="() => filterCallback(option)"
                >
                    <component
                        v-if="option.icon"
                        :is="option.icon"
                        class="me-2"
                    />
                    <span>{{ option.label }}</span>
                </el-option>
            </template>
            <template v-else-if="dropdowns.second.shown">
                <el-option
                    v-for="comparator in dropdowns.first.value.comparators"
                    :key="comparator.value"
                    :value="comparator"
                    :label="comparator.label"
                    :class="{
                        selected: current.some(
                            (c) => c.comparator === comparator,
                        ),
                    }"
                    @click="() => comparatorCallback(comparator)"
                />
            </template>
            <template v-else-if="dropdowns.third.shown">
                <el-option
                    v-for="filter in valueOptions"
                    :key="filter.value"
                    :value="filter"
                    :label="filter.label"
                    :class="{
                        selected: current.some((c) =>
                            c.value.includes(filter.value),
                        ),
                    }"
                    @click="() => valueCallback(filter)"
                />
            </template>
        </el-select>

        <el-button-group class="d-inline-flex">
            <KestraIcon :tooltip="$t('search')" placement="bottom">
                <el-button :icon="Magnify" @click="triggerSearch" />
            </KestraIcon>
            <Save :disabled="!current.length" :prefix :current />
            <Refresh v-if="refresh.shown" @refresh="refresh.callback" />
            <Settings v-if="settings.shown" :settings />
        </el-button-group>

        <Dashboards
            v-if="dashboards.shown"
            @dashboard="(value) => emits('dashboard', value)"
        />
    </section>
</template>

<script setup lang="ts">
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
    import Label from "./components/Label.vue";
    import Save from "./components/Save.vue";
    import Settings from "./components/Settings.vue";
    import Dashboards from "./components/Dashboards.vue";
    import KestraIcon from "../Kicon.vue";

    import Magnify from "vue-material-design-icons/Magnify.vue";

    import State from "../../utils/state.js";
    import DateRange from "../layout/DateRange.vue";

    const emits = defineEmits(["dashboard"]);
    const props = defineProps({
        prefix: {type: String, required: true},
        include: {type: Array, required: true},
        refresh: {
            type: Object,
            default: () => ({shown: false, callback: () => {}}),
        },
        settings: {
            type: Object,
            default: () => ({
                shown: false,
                charts: {shown: false, value: false, callback: () => {}},
            }),
        },
        dashboards: {
            type: Object,
            default: () => ({shown: false}),
        },
    });

    import {useFilters, compare} from "./useFilters.js";
    const {
        getRecentItems,
        setRecentItems,
        COMPARATORS,
        OPTIONS,
        encodeParams,
        decodeParams,
    } = useFilters(props.prefix);

    const select = ref<InstanceType<typeof ElSelect> | null>(null);
    const updateHoveringIndex = (index) => {
        select.value.states.hoveringIndex = index >= 0 ? index : 0;
    };
    const emptyLabel = ref(t("filters.empty"));
    const INITIAL_DROPDOWNS = {
        first: {shown: true, value: {}},
        second: {shown: false, index: -1},
        third: {shown: false, index: -1},
    };
    const dropdowns = ref({...INITIAL_DROPDOWNS});
    const closeDropdown = () => (select.value.dropdownMenuVisible = false);

    const triggerEnter = ref(true);
    const handleEnterKey = (option) => {
        if (!option) return;

        if (!triggerEnter.value) {
            triggerEnter.value = true;
            return;
        }

        if (dropdowns.value.first.shown) {
            const value = includedOptions.value.filter((o) => {
                let comparator = o.key;

                if (o.key === "timeRange") comparator = "relative_date";
                if (o.key === "date") comparator = "absolute_date";

                return comparator === option.label;
            })[0];

            filterCallback(value);
        } else if (dropdowns.value.second.shown) {
            comparatorCallback(option);
        } else if (dropdowns.value.third.shown) {
            valueCallback(option);
        }
    };

    const filterCallback = (option) => {
        if (!option.value) {
            triggerEnter.value = false;
            return;
        }

        option.value = {
            label: option.value?.label ?? "Unknown",
            comparator: undefined,
            value: [],
        };
        dropdowns.value.first = {shown: false, value: option};
        dropdowns.value.second = {shown: true, index: current.value.length};

        current.value.push(option.value);

        // If only one comparator option, automate selection of it
        if (option.comparators.length === 1) {
            comparatorCallback(option.comparators[0]);
        }
    };
    const comparatorCallback = (value) => {
        current.value[dropdowns.value.second.index].comparator = value;
        emptyLabel.value =
            current.value[dropdowns.value.second.index].label === "labels"
                ? t("filters.labels.placeholder")
                : t("filters.empty");

        dropdowns.value.first = {shown: false, value: {}};
        dropdowns.value.second = {shown: false, index: -1};
        dropdowns.value.third = {shown: true, index: current.value.length - 1};

        // Set hover index to the selected comparator for highlighting
        const index = valueOptions.value.findIndex((o) => o.value === value.value);
        updateHoveringIndex(index);
    };
    const dropdownClosedCallback = (visible) => {
        if (!visible) {
            dropdowns.value = {...INITIAL_DROPDOWNS};

            // If last filter item selection was not completed, remove it from array
            if (current.value?.at(-1)?.value?.length === 0) current.value.pop();
        } else {
            // Highlight all selected items by setting hoveringIndex to match the first selected item
            const index = valueOptions.value.findIndex((o) => {
                return current.value.some((c) => c.value.includes(o.value));
            });
            updateHoveringIndex(index);
        }
    };
    const valueCallback = (filter, isDate = false) => {
        if (!isDate) {
            const values = current.value[dropdowns.value.third.index].value;
            const index = values.indexOf(filter.value);

            if (index === -1) values.push(filter.value);
            else values.splice(index, 1);

            // Update the hover index for better UX
            const hoverIndex = valueOptions.value.findIndex(
                (o) => o.value === filter.value,
            );
            updateHoveringIndex(hoverIndex);
        } else {
            const match = current.value.find((v) => v.label === "absolute_date");
            if (match) match.value = [filter];
        }

        if (!current.value[dropdowns.value.third.index].comparator?.multiple) {
            // If selection is not multiple, close the dropdown
            closeDropdown();
        }

        triggerSearch();
    };

    import action from "../../models/action.js";
    import permission from "../../models/permission.js";

    const user = computed(() => store.state.auth.user);

    const namespaceOptions = ref([]);
    const parseNamespaces = (namespaces) => {
        const result = [];

        namespaces.forEach((namespace) => {
            let current = "";
            namespace.split(".").forEach((part) => {
                current = current ? `${current}.${part}` : part;
                result.push({label: current, value: current});
            });
        });

        return [...new Map(result.map((item) => [item.value, item])).values()];
    };
    const loadNamespaces = () => {
        const p = permission.NAMESPACE;
        const a = action.READ;

        if (user.value && user.value.hasAnyActionOnAnyNamespace(p, a)) {
            const dataType = "flow";
            store
                .dispatch("namespace/loadNamespacesForDatatype", {dataType})
                .then((r) => (namespaceOptions.value = parseNamespaces(r)));
        }
    };

    // Load all namespaces only if that filter is included
    if (props.include.includes("namespace")) loadNamespaces();

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

    const childOptions = [
        {
            label: t("trigger filter.options.ALL"),
            value: "ALL",
        },
        {
            label: t("trigger filter.options.CHILD"),
            value: "CHILD",
        },
        {
            label: t("trigger filter.options.MAIN"),
            value: "MAIN",
        },
    ];

    const levelOptions = [
        {label: "TRACE", value: "TRACE"},
        {label: "DEBUG", value: "DEBUG"},
        {label: "INFO", value: "INFO"},
        {label: "WARN", value: "WARN"},
        {label: "ERROR", value: "ERROR"},
    ];

    const relativeDateOptions = [
        {label: t("datepicker.last5minutes"), value: "PT5M"},
        {label: t("datepicker.last15minutes"), value: "PT15M"},
        {label: t("datepicker.last1hour"), value: "PT1H"},
        {label: t("datepicker.last12hours"), value: "PT12H"},
        {label: t("datepicker.last24hours"), value: "PT24H"},
        {label: t("datepicker.last48hours"), value: "PT48H"},
        {label: t("datepicker.last7days"), value: "PT168H"},
        {label: t("datepicker.last30days"), value: "PT720H"},
        {label: t("datepicker.last365days"), value: "PT8760H"},
    ];

    const isDatePickerShown = computed(() => {
        const c = current?.value?.at(-1);
        return c?.label === "absolute_date" && c.comparator;
    });

    const valueOptions = computed(() => {
        const type = current.value.at(-1)?.label;

        switch (type) {
        case "namespace":
            return namespaceOptions.value;

        case "scope":
            return scopeOptions;

        case "state":
            return State.arrayAllStates().map((s) => ({
                label: s.name,
                value: s.name,
            }));

        case "child":
            return childOptions;

        case "level":
            return levelOptions;

        case "relative_date":
            return relativeDateOptions;

        case "absolute_date":
            return [];

        default:
            return [];
        }
    });

    type CurrentItem = {
        label: string;
        value: string[];
        comparator?: Record<string, any>;
        persistent?: boolean;
    };
    const current = ref<CurrentItem[]>([]);
    const includedOptions = computed(() => {
        const dates = ["relative_date", "absolute_date"];

        const found = current.value?.find((v) => dates.includes(v?.label));
        const exclude = found ? dates.find((date) => date !== found.label) : null;

        return OPTIONS.filter((o) => {
            const label = o.value?.label;
            return props.include.includes(label) && label !== exclude;
        });
    });

    const changeCallback = (v) => {
        if (!Array.isArray(v) || !v.length) return;

        if (typeof v.at(-1) === "string") {
            if (v.at(-2)?.label === "labels") {
                // Adding labels to proper filter
                v.at(-2).value?.push(v.at(-1));
                closeDropdown();
                triggerSearch();
            } else {
                // Adding text search string
                const label = t("filters.options.text");
                const index = current.value.findIndex((i) => i.label === label);

                if (index !== -1) current.value[index].value = [v.at(-1)];
                else current.value.push({label, value: [v.at(-1)]});

                triggerSearch();
            }

            triggerEnter.value = false;
        }

        // Clearing the input field after value is being submitted
        select.value.states.inputValue = "";
    };

    const removeItem = (value) => {
        current.value = current.value.filter(
            (item) => JSON.stringify(item) !== JSON.stringify(value),
        );

        triggerSearch();
    };

    const handleHistoryItems = (value) => {
        if (value) current.value = value;
        select.value?.focus();
    };

    const triggerSearch = () => {
        if (current.value.length) {
            const r = getRecentItems().filter((i) =>
                compare(i.value, current.value),
            );
            setRecentItems([...r, {value: current.value}]);
        }

        router.push({query: encodeParams(current.value)});
    };

    // Include parameters from URL directly to filter
    current.value = decodeParams(route.query, props.include);

    const addNamespaceFilter = (namespace) => {
        if (!namespace) return;
        current.value.push({
            label: "namespace",
            value: [namespace],
            comparator: COMPARATORS.STARTS_WITH,
            persistent: true,
        });
    };

    const {name, params} = route;

    if (name === "flows/update") addNamespaceFilter(params?.namespace);
    else if (name === "namespaces/update") addNamespaceFilter(params.id);
</script>

<style lang="scss">
@mixin width-available {
    width: -moz-available;
    width: -webkit-fill-available;
    // https://caniuse.com/?search=fill-available
    width: fill-available;
}

.filters {
    @include width-available;

    & .el-select {
        max-width: calc(100% - 237px);

        &.settings {
            max-width: calc(100% - 285px);
        }

        &:not(.refresh) {
            max-width: calc(100% - 189px);
        }
    }

    & .el-select__placeholder {
        color: var(--bs-gray-700);
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
        .el-button {
            border-radius: 0;
        }

        span.kicon:last-child .el-button,
        > button.el-button:last-child {
            border-top-right-radius: var(--bs-border-radius);
            border-bottom-right-radius: var(--bs-border-radius);
        }
    }
}

.el-button-group .el-button--primary:last-child {
    border-left: none;
}

.el-button-group > .el-dropdown > .el-button {
    border-left-color: transparent;
}

.filters-select {
    & .el-select-dropdown {
        width: 300px !important;

        &:has(.el-select-dropdown__empty) {
            width: 500px !important;
        }
    }

    & .el-date-editor.el-input__wrapper {
        background-color: initial;
        box-shadow: none;
    }

    & .el-select-dropdown__item .material-design-icon {
        bottom: -0.15rem;
    }
}
</style>
