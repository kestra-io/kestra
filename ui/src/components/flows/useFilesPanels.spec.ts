import {beforeEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {h, inject, ref, type Ref} from "vue";

import {getTabFromFilesTab, useFilesPanels} from "./useFilesPanels";
import {FILES_REFRESH_CONTENT_INJECTION_KEY, FILES_UPDATE_CONTENT_INJECTION_KEY} from "../inputs/EditorWrapper.vue";
import {FILES_SAVE_ALL_INJECTION_KEY} from "../inputs/EditorButtonsWrapper.vue";
import type {Panel, TabLive} from "../../utils/multiPanelTypes";

const saveOrCreateFile = vi.fn();

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({saveOrCreateFile}),
}));
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({haveChange: false}),
}));
vi.mock("../../composables/usePanelDefaultSize", () => ({
    usePanelDefaultSize: () => ({defaultSize: ref(50)}),
}));
vi.mock("../inputs/EditorWrapper.vue", () => ({
    default: {name: "EditorWrapper", render: () => null},
    FILES_REFRESH_CONTENT_INJECTION_KEY: Symbol("files-refresh-content"),
    FILES_SET_DIRTY_INJECTION_KEY: Symbol("files-set-dirty"),
    FILES_UPDATE_CONTENT_INJECTION_KEY: Symbol("files-update-content"),
}));
vi.mock("../inputs/FileExplorer.vue", () => ({
    FILES_CLOSE_TAB_INJECTION_KEY: Symbol("files-close-tab"),
    FILES_OPEN_TAB_INJECTION_KEY: Symbol("files-open-tab"),
}));
vi.mock("../inputs/EditorButtonsWrapper.vue", () => ({
    FILES_SAVE_ALL_INJECTION_KEY: Symbol("files-save-all"),
}));

function editFile(path: string, content: string) {
    const panels: Ref<Panel[]> = ref([]);
    let updateContent!: (payload: {path: string; content: string}) => void;
    let refreshedContents!: Ref<Record<string, {content: string}>>;
    let saveAll!: () => Promise<void>;

    const Child = {
        setup() {
            updateContent = inject(FILES_UPDATE_CONTENT_INJECTION_KEY)!;
            refreshedContents = inject(FILES_REFRESH_CONTENT_INJECTION_KEY)!;
            saveAll = inject(FILES_SAVE_ALL_INJECTION_KEY)! as () => Promise<void>;
            return () => null;
        },
    };

    mount({
        setup() {
            useFilesPanels(panels, ref("io.kestra.test"));
            const tab = getTabFromFilesTab({name: path, path, extension: path.split(".").pop()!, flow: false, dirty: false});
            panels.value = [{activeTab: tab, tabs: [tab], size: 50}];
            return () => h(Child);
        },
    });

    updateContent({path, content});
    const tab = panels.value[0].tabs[0] as TabLive;
    tab.dirty = true;
    return {tab, updateContent, refreshedContents, saveAll};
}

describe("useFilesPanels save all", () => {
    beforeEach(() => {
        saveOrCreateFile.mockReset();
    });

    it("should give the editor the saved content as its new baseline", async () => {
        saveOrCreateFile.mockResolvedValue(undefined);
        const {tab, refreshedContents, saveAll} = editFile("data.txt", "edited");

        await saveAll();

        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt", content: "edited"});
        expect(tab.dirty).toBe(false);
        expect(refreshedContents.value["data.txt"]).toEqual({content: "edited"});
    });

    it("should keep a file dirty when it was edited while it was being saved", async () => {
        let finishSave: () => void = () => {};
        saveOrCreateFile.mockReturnValue(new Promise<void>((resolve) => finishSave = resolve));
        const {tab, updateContent, refreshedContents, saveAll} = editFile("data.txt", "edited");

        const saving = saveAll();
        updateContent({path: "data.txt", content: "edited again"});
        finishSave();
        await saving;

        expect(tab.dirty).toBe(true);
        expect(refreshedContents.value["data.txt"]).toBeUndefined();
    });
});
