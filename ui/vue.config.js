const path = require("path");

module.exports = {
    publicPath: "/ui/",
    outputDir: "../webserver/src/main/resources/ui",
    configureWebpack: {
        devtool: process.env.NODE_ENV !== "production" ? "eval-source-map" : false,
        resolve: {
            alias: {
                Override: path.resolve(__dirname, "src/override/")
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
        plugins: []
    },
    css: {
        sourceMap: process.env.NODE_ENV !== "production"
    }
};
