<template>
    <section class="d-inline-flex mb-3 filters">
        <Items :prefix="ITEMS_PREFIX" @search="handleClickedItems" />

        <el-select
            ref="select"
            :model-value="current"
            value-key="label"
            :placeholder="props.placeholder ?? t('filters.label')"
            default-first-option
            allow-create
            filterable
            clearable
            multiple
            placement="bottom"
            :show-arrow="false"
            fit-input-width
            :popper-class="!!props.searchCallback ? 'd-none' : 'filters-select'"
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
            @focus="handleFocus"
            data-test-id="KestraFilter__select"
        >
            <template #label="{value}">
                <Label :option="value" />
            </template>
            <template #empty>
                <span v-if="!isDatePickerShown">{{ emptyLabel }}</span>
                <DateRange
                    v-else
                    automatic
                    @update:model-value="(v) => valueCallback(v, true)"
                />
            </template>
            <template v-if="dropdowns.first.shown">
                <el-option
                    v-for="(option, index) in includedOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                    @click="() => filterCallback(option)"
                    :data-test-id="`KestraFilter__type__${index}`"
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
                    v-for="(comparator, index) in dropdowns.first.value.comparators"
                    :key="comparator.value"
                    :value="comparator"
                    :label="comparator.label"
                    :class="{
                        selected: current.some(
                            (c) => c.comparator === comparator,
                        ),
                    }"
                    @click="() => comparatorCallback(comparator)"
                    :data-test-id="`KestraFilter__comparator__${index}`"
                />
            </template>
            <template v-else-if="dropdowns.third.shown">
                <el-option
                    v-for="(filter, index) in valueOptions"
                    :key="filter.value"
                    :value="filter"
                    :disabled="isOptionDisabled(filter)"
                    :class="{
                        selected: current.some((c) =>
                            c.value.includes(filter.value),
                        ),
                        disabled: isOptionDisabled(filter),
                        'level-3': true
                    }"
                    @click="
                        () => !isOptionDisabled(filter) && valueCallback(filter)
                    "
                    :data-test-id="`KestraFilter__value__${index}`"
                >
                    <template v-if="filter.label.component">
                        <component :is="filter.label.component" v-bind="filter.label.props" />
                    </template>
                    <template v-else>
                        {{ filter.label }}
                    </template>
                </el-option>
            </template>
        </el-select>

        <el-button-group
            class="d-inline-flex"
            :class="{
                'me-1':
                    buttons.refresh.shown ||
                    buttons.settings.shown ||
                    dashboards.shown,
            }"
        >
            <KestraIcon :tooltip="$t('search')" placement="bottom">
                <el-button
                    :icon="Magnify"
                    @click="triggerSearch"
                    class="rounded-0"
                />
            </KestraIcon>
            <Save :disabled="!current.length" :prefix="ITEMS_PREFIX" :current />
        </el-button-group>

        <el-button-group
            v-if="buttons.refresh.shown || buttons.settings.shown"
            class="d-inline-flex ms-1"
            :class="{'me-1': dashboards.shown}"
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
    import {ref, computed, onMounted, watch, nextTick, shallowRef} from "vue";
    import {ElSelect} from "element-plus";

    import {Shown, Buttons, CurrentItem} from "./utils/types";

    import Refresh from "../layout/RefreshButton.vue";
    import Items from "./segments/Items.vue";
    import Label from "./components/Label.vue";
    import Save from "./segments/Save.vue";
    import Settings from "./segments/Settings.vue";
    import Dashboards from "./segments/Dashboards.vue";
    import KestraIcon from "../Kicon.vue";
    import DateRange from "../layout/DateRange.vue";
    import Status from "../../components/Status.vue";

    import {Magnify} from "./utils/icons";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useStore} from "vuex";
    const store = useStore();

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    const emits = defineEmits(["dashboard", "input"]);
    const props = defineProps({
        prefix: {type: String, default: undefined},
        include: {type: Array, default: () => []},
        values: {type: Object, default: undefined},
        decode: {type: Boolean, default: true},
        buttons: {
            type: Object as () => Buttons,
            default: () => ({
                refresh: {shown: false, callback: () => {}},
                settings: {
                    shown: false,
                    charts: {shown: false, value: false, callback: () => {}},
                },
            }),
        },
        dashboards: {
            type: Object as () => Shown,
            default: () => ({shown: false}),
        },
        placeholder: {type: String, default: undefined},
        searchCallback: {type: Function, default: undefined},
    });

    const ITEMS_PREFIX = props.prefix ?? String(route.name);

    import {useFilters} from "./composables/useFilters";
    const {COMPARATORS, OPTIONS} = useFilters(ITEMS_PREFIX);

    const select = ref<InstanceType<typeof ElSelect> | null>(null);
    const updateHoveringIndex = (index) => {
        select.value!.states.hoveringIndex = index >= 0 ? index : 0;
    };
    const emptyLabel = ref(t("filters.empty"));
    const INITIAL_DROPDOWNS = {
        first: {shown: true, value: {}},
        second: {shown: false, index: -1},
        third: {shown: false, index: -1},
    };
    const dropdowns = ref({...INITIAL_DROPDOWNS});
    const closeDropdown = () => (select.value!.dropdownMenuVisible = false);

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

    const getInputValue = () => select.value?.states.inputValue;
    const handleInputChange = (key) => {
        if (props.searchCallback) {
            props.searchCallback(getInputValue());
            return;
        }

        if (key === "Enter") return;

        if (current.value.at(-1)?.label === "user") {
            emits("input", getInputValue());
        }
    };

    const handleClear = () => {
        current.value = [];
        triggerSearch();
    };

    const activeParentFilter = ref<string | null>(null);
    const lastClickedParent = ref<string | null>(null);
    const showSubFilterDropdown = ref(false);
    const valueOptions = ref([]);
    const parentValue = ref<string | null>(null);

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

        // Check if parent filter already exists
        const existingFilterIndex = current.value.findIndex(
            (item) => item.label === option.value.label,
        );
        if (existingFilterIndex !== -1) {
            // If it exists, update current filter index
            dropdowns.value.second = {shown: true, index: existingFilterIndex};
            activeParentFilter.value = option.value.label;
            lastClickedParent.value = option.value.label;
            parentValue.value = option.value.label;
            showSubFilterDropdown.value = true;
            setOptions("filterCallback");
            if (option.comparators.length === 1) {
                comparatorCallback(option.comparators[0]);
            }
        } else {
            // If it doesn't exist, push new filter
            dropdowns.value.first = {shown: false, value: option};
            dropdowns.value.second = {shown: true, index: current.value.length};
            current.value.push(option.value);
            activeParentFilter.value = option.value.label;
            lastClickedParent.value = option.value.label;
            parentValue.value = option.value.label;
            showSubFilterDropdown.value = true;
            setOptions("filterCallback");
            if (option.comparators.length === 1) {
                comparatorCallback(option.comparators[0]);
            }
        }
    };
    const comparatorCallback = (value) => {
        current.value[dropdowns.value.second.index].comparator = value;
        emptyLabel.value = ["labels", "details"].includes(
            current.value[dropdowns.value.second.index].label,
        )
            ? t("filters.format")
            : t("filters.empty");

        dropdowns.value = {
            first: {shown: false, value: {}},
            second: {shown: false, index: -1},
            third: {shown: true, index: current.value.length - 1},
        };

        // Set hover index to the selected comparator for highlighting
        const index = valueOptions.value.findIndex((o) => o.value === value.value);
        updateHoveringIndex(index);
    };

    const dropdownClosedCallback = (visible) => {
        if (!visible) {
            dropdowns.value = {...INITIAL_DROPDOWNS};
            activeParentFilter.value = null;
            lastClickedParent.value = null;
            showSubFilterDropdown.value = false;
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
    const isOptionDisabled = () => {
        if (!activeParentFilter.value) return false;

        const parentIndex = current.value.findIndex(
            (item) => item.label === activeParentFilter.value,
        );
        if (parentIndex === -1) return false;
    };
    const valueCallback = (filter, isDate = false) => {
        // Don't do anything if the option is disabled
        if (isOptionDisabled(filter)) return;
        if (!isDate) {
            const parentIndex = current.value.findIndex(
                (item) => item.label === parentValue.value,
            );
            if (parentIndex !== -1) {
                if (
                    lastClickedParent.value === "Namespace" ||
                    lastClickedParent.value === "namespace" ||
                    lastClickedParent.value === "Log level"
                ) {
                    const values = current.value[parentIndex].value;
                    const index = values.indexOf(filter.value);

                    if (index === -1) {
                        current.value[parentIndex].value = [filter.value]; // Add only the filter.value
                    } else {
                        current.value[parentIndex].value = values.filter(
                            (value, i) => i !== index,
                        ); // remove the clicked item
                    }
                } else {
                    const values = current.value[parentIndex].value;
                    const index = values.indexOf(filter.value);
                    if (index === -1) values.push(filter.value);
                    else values.splice(index, 1);
                }
                const hoverIndex = valueOptions.value.findIndex(
                    (o) => o.value === filter.value,
                );
                updateHoveringIndex(hoverIndex);
            }
        } else {
            const match = current.value.find((v) => v.label === "absolute_date");
            if (match) {
                match.value = [
                    {
                        startDate: filter.startDate,
                        endDate: filter.endDate,
                    },
                ];
            }
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
    const {VALUES} = useValues(ITEMS_PREFIX);

    const isDatePickerShown = computed(() => {
        return current?.value?.some(
            (c) => c.label === "absolute_date" && c.comparator,
        );
    });
    const setOptions = () => {
        if (!lastClickedParent.value) {
            valueOptions.value = [];
            return;
        }
        const parentValue = lastClickedParent.value
            .toLowerCase()
            .replace(/\blog\b/gi, "")
            .trim()
            .replace(/\s+/g, "_");
        switch (parentValue) {
        case "namespace":
            valueOptions.value = namespaceOptions.value;
            break;

        case "state":
            valueOptions.value = (props.values?.state || VALUES.EXECUTION_STATES).
                map(value => {
                    value.label = {
                        "component": shallowRef(Status),
                        "props": {
                            "class": "justify-content-center",
                            "status": value.value,
                            "size": "small"
                        }
                    }
                    return value;
                });
            break;

        case "trigger_state":
            valueOptions.value = VALUES.TRIGGER_STATES;
            break;

        case "scope":
            valueOptions.value = VALUES.SCOPES;
            break;

        case "child":
            valueOptions.value = VALUES.CHILDS;
            break;

        case "level":
            valueOptions.value = VALUES.LEVELS;
            break;

        case "task":
            valueOptions.value = props.values?.task || [];
            break;

        case "metric":
            valueOptions.value = props.values?.metric || [];
            break;

        case "user":
            valueOptions.value = props.values?.user || [];
            break;

        case "type":
            valueOptions.value = VALUES.TYPES;
            break;

        case "service_type":
            valueOptions.value = props.values?.type || [];
            break;

        case "permission":
            valueOptions.value = VALUES.PERMISSIONS;
            break;

        case "action":
            valueOptions.value = VALUES.ACTIONS;
            break;

        case "status":
            valueOptions.value = VALUES.STATUSES;
            break;

        case "aggregation":
            valueOptions.value = VALUES.AGGREGATIONS;
            break;

        case "relative_date":
            valueOptions.value = VALUES.RELATIVE_DATE;
            break;

        case "absolute_date":
            valueOptions.value = [];
            break;

        default:
            valueOptions.value = [];
            break;
        }
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
                closeDropdown();
            }

            triggerEnter.value = false;
        }

        // Clearing the input field after value is being submitted
        select.value!.states.inputValue = "";
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

    import {encodeParams, decodeParams} from "./utils/helpers";

    const triggerSearch = () => {
        if (props.searchCallback) return;
        else router.push({query: encodeParams(current.value, OPTIONS)});
    };

    // Include parameters from URL directly to filter
    onMounted(() => {
        if (props.decode) {
            const decodedParams = decodeParams(route.query, props.include, OPTIONS);
            current.value = decodedParams.map((item: any) => {
                if (item.label === "absolute_date") {
                    return {
                        ...item,
                        value:
                            item.value?.length > 0
                                ? [
                                    {
                                        startDate: item.value[0]?.startDate,
                                        endDate: item.value[0]?.endDate,
                                    },
                                ]
                                : [],
                        comparator: item.comparator,
                    };
                }
                if (item.label === "relative_date") {
                    return {
                        ...item,
                        value: item.value?.length > 0 ? [item.value[0]] : [],
                        comparator: item.comparator,
                    };
                }
                return item;
            });
        }

        const addNamespaceFilter = (namespace) => {
            if (!props.decode || !namespace) return;
            current.value.push({
                label: "namespace",
                value: [namespace],
                comparator: COMPARATORS.STARTS_WITH,
                persistent: true,
            });
        };
        const {name, params} = route;

        if (name === "flows/update") {
            // Single flow page
            addNamespaceFilter(params?.namespace);

            if (props.decode && params.id) {
                current.value.push({
                    label: "flow",
                    value: [`${params.id}`],
                    comparator: COMPARATORS.IS,
                    persistent: true,
                });
            }
        } else if (name === "namespaces/update") {
            // Single namespace page
            addNamespaceFilter(params.id);
        }
    });

    watch(
        () => select.value?.dropdownMenuVisible,
        (visible) => {
            if (!visible) {
                dropdowns.value = {...INITIAL_DROPDOWNS};
                activeParentFilter.value = null;
                lastClickedParent.value = null;
                showSubFilterDropdown.value = false;
            }
        },
    );

    const handleFocus = () => {
        if (current.value.length > 0 && lastClickedParent.value) {
            const existingFilterIndex = current.value.findIndex(
                (item) => item.label === lastClickedParent.value,
            );
            if (existingFilterIndex !== -1) {
                if (!current.value[existingFilterIndex].comparator) {
                    dropdowns.value = {
                        first: {shown: false, value: {}},
                        second: {shown: true, index: existingFilterIndex},
                        third: {shown: false, index: -1},
                    };
                    showSubFilterDropdown.value = true;
                } else {
                    dropdowns.value = {
                        first: {shown: false, value: {}},
                        second: {shown: false, index: -1},
                        third: {shown: true, index: existingFilterIndex},
                    };
                    showSubFilterDropdown.value = false;
                }
                setOptions("handleFocus");
                select.value!.dropdownMenuVisible = true;
            }
        }
    };

    onMounted(() => {
        const el = select.value?.$el as HTMLElement;
        if (el) {
            let isDropdownOpen = false;

            el.addEventListener("click", (event) => {
                const target = event.target as HTMLElement;

                if (isDropdownOpen) {
                    return;
                }
                const selectedItem = target.closest(".el-select__selected-item");
                const selection = target.closest(
                    ".el-select__selection.is-near",
                ) as HTMLElement;
                if (selection && !selectedItem) {
                    event.preventDefault();
                    event.stopPropagation();
                    dropdowns.value = {...INITIAL_DROPDOWNS};
                    activeParentFilter.value = null;
                    lastClickedParent.value = null;
                    showSubFilterDropdown.value = false;
                    setOptions("onClick");
                    isDropdownOpen = true;
                    nextTick(() => {
                        if (!select.value?.dropdownMenuVisible) {
                            select.value?.focus();
                        }
                        isDropdownOpen = false;
                    });
                    return;
                }
                if (selectedItem) {
                    event.preventDefault();
                    event.stopPropagation();
                    const labelElement =
                        selectedItem.querySelector(".text-lowercase");
                    const label = labelElement?.textContent;

                    if (label) {
                        const existingFilterIndex = current.value.findIndex(
                            (item) =>
                                item?.label.toLowerCase() ===
                                label
                                    .toLowerCase()
                                    .replace(/\blog\b/gi, "")
                                    .trim()
                                    .replace(/\s+/g, "_"),
                        );
                        if (existingFilterIndex !== -1) {
                            lastClickedParent.value = label;
                            parentValue.value = label
                                .toLowerCase()
                                .replace(/\blog\b/gi, "")
                                .trim()
                                .replace(/\s+/g, "_"); // Set parentValue when a filter is clicked
                            if (!current.value[existingFilterIndex].comparator) {
                                dropdowns.value = {
                                    first: {shown: false, value: {}},
                                    second: {
                                        shown: true,
                                        index: existingFilterIndex,
                                    },
                                    third: {shown: false, index: -1},
                                };
                                showSubFilterDropdown.value = true;
                            } else {
                                dropdowns.value = {
                                    first: {shown: false, value: {}},
                                    second: {shown: false, index: -1},
                                    third: {
                                        shown: true,
                                        index: existingFilterIndex,
                                    },
                                };
                                showSubFilterDropdown.value = false;
                            }
                            setOptions("onClickSelection");
                            isDropdownOpen = true;
                            nextTick(() => {
                                if (!select.value?.dropdownMenuVisible) {
                                    select.value?.focus();
                                }
                                isDropdownOpen = false;
                            });
                        }
                    }
                }
            });
        }
    });
</script>

<style lang="scss">
@import "./styles/filter.scss";

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
            max-width: calc(100% - $included - $refresh - $settins + 0.25rem);
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
            min-width: $dashboards;
            max-width: calc(100% - $included - $dashboards);
        }
    }

    & .el-select__placeholder {
        color: $filters-gray-700;
    }

    & .el-select__wrapper {
        border-radius: 0;
        box-shadow:
            0 -1px 0 0 $filters-border-color inset,
            0 1px 0 0 $filters-border-color inset;

        & .el-tag {
            background: $filters-border-color !important;
            color: $filters-gray-900;

            & .el-tag__close {
                color: $filters-gray-900;
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
        width: auto !important;
        max-width: 300px;

        &:has(.el-select-dropdown__empty) {
            width: auto !important;
        }
    }

    .el-select-dropdown__empty span {
        padding: 0 1rem;
        color: var(--ks-content-inactive);
    }

    & .el-date-editor.el-input__wrapper {
        background-color: initial;
        box-shadow: none;
    }

    & .el-select-dropdown__item .material-design-icon {
        bottom: -0.15rem;
    }

    .el-select-dropdown__item {
        &.disabled {
            opacity: 0.6;

            &:hover {
                cursor: not-allowed;
                background-color: transparent;
            }
        }
    }
}
</style>
