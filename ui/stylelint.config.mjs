/** @type {import('stylelint').Config} */
export default {
    extends: [
        "stylelint-config-recommended-scss",
        "stylelint-config-recommended-vue/scss"
    ],
    plugins: [
        "./plugins/lint-custom-properties.mjs",
    ],
    rules: {
        "color-no-hex": true,
        "no-descending-specificity": null,
        "ks/custom-property-pattern-usage": [
            /(?<=ks-)/,
            {
                message: (prop) =>  `"${prop}" is not allowed. Try to use "--ks" prefixed custom properties`
            }
        ],
        "scss/no-global-function-names": null,
    },
}