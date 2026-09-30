import en from "../src/translations/en.json"

// URLs are relative to the manifest URL (<contextPath>/ui/) so sub-path deployments resolve;
// labels come from en.json, English-only because the manifest is one static build artifact.
export const pwaShortcuts = [
    {name: en.en.create_flow, url: "flows/new"},
    {name: en.en.flows, url: "flows"},
    {name: en.en.executions, url: "executions"},
    {name: en.en.ai.flow.title, url: "ai"},
]
