import {mount, shallowMount, type ComponentMountingOptions} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

type MessageValue = string | MessageValue[] | {[key: string]: MessageValue}
type Messages = {[key: string]: MessageValue}

interface AppOptions {
    /** Messages for the `en` locale. Left empty, `t("key")` renders the key, which is what most specs assert on. */
    messages?: Messages
    /** A whole locale map, for a spec that loads `en.json` instead of listing the keys it needs. */
    locales?: {[locale: string]: Messages}
}

export type I18nMountOptions<T> = ComponentMountingOptions<T> & AppOptions

function withApp<T>({messages = {}, locales, global: globalOptions = {}, ...mountOptions}: I18nMountOptions<T>) {
    // A spec that asserts on raw keys leaves most of them unset on purpose, so the warnings are off.
    const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: locales ?? {en: messages}})
    const callerPlugins = globalOptions.plugins ?? []
    return {
        ...mountOptions,
        global: {
            ...globalOptions,
            // The app registers the design system at bootstrap, so a mount without it resolves no
            // Ks component and warns once per mount, with the offending props in every trace.
            plugins: [i18n, ...(callerPlugins.includes(KestraDesignSystem) ? [] : [KestraDesignSystem]), ...callerPlugins],
        },
    } as ComponentMountingOptions<T>
}

/**
 * Mounts a component with the app context a spec needs, so no spec has to build its own i18n.
 * Anything passed in `global` is merged over the defaults, and extra plugins run after i18n.
 */
export function i18nMount<T>(component: T, options: I18nMountOptions<T> = {}) {
    return mount(component, withApp(options))
}

/** `i18nMount` with every child component stubbed. */
export function i18nShallowMount<T>(component: T, options: I18nMountOptions<T> = {}) {
    return shallowMount(component, withApp(options))
}
