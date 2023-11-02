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

// used to configure VSCode startup
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
            scheme: "https",
            authority: "openvsxorg.blob.core.windows.net",
            path: "/resources/PROxZIMA/sweetdracula/1.0.9/extension"
        },
        {
            scheme: window.location.protocol.replace(":", ""),
            authority: window.location.host,
            path: KESTRA_UI_PATH + "yamlExt"
        },
        {
            scheme: "https",
            authority: "openvsxorg.blob.core.windows.net",
            path: "/resources/kestra-io/kestra/0.1.6/extension"
        }
    ],
    "linkProtectionTrustedDomains": [
        "https://open-vsx.org",
        "https://openvsxorg.blob.core.windows.net"
    ],
    enabledExtensions: [
        // to handle dark theme
        "proxzima.sweetdracula",
        // to apply Kestra's flow validation schema
        "redhat.vscode-yaml",
        "kestra-io.kestra"
    ],
    configurationDefaults: {
        "files.autoSave": "off",
        "editor.fontSize": 12,
        "workbench.colorTheme": THEME === "dark" ? "Sweet Dracula" : "Default Light Modern",
        "workbench.startupEditor": "readme",
        // "workbench.activityBar.visible": false,
        // provide the Kestra root URL to extension
        "kestra.api.url": KESTRA_API_URL
    }
};