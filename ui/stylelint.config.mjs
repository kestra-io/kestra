/** @type {import('stylelint').Config} */
export default {
    plugins: ["./scripts/tokens/stylelintPlugin.mjs"],
    rules: {"ks/no-undeclared-custom-property": true},
    overrides: [
        {files: ["**/*.vue"], customSyntax: "postcss-html"},
        {files: ["**/*.scss"], customSyntax: "postcss-scss"},
    ],
    ignoreFiles: ["**/node_modules/**", "**/dist/**", "**/coverage/**", "**/storybook-static/**"],
}
