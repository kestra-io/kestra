import {describe, it, expect} from "vitest"
import {readFileSync} from "fs"
import {resolve} from "path"

describe("InputsForm - Issue #16045: URL strings in SELECT/MULTISELECT", () => {
    it("should NOT use KsMarkdown for SELECT option labels", () => {
        const filePath = resolve(__dirname, "../../../src/components/inputs/InputsForm.vue")
        const content = readFileSync(filePath, "utf-8")

        // Find the SELECT section
        const selectStartIdx = content.indexOf("v-if=\"input.type === 'SELECT' && !input.isRadio\"")
        const selectSectionStart = content.lastIndexOf("<KsSelect", selectStartIdx)
        const selectSectionEnd = content.indexOf("</KsSelect>", selectSectionStart)
        const selectSection = content.substring(selectSectionStart, selectSectionEnd)

        // KsOption should NOT have KsMarkdown child
        expect(selectSection).toContain("<KsOption")
        expect(selectSection).toContain(":label=\"item\"")
        
        // The critical check: no KsMarkdown inside KsOption
        const optionStartIdx = selectSection.indexOf("<KsOption")
        const optionEndIdx = selectSection.indexOf("/>")
        const optionContent = selectSection.substring(optionStartIdx, optionEndIdx)
        
        expect(optionContent).not.toContain("KsMarkdown")
    })

    it("should NOT use KsMarkdown for MULTISELECT option labels", () => {
        const filePath = resolve(__dirname, "../../../src/components/inputs/InputsForm.vue")
        const content = readFileSync(filePath, "utf-8")

        // Find the MULTISELECT section
        const multiSelectStartIdx = content.indexOf("v-if=\"input.type === 'MULTISELECT'\"")
        const multiSelectSectionStart = content.lastIndexOf("<KsSelect", multiSelectStartIdx)
        const multiSelectSectionEnd = content.indexOf("</KsSelect>", multiSelectSectionStart)
        const multiSelectSection = content.substring(multiSelectSectionStart, multiSelectSectionEnd)

        // KsOption should NOT have KsMarkdown child
        expect(multiSelectSection).toContain("<KsOption")
        expect(multiSelectSection).toContain(":label=\"item\"")
        
        // The critical check: no KsMarkdown inside KsOption
        const optionStartIdx = multiSelectSection.indexOf("<KsOption")
        const optionEndIdx = multiSelectSection.indexOf("/>")
        const optionContent = multiSelectSection.substring(optionStartIdx, optionEndIdx)
        
        expect(optionContent).not.toContain("KsMarkdown")
    })

    it("should use :label prop for displaying option text", () => {
        const filePath = resolve(__dirname, "../../../src/components/inputs/InputsForm.vue")
        const content = readFileSync(filePath, "utf-8")

        // For SELECT
        const selectStartIdx = content.indexOf("v-if=\"input.type === 'SELECT' && !input.isRadio\"")
        const selectSectionStart = content.lastIndexOf("<KsSelect", selectStartIdx)
        const selectSectionEnd = content.indexOf("</KsSelect>", selectSectionStart)
        const selectSection = content.substring(selectSectionStart, selectSectionEnd)

        expect(selectSection).toContain(":label=\"item\"")
        expect(selectSection).toContain(":value=\"item\"")

        // For MULTISELECT
        const multiSelectStartIdx = content.indexOf("v-if=\"input.type === 'MULTISELECT'\"")
        const multiSelectSectionStart = content.lastIndexOf("<KsSelect", multiSelectStartIdx)
        const multiSelectSectionEnd = content.indexOf("</KsSelect>", multiSelectSectionStart)
        const multiSelectSection = content.substring(multiSelectSectionStart, multiSelectSectionEnd)

        expect(multiSelectSection).toContain(":label=\"item\"")
        expect(multiSelectSection).toContain(":value=\"item\"")
    })
})
