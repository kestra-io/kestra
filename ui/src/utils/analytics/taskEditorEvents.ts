import {useApiStore} from "../../stores/api"

export function trackContextSectionExpanded(section: string) {
    useApiStore().posthogEvents({type: "CONTEXT_SECTION_EXPANDED", section})
}

export function trackChipInserted(section: string) {
    useApiStore().posthogEvents({type: "CHIP_INSERTED", section})
}

export function trackChipCopied(section: string) {
    useApiStore().posthogEvents({type: "CHIP_COPIED", section})
}

export function trackRequiredFieldJump(field: string) {
    useApiStore().posthogEvents({type: "REQUIRED_FIELD_JUMP", field})
}
