import {useStorage} from "@vueuse/core";
import {DeserializableEditorElement, Panel, Tab} from "../utils/multiPanelTypes";

export function useStoredPanels(key: string, editorElements: Pick<DeserializableEditorElement, "deserialize">[], defaultPanels: string[] = [], preSerializePanels?: (panels: Panel[]) => {tabs: string[], activeTab: string | undefined, size: number}[]) {
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
        }).filter(t => t !== undefined) as Tab[];
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
                    if(!v) return null;
                    const panels = JSON.parse(v);
                    return panels
                        .filter((p: any) => p.tabs.length)
                        .map((p: {tabs: string[], activeTab: string, size: number}):Panel => {
                            const tabs = deserializeTabTags(p.tabs);
                            const activeTab = tabs.find((t: any) => t.value === p.activeTab) ?? tabs[0];
                            return {
                                activeTab,
                                tabs,
                                size: p.size
                            };
                        });
                }
            },
        }
    );
    return panels;
}