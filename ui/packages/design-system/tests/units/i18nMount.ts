import {mount, shallowMount, type ComponentMountingOptions} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../src/index"

type MessageValue = string | MessageValue[] | {[key: string]: MessageValue}
type Messages = {[key: string]: MessageValue}

interface AppOptions {
    /** Messages for the `en` locale. Left empty, `t("key")` renders the key, which is what most specs assert on. */
    messages?: Messages
    /** A whole locale map, for a spec that loads a locale file instead of listing the keys it needs. */
    locales?: {[locale: string]: Messages}
}

export type I18nMountOptions<T> = ComponentMountingOptions<T> & AppOptions

function withApp<T>({messages = {}, locales, global: globalOptions = {}, ...mountOptions}: I18nMountOptions<T>) {
    const i18n = createI18n({legacy: false, locale: "en", messages: locales ?? {en: messages}})
    return {
        ...mountOptions,
        global: {
            ...globalOptions,
            plugins: [i18n, KestraDesignSystem, ...(globalOptions.plugins ?? [])],
        },
    } as ComponentMountingOptions<T>
}

/**
 * Mounts a design-system component with i18n and the design-system plugin installed, so a component
 * that starts using `$t` cannot fail on a spec that simply forgot the plugin.
 */
export function i18nMount<T>(component: T, options: I18nMountOptions<T> = {}) {
    return mount(component, withApp(options))
}

/** `i18nMount` with every child component stubbed. */
export function i18nShallowMount<T>(component: T, options: I18nMountOptions<T> = {}) {
    return shallowMount(component, withApp(options))
}
