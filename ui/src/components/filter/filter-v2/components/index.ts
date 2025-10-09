import FilterComparatorSelect from "../layout/FilterComparatorSelect.vue";
import FilterDateTime from "../layout/FilterDateTime.vue";
import FilterFooter from "../layout/FilterFooter.vue";
import FilterHeader from "../layout/FilterHeader.vue";
import FilterMultiSelect from "../layout/FilterMultiSelect.vue";
import FilterSelect from "../layout/FilterSelect.vue";
import FilterText from "../layout/FilterText.vue";

export {
    FilterComparatorSelect,
    FilterDateTime,
    FilterFooter,
    FilterHeader,
    FilterMultiSelect,
    FilterSelect,
    FilterText
};

export const FilterComponents = {
    ComparatorSelect: FilterComparatorSelect,
    DateTime: FilterDateTime,
    Footer: FilterFooter,
    Header: FilterHeader,
    MultiSelect: FilterMultiSelect,
    Select: FilterSelect,
    Text: FilterText
} as const;

export type FilterComponentName = keyof typeof FilterComponents;

export function getFilterComponent(name: string) {
    return FilterComponents[name as FilterComponentName] || null;
}