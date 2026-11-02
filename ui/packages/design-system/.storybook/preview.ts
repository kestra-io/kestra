import {setup} from "@storybook/vue3-vite"
import {withThemeByClassName} from "@storybook/addon-themes"
import type {Preview} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createRouter, createMemoryHistory} from "vue-router"

import "../src/assets/styles/index.scss"

import KestraDesignSystem, {registerDesignSystemI18n} from "../src/index"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

const i18n = createI18n({legacy: false, locale: "en"})

setup((app) => {
    app.use(i18n)
    app.use(KestraDesignSystem)
    app.use(router)
})

const preview: Preview = {
    beforeAll: () => registerDesignSystemI18n(i18n),
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
        backgrounds: {disabled: true},
        controls: {
            matchers: {
                color: /[Cc]olor$/,
                date: /Date$/i,
            },
        },
    },
}

export default preview
