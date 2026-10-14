import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import KsInput from "../../../src/components/Form/KsInput.vue"
import KsSelect from "../../../src/components/Form/KsSelect/KsSelect.vue"
import KsSearch from "../../../src/components/Form/KsSearch.vue"
import KsInputNumber from "../../../src/components/Form/KsInputNumber.vue"
import KsDatePicker from "../../../src/components/Form/KsDatePicker.vue"
import KsTimePicker from "../../../src/components/Form/KsTimePicker.vue"
import KsAutocomplete from "../../../src/components/Form/KsAutocomplete.vue"

const meta: Meta = {
    title: "Components/Form/Placeholder consistency",
    parameters: {
        docs: {
            description: {
                component:
                    "Every form control renders its placeholder with the same size, weight and colour, " +
                    "whatever font size the control itself uses. The rule lives in `element-plus.scss` " +
                    "and the `--ks-placeholder-*` tokens; components must not restyle placeholders locally.",
            },
        },
    },
}
export default meta

/** Each control renders an empty placeholder so they can be compared side by side. */
export const AllControls: StoryObj = {
    render: () => ({
        components: {KsInput, KsSelect, KsSearch, KsInputNumber, KsDatePicker, KsTimePicker, KsAutocomplete},
        template: `
            <div style="display: grid; gap: var(--ks-spacing-3); width: 22rem">
                <KsInput class="probe-input" placeholder="Target audience for the token" />
                <KsInput class="probe-textarea" type="textarea" placeholder="Short description of the credential" />
                <KsSelect class="probe-select" placeholder="Space-separated OAuth scopes" />
                <KsSearch class="probe-search" placeholder="Search" />
                <KsInputNumber class="probe-number" placeholder="Retries" />
                <KsDatePicker class="probe-date" placeholder="Pick a date" />
                <KsTimePicker class="probe-time" placeholder="Pick a time" />
                <KsAutocomplete class="probe-autocomplete" placeholder="Namespace" :fetchSuggestions="() => []" />
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        const placeholders: Record<string, string> = {
            "probe-input": ".kel-input__inner",
            "probe-textarea": ".kel-textarea__inner",
            "probe-search": ".kel-input__inner",
            "probe-number": ".kel-input__inner",
            "probe-date": ".kel-input__inner",
            "probe-time": ".kel-input__inner",
            "probe-autocomplete": ".kel-input__inner",
        }

        const styles = Object.entries(placeholders).map(([root, inner]) => {
            const element = canvasElement.querySelector(`.${root} ${inner}`)
            expect(element, `${root} renders an input`).not.toBeNull()
            const {fontSize, fontWeight, color} = getComputedStyle(element!, "::placeholder")
            return {control: root, fontSize, fontWeight, color}
        })

        // The select paints a span rather than a real ::placeholder, so it is read separately.
        const selectPlaceholder = canvasElement.querySelector(".probe-select .kel-select__placeholder")
        expect(selectPlaceholder, "the select renders a placeholder").not.toBeNull()
        const {fontSize, fontWeight, color} = getComputedStyle(selectPlaceholder!)
        styles.push({control: "probe-select", fontSize, fontWeight, color})

        // --ks-font-size-xs is a calc() over the font scale, so it has to be resolved by the browser.
        const probe = document.createElement("span")
        probe.style.fontSize = "var(--ks-font-size-xs)"
        probe.style.fontWeight = "var(--ks-font-weight-regular)"
        canvasElement.append(probe)
        const expected = {
            fontSize: getComputedStyle(probe).fontSize,
            fontWeight: getComputedStyle(probe).fontWeight,
            color: styles[0].color,
        }
        probe.remove()

        for (const style of styles) {
            expect(style, `${style.control} follows the placeholder contract`).toEqual({
                control: style.control,
                ...expected,
            })
        }
    },
}
