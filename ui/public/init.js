Object.keys(window.webPackagePaths).map(function (key) {
    window.webPackagePaths[key] = `${window.location.origin}${KESTRA_UI_PATH}vscode-web/dist/node_modules/${key}/${window.webPackagePaths[key]}`;
});

require.config({
    baseUrl: `${window.location.origin}${KESTRA_UI_PATH}vscode-web/dist/out`,
    recordStats: true,
    trustedTypesPolicy: window.trustedTypes?.createPolicy("amdLoader", {
        createScriptURL(value) {
            return value;
        }
    }),
    paths: window.webPackagePaths
});

// publisherId/extensionId format
const extensionsToFetch = {
    // to handle dark theme
    "PROxZIMA/sweetdracula": "1.0.9",
    // to apply Kestra's flow validation schema, comment for local extension testing
    "kestra-io/kestra": "0.1.11",
};

let url;
try {
    url = new URL(KESTRA_API_URL);
} catch (e) {
    // KESTRA_API_URL is a relative path
    url = {
        ...window.location,
        pathname: KESTRA_API_URL
    };
}
const extensionUrls = Object.entries(extensionsToFetch).map(([extensionId, version]) => ({
    scheme: url.protocol.replace(":", ""),
    authority: url.host,
    path: `${url.pathname}/editor/marketplace/resource/${extensionId}/${version}/extension`
}));

// used to configure VSCode startup
const sidebarTabs = [
    {"id": "workbench.view.explorer", "pinned": true, "visible": true, "order": 0},
    {"id": "workbench.view.search", "pinned": true, "visible": true, "order": 1},
    {"id": "workbench.view.scm", "pinned": false, "visible": false, "order": 2},
    {"id": "workbench.view.debug", "pinned": false,"visible": false,"order": 3},
    {"id": "workbench.view.extensions", "pinned": true,"visible": true,"order": 4},
    {"id": "workbench.view.remote", "pinned": false,"visible": false,"order": 4},
    {"id": "workbench.view.extension.test", "pinned": false,"visible": false,"order": 6},
    {"id": "workbench.view.extension.references-view", "pinned": false,"visible": false,"order": 7},
    {"id": "workbench.panel.chatSidebar", "pinned": false,"visible": false,"order": 100},
    {"id": "userDataProfiles", "pinned": false, "visible": false},
    {"id": "workbench.view.sync", "pinned": false,"visible": false},
    {"id": "workbench.view.editSessions", "pinned": false, "visible": false}
];

const bottomBarTabs = [
    {"id":"workbench.panel.markers", "pinned": false,"visible": false,"order": 0},
    {"id":"workbench.panel.output", "pinned": false,"visible": false,"order": 1},
    {"id":"workbench.panel.repl", "pinned": false,"visible": false,"order": 2},
    {"id":"terminal", "pinned": false,"visible": false,"order": 3},
    {"id":"workbench.panel.testResults", "pinned": false,"visible": false,"order": 3},
    {"id":"~remote.forwardedPortsContainer", "pinned": false,"visible": false,"order": 5},
    {"id":"refactorPreview", "pinned": false,"visible": false}
];

const apiUrl = url.origin + url.pathname;
window.product = {
    productConfiguration: {
        nameShort: "Kestra VSCode",
        nameLong: "Kestra VSCode",
        // configure VSCode Marketplace
        extensionsGallery: {
            nlsBaseUrl: `${apiUrl}/editor/marketplace/nls`,
            serviceUrl: `${apiUrl}/editor/marketplace/service`,
            searchUrl: `${apiUrl}/editor/marketplace/search`,
            servicePPEUrl: `${apiUrl}/editor/marketplace/serviceppe`,
            cacheUrl: `${apiUrl}/editor/marketplace/cache`,
            itemUrl: `${apiUrl}/editor/marketplace/item`,
            publisherUrl: `${apiUrl}/editor/marketplace/publisher`,
            resourceUrlTemplate: `${apiUrl}/editor/marketplace/resource/{publisher}/{name}/{version}/{path}`,
            controlUrl: `${apiUrl}/editor/marketplace/control`
        },
        extensionEnabledApiProposals: {
            "ms-python.python": [
                "contribEditorContentMenu",
                "quickPickSortByLabel",
                "testObserver",
                "quickPickItemTooltip",
                "saveEditor",
                "terminalDataWriteEvent",
                "terminalExecuteCommandEvent"
            ],
            "kestra-io.kestra": [
                "fileSearchProvider"
            ]
        }
    },
    // scope the VSCode instance to Kestra File System Provider (defined in Kestra VSCode extension)
    folderUri: {
        scheme: "kestra",
        path: "/" + queryParams["namespace"]
    },
    commands: [
        {
            id: "custom.postMessage",
            handler: async (data) => {
                window.parent.postMessage(data, "*")
            }
        }
    ],
    additionalBuiltinExtensions: [
        {
            scheme: window.location.protocol.replace(":", ""),
            authority: window.location.host,
            path: KESTRA_UI_PATH + "vscode/extensions/yaml/extension"
        },
        // uncomment for local extension testing
        /*{
            scheme: window.location.protocol.replace(":", ""),
            authority: window.location.host,
            path: KESTRA_UI_PATH + "vscode/extensions/kestra/extension"
        },*/
        ...extensionUrls
    ],
    configurationDefaults: {
        "files.autoSave": "off",
        "editor.fontSize": 12,
        "workbench.colorTheme": THEME === "dark" ? "Sweet Dracula" : "Default Light Modern",
        // provide the Kestra root URL to extension
        "kestra.api.url": KESTRA_API_URL
    },
    profile: {
        name: "Kestra VSCode",
        contents: JSON.stringify({
            globalState: JSON.stringify({
                "storage": {
                    "workbench.activity.pinnedViewlets2": sidebarTabs,
                    "workbench.activity.showAccounts": "false",
                    "workbench.panel.pinnedPanels": bottomBarTabs
                }
            })
        })
    }
};