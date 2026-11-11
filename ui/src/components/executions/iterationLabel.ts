import type {ComposerTranslation} from "vue-i18n"
import {capForDisplay} from "../../utils/utils"

export function iterationLabel(t: ComposerTranslation, run: {value?: unknown}, position: number): string {
    return run.value ? capForDisplay(String(run.value)) : t("iteration_number", {number: position})
}
