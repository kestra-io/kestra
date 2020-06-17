const path = require('path');

module.exports = {
    publicPath: "/ui/",
    outputDir: "../webserver/src/main/resources/ui",
    configureWebpack: {
        resolve: {
            alias: {
                Override: path.resolve(__dirname, 'src/override/')
            }
        },
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
