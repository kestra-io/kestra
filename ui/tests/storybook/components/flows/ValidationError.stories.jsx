import ValidationError from "../../../../src/components/flows/ValidationError.vue";

export default {
    title: "Components/Flows/ValidationError",
    component: ValidationError,
    argTypes: {
        errors: {control: "array"},
        warnings: {control: "array"},
        infos: {control: "array"},
        link: {control: "boolean"},
        size: {control: "text"},
        tooltipPlacement: {control: "text"},
    },
}

const Template = (args) => ({
    components: {ValidationError},
    setup() {
        return {args};
    },
    template: "<ValidationError v-bind=\"args\" />",
});

export const Default = Template.bind({});
Default.args = {
    errors: ["Error 1", "Error 2"],
    warnings: ["Warning 1"],
    infos: ["Info 1"],
    link: false,
    size: "default",
    tooltipPlacement: "top",
};
