/** @type {import('stylelint').Config} */
export default {
    extends: [
        "stylelint-config-recommended-scss",
        "stylelint-config-recommended-vue/scss",
    ],
    plugins: [
        "./plugins/lint-custom-properties.mjs",
    ],
    // Everything except the token rule is pre-existing debt this config never enforced — 269 hex
    // colours and 158 non-`--ks` custom properties, which is why nothing ran it. They stay visible
    // in an editor as warnings; only an undeclared token fails a build, since that one is not a
    // style preference but a declaration the browser drops.
    defaultSeverity: "warning",
    rules: {
        "ks/no-undeclared-custom-property": [true, {severity: "error"}],
        "color-no-hex": true,
        "no-descending-specificity": null,
        "ks/custom-property-pattern-usage": [
            /(?<=ks-)/,
            {
                message: (prop) =>  `"${prop}" is not allowed. Try to use "--ks" prefixed custom properties`,
            },
        ],
        "scss/no-global-function-names": null,
    },
}