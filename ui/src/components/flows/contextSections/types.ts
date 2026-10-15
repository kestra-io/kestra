export interface DataChip {
    label: string
    expr?: string
    type?: string
    // Shared by every chip derived from the same underlying entry (e.g. read()/fileURI() for one
    // file), so the section header can count entries instead of chips.
    groupKey?: string
}

export interface DataSection {
    key: string
    label: string
    chips: DataChip[]
    isNew?: boolean
}

// What a provider actually fetches and what gets cached: a translation *key*, not a resolved label,
// so the cache never bakes in one UI language and the fetch layer can translate it fresh on every read.
export interface ProviderSection {
    key: string
    labelKey: string
    chips: DataChip[]
    isNew?: boolean
}

export interface ContextSectionInput {
    namespace: string
    tenant?: string
}

export type ContextSectionProvider = (input: ContextSectionInput) => Promise<ProviderSection | null>
