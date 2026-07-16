import {Comparators} from "@kestra-io/design-system"

type Translate = (key: string) => string

export const labelComparatorLabels = (t: Translate): Partial<Record<Comparators, string>> => ({
    [Comparators.IN]: t("filter.label_comparators.has_any_of"),
    [Comparators.NOT_IN]: t("filter.label_comparators.has_none_of"),
    [Comparators.EQUALS]: t("filter.label_comparators.has_all_of"),
    [Comparators.CONTAINS]: t("filter.label_comparators.contains"),
    [Comparators.NOT_CONTAINS]: t("filter.label_comparators.does_not_contain"),
    [Comparators.IS_NOT_NULL]: t("filter.label_comparators.is_set"),
    [Comparators.IS_NULL]: t("filter.label_comparators.is_not_set"),
})
