import {ref} from "vue";
import BarChart from "../../../../src/components/charts/BarChart.vue";
import {vueRouter} from "storybook-vue3-router";
import {within, expect, fireEvent, waitFor} from "storybook/test";

const hasNavigated = ref("");

export default {
    title: "Dashboard/Charts/Executions/BarChart",
    component: BarChart,
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        },
        {
            path: "/executions",
            name: "executions/list",
            component: {template: "<div>executions</div>"},
            beforeEnter: (to) => {
                hasNavigated.value = to.name;
            }
        }])
    ],
    parameters: {
        layout: "centered",
    },
};

// Helper to generate sample data for the last n days
const generateSampleData = (days) => {
    const data = [];
    const states = ["SUCCESS", "FAILED", "RUNNING"];
    const now = new Date();

    for (let i = 0; i < days; i++) {
        const date = new Date(now);
        date.setDate(date.getDate() - i);

        const executionCounts = {};
        states.forEach(state => {
            executionCounts[state] = Math.floor(Math.random() * 50); // Random count between 0-50
        });

        data.push({
            startDate: date.toISOString(),
            executionCounts,
            duration: {
                avg: Math.floor(Math.random() * 300), // Random duration between 0-300 seconds
            },
            groupBy: "DAY"
        });
    }

    return data.reverse(); // Reverse to show oldest to newest
};

// Template for all stories
const Template = (args) => ({
    setup() {
        hasNavigated.value = "";
        return () => {
            return <div style="width: 200px;">
                <BarChart small duration={false} scales={false} {...args} style="height:50px"/>
            </div>
        }
    }
});



// Story with 30 days of data
export const ThirtyDays = Template.bind({});
ThirtyDays.args = {
    plugins: [],
    data: generateSampleData(30),
    total: 1500,
};
ThirtyDays.play = async ({canvasElement}) => {
    const canvas = within(canvasElement);
    const chart = canvas.getByRole("img");
    expect(chart).toBeInTheDocument();
    const {top, left} = chart.getBoundingClientRect();
    fireEvent.click(chart, {
        clientX: left + 50,
        clientY: top + 40,
    })
    waitFor(function checkHasNavigatedToExecutionsList(){
        expect(hasNavigated.value, "we should have navigated to executions list").toBe("executions/list");
    });
};

// Story with no data
export const NoData = Template.bind({});
NoData.args = {
    plugins: [],
    data: [],
    total: 0,
};

// Story with single day data
export const SingleDay = Template.bind({});
SingleDay.args = {
    plugins: [],
    data: generateSampleData(1),
    total: 50,
};