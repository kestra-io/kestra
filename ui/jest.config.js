module.exports = {
    preset: "@vue/cli-plugin-unit-jest",
    moduleFileExtensions: [
        "js",
        "json",
        "vue"
    ],
    transform: {
        ".*\\.(vue)$": "@vue/vue2-jest",
        ".*\\.(js)$": "babel-jest"
    },
    transformIgnorePatterns: [
        "/node_modules/(?!vue-material-design-icons|vue-axios|axios)"
    ],
    modulePaths: [
        "<rootDir>/src/"
    ],
    moduleNameMapper: {
        "^.+\\.(css|styl|less|sass|scss|png|jpg|ttf|woff|woff2)$": "jest-transform-stub"
    },
    globals: {
        "KESTRA_BASE_PATH": "/"
    },
    setupFiles: ["./tests/jest.setup.js"],
}
