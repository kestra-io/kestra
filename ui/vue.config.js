const path = require("path");
const MonacoEditorPlugin = require("monaco-editor-webpack-plugin")
// const WebpackBundleAnalyzer = require("webpack-bundle-analyzer").BundleAnalyzerPlugin;

module.exports = {
    publicPath: "/ui/",
    outputDir: "../webserver/src/main/resources/ui",
    configureWebpack: {
        devtool: process.env.NODE_ENV !== "production" ? "eval-source-map" : false,
        resolve: {
            alias: {
                Override: path.resolve(__dirname, "src/override/"),
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
