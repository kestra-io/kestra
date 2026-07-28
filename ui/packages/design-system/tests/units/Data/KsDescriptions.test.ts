import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KsDescriptions from "../../../src/components/Data/KsDescriptions.vue"
import KsDescriptionsItem from "../../../src/components/Data/KsDescriptionsItem.vue"

// Mount the two components directly (they import Element Plus themselves) rather than the whole
// design-system plugin, to avoid unrelated heavy deps in the index.
const globalConfig = {}

describe("KsDescriptions", () => {
    // Regression: ElDescriptions collects rows by filtering slot children whose component name is
    // exactly "ElDescriptionsItem". KsDescriptionsItem must be detectable as such, otherwise every
    // row is dropped and the descriptions block renders empty.
    test("renders rows declared with KsDescriptionsItem", () => {
        const wrapper = mount({
            components: {KsDescriptions, KsDescriptionsItem},
            template: `
                <ks-descriptions border :column="1">
                    <ks-descriptions-item label="Status">RUNNING</ks-descriptions-item>
                </ks-descriptions>
            `,
        }, {global: globalConfig})

        expect(wrapper.text()).toContain("Status")
        expect(wrapper.text()).toContain("RUNNING")
    })

    test("renders the #label slot of a KsDescriptionsItem", () => {
        const wrapper = mount({
            components: {KsDescriptions, KsDescriptionsItem},
            template: `
                <ks-descriptions border :column="1">
                    <ks-descriptions-item>
                        <template #label>Custom Label</template>
                        VALUE
                    </ks-descriptions-item>
                </ks-descriptions>
            `,
        }, {global: globalConfig})

        expect(wrapper.text()).toContain("Custom Label")
        expect(wrapper.text()).toContain("VALUE")
    })
})
