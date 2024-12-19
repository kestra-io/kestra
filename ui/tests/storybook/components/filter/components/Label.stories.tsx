import type {StoryObj} from "@storybook/vue3";
import {
    within,
    expect,
    waitFor
} from "@storybook/test";
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


export const WithTest: StoryObj<typeof Label> = {
    // this in an example test and should not be taken seriously
    play({canvasElement}){
        const canvas = within(canvasElement);
        waitFor(function testRender() {
            expect(canvas.getByText("single compared value")).not.toBeNull();
        })
    },
    render: () => ({
        setup(){
            return () => <Label option={{
                    label: "action",
                    value: ["single compared value"],
                    comparator: "eq"
                }}/>
        }
    })
}
