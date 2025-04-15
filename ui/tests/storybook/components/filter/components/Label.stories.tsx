import Label from "../../../../../src/components/filter/components/Label.vue";

export default {title: "filter/components/label", component: Label};

export const Default = () => <Label prefix="ks-" option={{
    label: "namespace",
    value: ["engineering.kestra.io"],
    comparator: {label: "starts with", value: "starts with"},
}} />;

export const MultipleValue = () => <Label prefix="ks-" option={{
    label: "state",
    value: ["SUCCESS", "RUNNING"],
    comparator: {label: "is one of", value: "is one of", multiple: true}
}} />;

export const DateValue = () => <Label prefix="ks-" option={{
    label: "absolute_date",
    value: [{startDate: new Date(), endDate: new Date()}],
    comparator: {label: "between", value: "between"}
}} />;