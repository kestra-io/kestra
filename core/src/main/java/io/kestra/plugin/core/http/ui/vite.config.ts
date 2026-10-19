import defaultViteConfig from "@kestra-io/artifact-sdk/vite.config";

const slots = () => [
    {
        slotName: "topology-details",
        path: "./src/components/HttpTopologyDetails.vue",
        additionalProperties: {
            // node base (56) + KsTopologyDetails rows (~31px each)
            // no execution: nothing to show, one row
            height: 90,
            // with execution: method, host and path, status, content type, size, duration
            heightWithExecution: 250,
            // A non empty taskProp is what makes the host open the topology-task-drawer slot.
            customAction: { label: "HTTP request details", taskProp: "uri", lang: "plaintext" },
        },
    },
    {
        slotName: "topology-task-drawer",
        path: "./src/components/HttpTaskDrawer.vue",
    },
];

export default defaultViteConfig({
    plugin: "io.kestra.plugin.core",

    exposes: {
        "http.Request": slots(),
        "http.Download": slots(),
    },
});