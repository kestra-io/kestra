import Bar from "../../../../../../src/components/dashboard/components/charts/executions/Bar.vue";

export default {
    title: "Dashboard/Charts/Executions/Bar",
    component: Bar,
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
    components: {Bar},
    setup() {
        return () => {
            return <div style="width: 800px;"><Bar {...args} /></div>
        }
    }
});

// Basic story with 7 days of data
export const SevenDays = Template.bind({});
SevenDays.args = {
    data: generateSampleData(7),
    total: 350, // Example total
};

// Story with 30 days of data
export const ThirtyDays = Template.bind({});
ThirtyDays.args = {
    data: generateSampleData(30),
    total: 1500,
};

// Story with no data
export const NoData = Template.bind({});
NoData.args = {
    data: [],
    total: 0,
};

// Story with single day data
export const SingleDay = Template.bind({});
SingleDay.args = {
    data: generateSampleData(1),
    total: 50,
};

// Story with hourly data
export const HourlyData = Template.bind({});
HourlyData.args = {
    data: Array.from({length: 24}, (_, i) => {
        const date = new Date();
        date.setHours(i);
        return {
            startDate: date.toISOString(),
            executionCounts: {
                SUCCESS: Math.floor(Math.random() * 30),
                FAILED: Math.floor(Math.random() * 10),
                RUNNING: Math.floor(Math.random() * 5),
            },
            duration: {
                avg: Math.floor(Math.random() * 300),
            },
            groupBy: "HOUR"
        };
    }),
    total: 500,
}; 

// Story with execution names as labels
export const ExecutionNamesAsLabels = Template.bind({});
ExecutionNamesAsLabels.args = {
    data: generateSampleData(7),
    total: 350,
    labels: ["Execution 1", "Execution 2", "Execution 3", "Execution 4", "Execution 5", "Execution 6", "Execution 7"]
};