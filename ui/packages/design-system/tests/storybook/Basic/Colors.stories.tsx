/**
 * list all variable colors in a theme, with their name and value, to make sure they are correct and up-to-date.
 * This is not a visual test, but it can be used to generate a visual test by taking a screenshot of the rendered output.
 */

import type {Meta, StoryObj} from "@storybook/vue3"
import themeVariables from "./Color-variables.json" with {type: "json"}
import { ref } from "vue"

export default {
    title: "Basic/Colors",
} as Meta

type Story = StoryObj

export const AllColors: Story = {
    render: () => ({
        setup() {
            const colorsGroupedByCategory = themeVariables.reduce((groups, name) => {
                const [category] = name.replace(/--ks-/, "").split("-")
                if (!groups[category]) {
                    groups[category] = []
                }
                groups[category].push(name)
                return groups
            }, {} as Record<string, string[]>)

            const copiedValue = ref("<none>")
            let timeout: ReturnType<typeof setTimeout> | null = null

            function copyToClipboard(value: string) {
                navigator.clipboard.writeText(value)
                copiedValue.value = value
                if (timeout) {
                    clearTimeout(timeout)
                }
                timeout = setTimeout(() => {
                    copiedValue.value = "<none>"
                }, 2000)
            }

            return () => (
                <div style="padding:24px;display:flex;flex-direction:column;gap:40px">
                    {Object.entries(colorsGroupedByCategory).map(([category, names]) => (
                        <div key={category} style="display:flex;flex-direction:column;gap:8px">
                            <h2 style="margin:0">{category}</h2>
                            <div style="display:flex;flex-wrap:wrap;gap:12px">
                                {names.map(name => (
                                    <div key={name} style="cursor:pointer;display:flex;align-items:center;gap:12px;min-width:300px;" onClick={() => copyToClipboard(`var(${name})`)}>
                                        <div style={{width: "24px", height: "24px", backgroundColor: `var(${name})`, border: "1px solid #ccc", borderRadius: "4px"}} />
                                        {
                                            copiedValue.value === `var(${name})`?
                                                <span>copied</span>
                                        :   
                                            /* if the category is text, color the text, border, add a border, bg show the background */
                                            <span style={{color: category.includes("text") ? `var(${name})` : undefined, border: category.includes("border") ? `1px solid var(${name})` : undefined, backgroundColor: category.includes("bg") ? `var(${name})` : undefined, padding: "2px 4px", borderRadius: "4px"}}>
                                                {name}
                                            </span>
                                        }
                                    </div>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            )
        }
    }),
}