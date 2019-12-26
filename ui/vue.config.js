module.exports = {
    publicPath: "/ui/",
    outputDir: "../webserver/src/main/resources/ui",
    configureWebpack: {
        module: {
            rules: [
                {
                    test: /\.sass$/,
                    use: [
                        'vue-style-loader',
                        'css-loader',
                        'sass-loader'
                    ]
                }
            ]
        },
        plugins: []
    },
    css: {
        sourceMap: true
    }
};