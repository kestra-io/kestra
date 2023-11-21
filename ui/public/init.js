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
const versionByExtensionIdToFetch = {
    // to handle dark theme
    "PROxZIMA/sweetdracula": "1.0.9",
    // to apply Kestra's flow validation schema
    "kestra-io/kestra": "0.1.7",
    // for Python autocompletion along with Pylance that is needed for it to work
    // "ms-python/python": "2023.20.0"
};

const extensionsToFetch = Object.entries(versionByExtensionIdToFetch).map(([extensionId, version]) => ({
    scheme: "https",
    authority: "openvsxorg.blob.core.windows.net",
    path: `/resources/${extensionId}/${version}/extension`
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

window.product = {
    productConfiguration: {
        nameShort: "Kestra VSCode",
        nameLong: "Kestra VSCode",
        // configure the open sx marketplace
        "extensionsGallery": {
            "serviceUrl": "https://open-vsx.org/vscode/gallery",
            "itemUrl": "https://open-vsx.org/vscode/item",
            "resourceUrlTemplate": "https://openvsxorg.blob.core.windows.net/resources/{publisher}/{name}/{version}/{path}"
        },
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
        // {
        //     scheme: window.location.protocol.replace(":", ""),
        //     authority: window.location.host,
        //     path: KESTRA_UI_PATH + "vscode/extensions/pylance/extension"
        // },
        ...extensionsToFetch
    ],
    "linkProtectionTrustedDomains": [
        "https://open-vsx.org",
        "https://openvsxorg.blob.core.windows.net"
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