import {ref} from "vue";
import {vueRouter} from "storybook-vue3-router";
import {
    userEvent,
    within,
    expect,
    waitFor
} from "@storybook/test";
import LogLine from "../../../../src/components/logs/LogLine.vue";
import {ElCard} from "element-plus";

const ALLOWED_LEVELS = [
    "TRACE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
];

export default {
    title: "Components/Logs/LogLine",
    component: LogLine,
    argTypes: {
        cursor: {
            control: "boolean",
            description: "Shows a cursor icon on the left side of the log line"
        },
        filter: {
            control: "text",
            description: "Text to filter log messages"
        },
        level: {
            control: "select",
            options: ALLOWED_LEVELS,
            description: "Log level"
        },
        excludeMetas: {
            control: "array",
            description: "Array of meta fields to exclude from display"
        },
        title: {
            control: "boolean",
            description: "Shows taskId or flowId as a title"
        }
    },
    decorators: [vueRouter([
        {
            path: "/",
            component: () => Promise.resolve({default: () => <div>Home</div>})
        },
        {
            path: "/flows",
            name: "flows/list",
            component: () => Promise.resolve({default: () => <div>Flows</div>})
        },
        {
            path: "/:namespace/flows/update/:id",
            name: "flows/update",
            component: () => Promise.resolve({default: () => <div>Flow Update</div>})
        },
        {
            path: "/:namespace/executions/:id",
            name: "executions/list",
            component: () => Promise.resolve({default: () => <div>Executions List</div>})
        }
        ,
        {
            path: "/:namespace/executions/update/:flowId/:id",
            name: "executions/update",
            component: () => Promise.resolve({default: () => <div>Executions List</div>})
        }
    ])]
};

const Template = (args) => ({
    components: {LogLine},
    setup() {
        return () => {
            args.log.level = args.level;
            return <LogLine {...args} />;
        }
    }
});

const argsDefaults = (level, message = "This is an info message") => ({
    cursor: true,
    log: {
        level,
        message,
        timestamp: new Date().toISOString(),
        namespace: "test-namespace",
        flowId: "flow-123",
        executionId: "exec-456"
    },
    level
})

export const Info = Template.bind({});
Info.args = argsDefaults("INFO");

export const Warning = Template.bind({});
Warning.args = argsDefaults("WARN");

export const Error = Template.bind({});
Error.args = argsDefaults("ERROR");

export const WithTitle = Template.bind({});
WithTitle.args = {
    cursor: true,
    log: {
        level: "INFO",
        message: "This is a message with title",
        timestamp: new Date().toISOString(),
        namespace: "test-namespace",
        flowId: "flow-123",
        executionId: "exec-456",
        taskId: "task-789"
    },
    level: "INFO",
    title: true
};

export const WithFilter = {
    render: () => {
        return {
            setup(){
                const values = ref("filterable");
                return () => <el-card>
                    <el-form label-position="top">
                        <el-form-item label="Filter">
                            <el-input v-model={values.value} type="search" placeholder="Filter"/>
                        </el-form-item>
                        <hr style={{margin: ".5rem 0"}}/>
                        <LogLine {...argsDefaults("INFO", "This is a filterable message")} filter={values.value} />
                    </el-form>
                </el-card>
            }
        }
    }
}

// check if when the filter changes, the message disapears
WithFilter.play = async ({canvasElement}) => {
    const can = within(canvasElement);
    const input = can.getByLabelText("Filter");
    await userEvent.type(input, " hide me");
    await waitFor(() => expect(can.queryByText("This is a filterable message")).not.toBeInTheDocument());
}

export const WithRouterLinkInMarkdown = Template.bind({});
WithRouterLinkInMarkdown.args = argsDefaults("INFO", "Created new execution [[link execution=\"4Q9z27FJ26FRIhdv037HtF\" flowId=\"child\" namespace=\"company.team\"]]")

// check that the proper links were created from the message
WithRouterLinkInMarkdown.play = async ({canvasElement}) => {
    const can = within(canvasElement);
    await waitFor(() => expect(can.getAllByRole("link")).toHaveLength(5));
    const links = can.getAllByRole("link");
    expect(links[3]).toHaveTextContent("4Q9z27FJ26FRIhdv037HtF");
    expect(links[4]).toHaveTextContent("company.team.child");
}

export const WithExcludedMetas = Template.bind({});
WithExcludedMetas.args = {
    cursor: true,
    log: {
        level: "INFO",
        message: "This message has excluded meta fields",
        timestamp: new Date().toISOString(),
        namespace: "test-namespace",
        flowId: "flow-123",
        executionId: "exec-456"
    },
    level: "INFO",
    filter: "",
    excludeMetas: ["namespace", "flowId"],
    title: false
};

export const MultipleLogLinesWithAllLevels = () => {
    return (
        <ElCard>
            {
                ALLOWED_LEVELS.map((level, index) => {
                    return <LogLine {...Info.args} cursor={false} level={level} log={{...Info.args.log, level}} style={{borderTop: index===0 ? "none" : "1px solid var(--ks-border-primary)"}} />
                })
            }
        </ElCard>
    );
};

// reproduction of https://github.com/kestra-io/kestra/pull/7133
export const ShortLogWithoutContext = () => {
    return (
        <ElCard>
            <LogLine log={{level: "INFO", message: "test"}} />
        </ElCard>
    );
}