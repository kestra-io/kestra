import {setup} from "@storybook/vue3-vite"
import {withThemeByClassName} from "@storybook/addon-themes"
import ElementPlus from "element-plus"
/*
 * dist/index.css contains all light-mode variables (:root) and component styles.
 * theme-chalk/dark/css-vars.css contains ONLY the html.dark { ... } overrides —
 * they are NOT bundled into dist/index.css and must be imported separately.
 */
import "element-plus/dist/index.css"
import "element-plus/theme-chalk/dark/css-vars.css"
import "./storybook.css"
import type {Preview} from "@storybook/vue3-vite"

// Register Element Plus globally so el-option resolves its parent el-select context
setup((app) => {
    app.use(ElementPlus)
})

const preview: Preview = {
    decorators: [
        /*
         * Adds/removes the `dark` class on <html>.
         * Element Plus uses `html.dark` as its dark-mode selector, so this
         * is the only toggle needed — no JavaScript color swapping is required.
         *
         * light → class "" (no class added, :root variables stay active)
         * dark → class "dark" (html.dark overrides activate)
         */
        withThemeByClassName({
            themes: {
                light: "",
                dark: "dark",
            },
            defaultTheme: "light",
        }),
    ],
    parameters: {
        /*
         * Disable the built-in backgrounds panel – the canvas background
         * is handled entirely by the Element Plus CSS variables in
         * storybook.css, which automatically switches with the theme.
         */
        backgrounds: {disable: true},
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i,
            },
        },
    },
}

export default preview
