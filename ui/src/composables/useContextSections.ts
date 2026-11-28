import {computed, ref, watch, type Ref} from "vue"
import {useI18n} from "vue-i18n"
import {fetchContextSections} from "../components/flows/contextSections/fetchContextSections"
import {OSS_CONTEXT_SECTION_PROVIDERS} from "../components/flows/contextSections/providers"
import {useContextSectionsExtension} from "override/components/flows/contextSectionsExtension"
import {useFlowContextSections} from "override/components/flows/flowContextSections"
import type {DataSection} from "../components/flows/contextSections/types"
import {latestRequest} from "../utils/latestRequest"

export function useContextSections(namespace: Ref<string | undefined>) {
    const {t, locale} = useI18n()
    const namespaceSections = ref<DataSection[]>([])

    const providers = [...OSS_CONTEXT_SECTION_PROVIDERS, ...useContextSectionsExtension()]

    const startSections = latestRequest()
    watch([namespace, locale], async ([ns]) => {
        const isLatest = startSections()
        if (!ns) {
            namespaceSections.value = []
            return
        }
        const result = await fetchContextSections(providers, {namespace: ns}, t)
        if (isLatest()) namespaceSections.value = result
    }, {immediate: true})

    // Sections that depend on the flow being edited rather than on its namespace alone (never cached per namespace)
    const flowSections = useFlowContextSections(namespace)
    const sections = computed<DataSection[]>(() => [...namespaceSections.value, ...flowSections.value])

    return {sections}
}
