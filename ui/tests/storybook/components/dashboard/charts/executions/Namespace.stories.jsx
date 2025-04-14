import Namespace from "../../../../../../src/components/dashboard/components/charts/executions/Namespace.vue";

export default {
    title: "Dashboard/Charts/Executions/Namespace",
    component: Namespace,
    parameters: {
        layout: "centered",
    },
};

// Helper to generate sample namespace data
const generateNamespaceData = (namespaceCount, prefix = "namespace-") => {
    const states = ["SUCCESS", "FAILED", "RUNNING"];
    const data = {};

    for (let i = 1; i <= namespaceCount; i++) {
        const counts = {};
        states.forEach(state => {
            counts[state] = Math.floor(Math.random() * 50); // Random count between 0-50
        });

        const total = Object.values(counts).reduce((sum, count) => sum + count, 0);

        data[`${prefix}${i}`] = {
            counts,
            total
        };
    }

    return data;
};

// Calculate total executions from namespace data
const calculateTotal = (data) => {
    return Object.values(data).reduce((sum, namespace) => sum + namespace.total, 0);
};

// Template for all stories
const Template = (args) => ({
    components: {Namespace},
    setup() {
        return () => {
            return <div style="width: 800px;"><Namespace {...args} /></div>
        }
    }
});

// Story with 5 namespaces
export const FiveNamespaces = Template.bind({});
const fiveNamespacesData = generateNamespaceData(5);
FiveNamespaces.args = {
    data: fiveNamespacesData,
    total: calculateTotal(fiveNamespacesData)
};

// Story with many namespaces
export const ManyNamespaces = Template.bind({});
const manyNamespacesData = generateNamespaceData(15);
ManyNamespaces.args = {
    data: manyNamespacesData,
    total: calculateTotal(manyNamespacesData)
};

// Story with single namespace
export const SingleNamespace = Template.bind({});
const singleNamespaceData = generateNamespaceData(1);
SingleNamespace.args = {
    data: singleNamespaceData,
    total: calculateTotal(singleNamespaceData)
};

// Story with no data
export const NoData = Template.bind({});
NoData.args = {
    data: {},
    total: 0
};

// Story with custom namespace names
export const CustomNamespaces = Template.bind({});
const customData = {
    "dev-team": {
        counts: {
            SUCCESS: 45,
            FAILED: 5,
            RUNNING: 3
        },
        total: 53
    },
    "prod-pipeline": {
        counts: {
            SUCCESS: 98,
            FAILED: 2,
            RUNNING: 5
        },
        total: 105
    },
    "data-ingestion": {
        counts: {
            SUCCESS: 75,
            FAILED: 8,
            RUNNING: 4
        },
        total: 87
    }
};

CustomNamespaces.args = {
    data: customData,
    total: calculateTotal(customData)
};

// Story with super long namespace names
export const LongNamespaces = Template.bind({});
const customDataLong = generateNamespaceData(25, "super-long-namespace-name-");
   
LongNamespaces.args = {
    data: customDataLong,
    total: calculateTotal(customDataLong)
}; 