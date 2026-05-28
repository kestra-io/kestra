import {setup} from "@storybook/vue3-vite"
import {withThemeByClassName} from "@storybook/addon-themes"
import type {Preview} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createRouter, createMemoryHistory} from "vue-router"

import "../src/assets/styles/index.scss"

import KestraDesignSystem from "../src/index"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

setup((app) => {
    app.use(createI18n({legacy: false, locale: "en"}))
    app.use(KestraDesignSystem)
    app.use(router)
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
                "dark-2": "dark dark-2",
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
                color: /[Cc]olor$/,
                date: /Date$/i,
            },
        },
    },
}

export default preview
