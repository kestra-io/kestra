<template>
    <section class="d-inline-flex mb-3 filters">
        <Items :prefix @search="handleClickedItems" />

        <el-select
            ref="select"
            :model-value="current"
            value-key="label"
            :placeholder="t('filters.label')"
            default-first-option
            allow-create
            filterable
            clearable
            multiple
            placement="bottom"
            :show-arrow="false"
            fit-input-width
            popper-class="filters-select"
            @change="(value) => changeCallback(value)"
            @keyup="(e) => handleInputChange(e.key)"
            @keyup.enter="() => handleEnterKey(select?.hoverOption?.value)"
            @remove-tag="(item) => removeItem(item)"
            @visible-change="(visible) => dropdownClosedCallback(visible)"
            @clear="handleClear"
            :class="{
                refresh: buttons.refresh.shown,
                settings: buttons.settings.shown,
                dashboards: dashboards.shown,
            }"
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

        <el-button-group class="d-inline-flex me-1">
            <KestraIcon :tooltip="$t('search')" placement="bottom">
                <el-button
                    :icon="Magnify"
                    @click="triggerSearch"
                    class="rounded-0"
                />
            </KestraIcon>
            <Save :disabled="!current.length" :prefix :current />
        </el-button-group>

        <el-button-group
            v-if="buttons.refresh.shown || buttons.settings.shown"
            class="d-inline-flex mx-1"
        >
            <Refresh
                v-if="buttons.refresh.shown"
                @refresh="buttons.refresh.callback"
            />
            <Settings
                v-if="buttons.settings.shown"
                :settings="buttons.settings"
                :refresh="buttons.refresh.shown"
            />
        </el-button-group>

        <Dashboards
            v-if="dashboards.shown"
            @dashboard="(value) => emits('dashboard', value)"
            class="ms-1"
        />
    </section>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import {ElSelect} from "element-plus";

    import Refresh from "../layout/RefreshButton.vue";
    import Items from "./segments/Items.vue";
    import Label from "./components/Label.vue";
    import Save from "./segments/Save.vue";
    import Settings from "./segments/Settings.vue";
    import Dashboards from "./segments/Dashboards.vue";
    import KestraIcon from "../Kicon.vue";
    import DateRange from "../layout/DateRange.vue";

    import {Magnify} from "./utils/icons.js";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    const emits = defineEmits(["dashboard", "input"]);
    const props = defineProps({
        prefix: {type: String, required: true},
        include: {type: Array, required: true},
        values: {type: Object, default: undefined},
        buttons: {
            type: Object,
            default: () => ({
                refresh: {shown: false, callback: () => {}},
                settings: {
                    shown: false,
                    charts: {shown: false, value: false, callback: () => {}},
                },
            }),
        },
        dashboards: {
            type: Object,
            default: () => ({shown: false}),
        },
    });

    import {useFilters} from "./composables/useFilters.js";
    const {COMPARATORS, OPTIONS} = useFilters(props.prefix);

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

    const handleInputChange = (key) => {
        if (key === "Enter") return;

        if (current.value.at(-1)?.label === "user") {
            emits("input", select.value.states.inputValue);
        }
    };

    const handleClear = () => {
        current.value = [];
        triggerSearch();
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
        emptyLabel.value = ["labels", "details"].includes(
            current.value[dropdowns.value.second.index].label,
        )
            ? t("filters.format")
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
            const currentFilter = current.value[dropdowns.value.third.index];
            const label = currentFilter.label;
            const existingIndex = current.value.findIndex((i) => i.label === label);

            if (
                existingIndex !== -1 &&
                existingIndex !== dropdowns.value.third.index
            ) {
                if (!currentFilter.comparator?.multiple) {
                    current.value[existingIndex].value = [filter.value];
                    current.value.splice(dropdowns.value.third.index, 1);
                    dropdowns.value.third.index = existingIndex;
                } else {
                    current.value[existingIndex].value.push(filter.value);
                }
            } else {
                const values = currentFilter.value;
                const index = values.indexOf(filter.value);
                if (index === -1) values.push(filter.value);
                else values.splice(index, 1);
            }

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

    import {useValues} from "./composables/useValues";
    const {VALUES} = useValues(props.prefix);

    const isDatePickerShown = computed(() => {
        const c = current?.value?.at(-1);
        return c?.label === "absolute_date" && c.comparator;
    });

    const valueOptions = computed(() => {
        const type = current.value.at(-1)?.label;

        switch (type) {
        case "namespace":
            return namespaceOptions.value;

        case "state":
            return VALUES.EXECUTION_STATE;

        case "trigger_state":
            return VALUES.TRIGGER_STATE;

        case "scope":
            return VALUES.SCOPE;

        case "permission":
            return VALUES.PERMISSIONS;

        case "action":
            return VALUES.ACTIONS;

        case "child":
            return VALUES.CHILD;

        case "level":
            return VALUES.LEVEL;

        case "relative_date":
            return VALUES.RELATIVE_DATE;

        case "task":
            return props.values?.task || [];

        case "metric":
            return props.values?.metric || [];

        case "user":
            return props.values?.user || [];

        case "aggregation":
            return VALUES.AGGREGATION;

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
            if (["labels", "details"].includes(v.at(-2)?.label)) {
                // Adding labels to proper filter
                const existingIndex = current.value.findIndex(
                    (i) => i.label === "labels",
                );
                if (existingIndex !== -1) {
                    current.value[existingIndex].value.push(v.at(-1));
                } else {
                    current.value.push({label: "labels", value: [v.at(-1)]});
                }
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

    const handleClickedItems = (value) => {
        if (value) current.value = value;
        select.value?.focus();
    };

    import {encodeParams, decodeParams} from "./utils/helpers.js";

    const triggerSearch = () => {
        router.push({query: encodeParams(current.value, OPTIONS)});
    };

    // Include parameters from URL directly to filter
    current.value = decodeParams(route.query, props.include, OPTIONS);

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

$included: 144px;

$refresh: 104px;
$settins: 52px;
$dashboards: 52px;

.filters {
    @include width-available;

    & .el-select {
        width: 100%;

        &.refresh.settings.dashboards {
            max-width: calc(
                100% - $included - $refresh - $settins - $dashboards
            );
        }

        &.refresh.settings {
            max-width: calc(100% - $included - $refresh - $settins);
        }

        &.settings.dashboards {
            max-width: calc(100% - $included - $settins - $dashboards);
        }

        &.refresh.dashboards {
            max-width: calc(100% - $included - $refresh - $dashboards);
        }

        &.refresh {
            max-width: calc(100% - $included - $refresh);
        }

        &.settings {
            max-width: calc(100% - $included - $settins);
        }

        &.dashboards {
            max-width: calc(100% - $included - $dashboards);
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
