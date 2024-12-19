import Label from "../../../../../src/components/filter/components/Label.vue";

export default {
    title: "Components/Filter/Components/Label",
    component: Label,
};

export const Default = () => <Label option={{
    label: "action",
    value: ["compared value", "compared value 2"],
    comparator: "eq"
}}/>;

export const WithSingleValue = () => <Label option={{
    label: "action",
    value: ["single compared value"],
    comparator: "eq"
}}/>;