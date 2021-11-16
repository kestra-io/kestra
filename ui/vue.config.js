const path = require("path");
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
                languages: ["yaml"],
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
            // new WebpackBundleAnalyzer()
        ],
    },
    css: {
        sourceMap: process.env.NODE_ENV !== "production"
    }
};
