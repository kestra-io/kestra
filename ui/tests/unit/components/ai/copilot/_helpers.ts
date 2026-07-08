import {createI18n} from "vue-i18n"
import en from "../../../../../src/translations/en.json"

/**
 * Real `en` messages so `ai.copilot.*` keys resolve to actual copy (catches typos /
 * shadowed keys), warnings silenced.
 */
export const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

/**
 * Lightweight functional stubs for the design-system components the copilot uses.
 * The real DS pulls in `@popperjs/core` (CJS) which breaks the jsdom unit env, and
 * these tests target the copilot components' own logic — not Element Plus rendering.
 * Each stub keeps just enough behaviour (v-model, click, disabled, slots) to assert
 * wiring, and lets `data-test` / listeners fall through to its root element.
 */
export const ksStubs = {
    KsInput: {
        name: "KsInput",
        props: ["modelValue", "disabled", "placeholder", "type", "autosize", "rows"],
        emits: ["update:modelValue"],
        template: `<textarea
            :disabled="disabled"
            :placeholder="placeholder"
            :value="modelValue"
            @input="$emit('update:modelValue', $event.target.value)"
        ></textarea>`,
    },
    KsButton: {
        name: "KsButton",
        props: ["icon", "disabled", "type", "ariaLabel"],
        emits: ["click"],
        template: "<button :disabled=\"disabled\" @click=\"$emit('click')\"><slot /></button>",
    },
    KsSegmented: {
        name: "KsSegmented",
        props: ["options", "modelValue", "size"],
        emits: ["change"],
        template: `<div class="ks-segmented">
            <button v-for="o in options" :key="o.value" :data-value="o.value" @click="$emit('change', o.value)">{{ o.label }}</button>
        </div>`,
    },
    KsCard: {name: "KsCard", template: "<div><slot /></div>"},
    KsText: {name: "KsText", props: ["size", "type"], template: "<span><slot /></span>"},
    KsIcon: {name: "KsIcon", template: "<i><slot /></i>"},
    KsTag: {name: "KsTag", props: ["size"], template: "<span class=\"ks-tag\"><slot /></span>"},
    KsAlert: {name: "KsAlert", props: ["type"], template: "<div class=\"ks-alert\" :data-type=\"type\"><slot /></div>"},
    KsScrollbar: {name: "KsScrollbar", template: "<div><slot /></div>"},
    KsMarkdown: {name: "KsMarkdown", props: ["content"], template: "<div class=\"ks-markdown\">{{ content }}</div>"},
    KsCollapse: {name: "KsCollapse", props: ["modelValue"], template: "<div><slot /></div>"},
    KsCollapseItem: {name: "KsCollapseItem", props: ["name", "title"], template: "<div><span class=\"collapse-title\">{{ title }}</span><slot /></div>"},
}

/** Common `global` mount option for copilot component tests. */
export const mountGlobal = {plugins: [i18n], stubs: ksStubs}
