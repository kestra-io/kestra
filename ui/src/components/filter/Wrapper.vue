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
            popper-class="global-filters-select"
            @change="(value) => changeCallback(value)"
            @remove-tag="(item) => removeItem(item)"
            @visible-change="(visible) => dropdownClosedCallback(visible)"
        >
            <template #label="{value}">
                <span>{{ formatLabel(value) }} </span>
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
                    @click="() => valueCallback(filter)"
                />
            </template>
        </el-select>

        <el-button-group class="d-inline-flex">
            <el-button :icon="Magnify" @click="triggerSearch" />
            <Save :disabled="!current.length" :prefix :current />
            <Refresh v-if="refresh.shown" @refresh="refresh.callback" />
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

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import Refresh from "../layout/RefreshButton.vue";

    import History from "./components/history/History.vue";
    import Save from "./components/Save.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    import State from "../../utils/state.js";
    import DateRange from "../layout/DateRange.vue";

    const props = defineProps({
        prefix: {type: String, required: true},
        include: {type: Array, required: true},
        refresh: {
            type: Object,
            default: () => ({shown: false, callback: () => {}}),
        },
    });

    import {formatLabel, useFilters} from "./filters.js";
    const {getRecentItems, setRecentItems, OPTIONS, encodeParams, decodeParams} =
        useFilters(props.prefix);

    const select = ref<InstanceType<typeof ElSelect> | null>(null);
    const emptyLabel = ref(t("filters.empty"));
    const INITIAL_DROPDOWNS = {
        first: {shown: true, value: {}},
        second: {shown: false, index: -1},
        third: {shown: false, index: -1},
    };
    const dropdowns = ref({...INITIAL_DROPDOWNS});
    const closeDropdown = () => (select.value.dropdownMenuVisible = false);
    const filterCallback = (option) => {
        option.value = {
            label: option.value?.label ?? "Unknown",
            comparator: undefined,
            value: [],
        };
        dropdowns.value.first = {shown: false, value: option};
        dropdowns.value.second = {shown: true, index: current.value.length};

        current.value.push(option.value);
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
    };
    const dropdownClosedCallback = (visible) => {
        if (!visible) {
            dropdowns.value = {...INITIAL_DROPDOWNS};

            // If last filter item selection was not completed, remove it from array
            if (current.value?.at(-1)?.value?.length === 0) current.value.pop();
        }
    };
    const valueCallback = (filter, isDate) => {
        if (!isDate) {
            const values = current.value[dropdowns.value.third.index].value;
            const index = values.indexOf(filter.value);

            if (index === -1) values.push(filter.value);
            else values.splice(index, 1);
        } else {
            const match = current.value.find((v) => v.label === "absolute_date");
            if (match) match.value = [filter];
        }

        if (!current.value[dropdowns.value.third.index].comparator?.multiple) {
            // If selection is not multiple, close the dropdown
            closeDropdown();
        }
    };

    import action from "../../models/action";
    import permission from "../../models/permission";

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
        value: Array<any>;
        comparator?: string;
    };
    const current = ref<CurrentItem[]>([]);
    const includedOptions = computed(() => {
        return OPTIONS.filter((o) => props.include.includes(o.value?.label));
    });

    const changeCallback = (v) => {
        if (!Array.isArray(v) || !v.length) return;

        if (typeof v.at(-1) === "string") {
            if (v.at(-2)?.label === "labels") {
                // Adding labels to proper filter
                v.at(-2).value?.push(v.at(-1));
                closeDropdown();
            } else {
                // Adding text search string
                const label = t("filters.options.text");
                const index = current.value.findIndex((i) => i.label === label);

                if (index !== -1) current.value[index].value = [v.at(-1)];
                else current.value.push({label, value: [v.at(-1)]});
            }
        }

        // Clearing the input field after value is being submitted
        select.value.states.inputValue = "";
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
        if (current.value.length) {
            const r = getRecentItems().filter((i) => i.value !== current.value);
            setRecentItems([...r, {value: current.value}]);
        }

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

.global-filters-select {
    & .el-date-editor.el-input__wrapper {
        background-color: initial;
        box-shadow: none;
    }
}
</style>
