import {useEventListener, useStorage} from "@vueuse/core"
import {EditorElement, Panel, Tab} from "../utils/multiPanelTypes"
import {ref} from "vue"

export const STORED_PANELS_RELOAD_EVENT = "kestra-stored-panels-reload"

export interface PreSerializedPanel {
    tabs: string[];
    activeTab: string | undefined;
    size: number;
}

export function useStoredPanels(key: string | undefined, editorElements: Pick<EditorElement, "deserialize">[], defaultPanels: string[] = [], preSerializePanels?: (panels: Panel[]) => PreSerializedPanel[]) {
    const preSerializePanelsFn = preSerializePanels ?? ((ps: Panel[]) => ps.map(p => ({
        tabs: p.tabs.map(t => t.uid),
        activeTab: p.activeTab?.uid,
        size: p.size,
    })))

    /**
     * function called on mount to deserialize tabs from storage
     * NOTE: if a tab is not relevant anymore, it will be ignored
     * hence the "allowCreate = false".
     * @param tags
     */
    function deserializeTabTags(uids: string[]): Tab[] {
        return uids.map(uid => {
            for (const element of editorElements) {
                const deserializedTab = element.deserialize(uid, false)
                if (deserializedTab) {
                    return deserializedTab
                }
            }
        }).filter(t => t !== undefined)
    }

    const initialPanels = deserializeTabTags(defaultPanels).map((t) => {
            return {
                activeTab: t,
                tabs: [t],
                size: 100 / defaultPanels.length,
            }
        })

    function write(v: Panel[]){
        return JSON.stringify(preSerializePanelsFn(v))
    }

    function read(v?: string | null) {
        if(!v) return []
        const rawPanels: PreSerializedPanel[] = JSON.parse(v)
        const convertedPanels = rawPanels
            .map((p):Panel => {
                const tabsConverted = deserializeTabTags(p.tabs)
                const activeTab = tabsConverted.find((t) => t.uid === p.activeTab) ?? tabsConverted[0]
                return {
                    activeTab,
                    tabs: tabsConverted,
                    size: p.size,
                }
            })
            .filter((p) => p.tabs.length)

        return convertedPanels
    }

    const panels = key ? useStorage<Panel[]>(
        key,
        initialPanels,
        undefined,
        {
            listenToStorageChanges: false,
            serializer: {write, read},
        },
    ) : ref(initialPanels)

    if (key) {
        useEventListener(window, STORED_PANELS_RELOAD_EVENT, (event: Event) => {
            if (event instanceof CustomEvent && event.detail === key) {
                panels.value = read(localStorage.getItem(key))
            }
        })
    }

    function saveState(altKey: string | undefined) {
        if (altKey) {
            // Save the current state to local storage
            localStorage.setItem(altKey, write(panels.value))
        }
    }

    return {panels, saveState}
}
