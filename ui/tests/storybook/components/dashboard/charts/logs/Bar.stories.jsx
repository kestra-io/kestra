import Bar from "../../../../../../src/components/dashboard/components/charts/logs/Bar.vue";

export default {
    title: "Dashboard/Charts/Logs/Bar",
    component: Bar,
    parameters: {
        layout: "centered",
    },
};

// Helper to generate sample data for the last n days
const generateSampleData = (hours) => {
    const data = [];
    const states = ["TRACE",
        "INFO",
        "DEBUG",
        "WARN",
        "ERROR"];
    const now = new Date();

    for (let h = 0; h < hours; h++) {
        const date = new Date(now);
        // set a date for each hour on the hours scale
        date.setHours(date.getHours() - h);

        const executionCounts = {};
        states.forEach(state => {
            executionCounts[state] = Math.floor(Math.random() * 50); // Random count between 0-50
        });

        data.push({
            "timestamp": date.toISOString(),
            "counts": states.reduce((acc, state) => {
                acc[state] = executionCounts[state];
                return acc;
            }, {}),
            "groupBy": "day"
        });
    }

    return data.reverse(); // Reverse to show oldest to newest
};

// Template for all stories
const Template = (args) => ({
    setup() {
        return () => <div style="width: 800px;"><Bar {...args} /></div>
    }
});

// Story with single day data
export const SingleDay24Hours = Template.bind({});
SingleDay24Hours.args = {
    data: generateSampleData(24),
    total: 2,
};