export interface DataChip {
    label: string
    expr?: string
    type?: string
}

export interface DataSection {
    key: string
    label: string
    chips: DataChip[]
    isNew?: boolean
}

export interface ContextSectionInput {
    namespace: string
    tenant?: string
    t: (key: string) => string
}

export type ContextSectionProvider = (input: ContextSectionInput) => Promise<DataSection | null>
