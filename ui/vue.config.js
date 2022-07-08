const path = require("path");
const webpack = require("webpack");
const MonacoEditorPlugin = require("monaco-editor-webpack-plugin")
// const WebpackBundleAnalyzer = require("webpack-bundle-analyzer").BundleAnalyzerPlugin;

module.exports = {
    publicPath: "/ui/",
    outputDir: "../webserver/src/main/resources/ui",
    pages: {
        index: {
            entry: "src/main.js",
            template: "public/index.html",
            filename: "index.html",
        },
    },
    chainWebpack: config => {
        config.entry("theme-light")
            .add("./src/styles/theme-light.scss")
            .end();

        config.entry("theme-dark")
            .add("./src/styles/theme-dark.scss")
            .end();
    },
    configureWebpack: {
        devtool: process.env.NODE_ENV !== "production" ? "eval-source-map" : false,
        optimization: {
            runtimeChunk: {
                name: "runtime",
            },
        },
        resolve: {
            alias: {
                override: path.resolve(__dirname, "src/override/"),
                "monaco-editor$": "monaco-editor"
            }
        },
        module: {
            rules: [
                {
                    test: /\.sass$/,
                    use: ["vue-style-loader", "css-loader", "sass-loader"]
                }
            ]
        },
        plugins: [
            new MonacoEditorPlugin({
                languages: [],
                customLanguages: [
                    {
                        label: "yaml",
                        entry: [
                            "monaco-yaml/lib/esm/monaco.contribution",
                            "vs/basic-languages/yaml/yaml.contribution",
                        ],
                        worker: {
                            id: "monaco-yaml/lib/esm/yamlWorker",
                            entry: "monaco-yaml/lib/esm/yaml.worker",
                        },
                    },
                ],
                features: [
                    "!accessibilityHelp",
                    "!anchorSelect",
                    "!codelens",
                    "!colorPicker",
                    "!gotoError",
                    "!gotoSymbol",
                    "!rename",
                    "!snippets",
                    "!toggleHighContrast",
                    "!toggleTabFocusMode",
                    "!viewportSemanticTokens",
                ]
            }),
            new webpack.ProvidePlugin({
                process: "process/browser",
                Buffer: ["buffer", "Buffer"],
            }),
            // new WebpackBundleAnalyzer()
        ],
    },
    css: {
        sourceMap: process.env.NODE_ENV !== "production"
    }
};
