import {ref, watch, type Ref} from "vue"
import {useI18n} from "vue-i18n"
import {fetchContextSections} from "../components/flows/contextSections/fetchContextSections"
import {OSS_CONTEXT_SECTION_PROVIDERS} from "../components/flows/contextSections/providers"
import {useContextSectionsExtension} from "override/components/flows/contextSectionsExtension"
import type {DataSection} from "../components/flows/contextSections/types"

export function useContextSections(namespace: Ref<string | undefined>) {
    const {t} = useI18n()
    const sections = ref<DataSection[]>([])

    const providers = [...OSS_CONTEXT_SECTION_PROVIDERS, ...useContextSectionsExtension()]

    let latestRequest = 0
    watch(namespace, async (ns) => {
        const requestId = ++latestRequest
        if (!ns) {
            sections.value = []
            return
        }
        const result = await fetchContextSections(providers, {namespace: ns}, t)
        if (requestId === latestRequest) sections.value = result
    }, {immediate: true})

    return {sections}
}
