import ForEachStatus from "../../../../src/components/executions/ForEachStatus.vue";
import {vueRouter} from "storybook-vue3-router";

const meta = {
    title: "components/ForEachStatus",
    component: ForEachStatus,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
            {
                path: "/executions",
                name:"executions/list",
                component: {template: "<div>executions</div>"}
            },
        ])]
}

export default meta;

export const Default = {
    render(){
        return ( <div style={{border: "1px solid lightgray", padding: "1rem", width: "600px"}}>
            <ForEachStatus executionId={"123123"} subflowsStatus={{
                RUNNING:555,
                WARNING:222,
                FAILED:100
            }} max={1000} />
        </div>)
    }
}