import {useStorage} from "@vueuse/core";
import {DeserializableEditorElement, Panel, Tab} from "../utils/multiPanelTypes";

interface PreSerializedPanel {
    tabs: string[];
    activeTab: string | undefined;
    size: number;
}

export function useStoredPanels(key: string, editorElements: Pick<DeserializableEditorElement, "deserialize">[], defaultPanels: string[] = [], preSerializePanels?: (panels: Panel[]) => PreSerializedPanel[]) {
    const preSerializePanelsFn = preSerializePanels ?? ((ps: Panel[]) => ps.map(p => ({
        tabs: p.tabs.map(t => t.value),
        activeTab: p.activeTab?.value,
        size: p.size,
    })));

    /**
     * function called on mount to deserialize tabs from storage
     * NOTE: if a tab is not relevant anymore, it will be ignored
     * hence the "allowCreate = false".
     * @param tags
     */
    function deserializeTabTags(tags: string[]): Tab[] {
        return tags.map(tag => {
            for (const element of editorElements) {
                const deserializedTab = element.deserialize(tag, false);
                if (deserializedTab) {
                    return deserializedTab;
                }
            }
        }).filter(t => t !== undefined);
    }

    const panels = useStorage<Panel[]>(
        key,
        deserializeTabTags(defaultPanels).map((t) => {
            return {
                activeTab: t,
                tabs: [t],
                size: 100 / defaultPanels.length
            };
        }),
        undefined,
        {
            serializer: {
                write(v: Panel[]){
                    return JSON.stringify(preSerializePanelsFn(v));
                },
                read(v?: string) {
                    if(!v) return [];
                    const rawPanels: PreSerializedPanel[] = JSON.parse(v);
                    const convertedPanels = rawPanels
                        .filter((p) => p.tabs.length)
                        .map((p):Panel => {
                            const tabsConverted = deserializeTabTags(p.tabs);
                            const activeTab = tabsConverted.find((t) => t.value === p.activeTab) ?? tabsConverted[0];
                            return {
                                activeTab,
                                tabs: tabsConverted,
                                size: p.size
                            };
                        });

                    return convertedPanels
                }
            },
        }
    );

    return panels;
}