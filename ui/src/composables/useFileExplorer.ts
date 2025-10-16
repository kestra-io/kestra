import {EditorTabProps, useEditorStore} from "../stores/editor";

export function useFileExplorer() {
    const editorStore = useEditorStore();

    function fileClicked(file: EditorTabProps){
        editorStore.openTab(file);
    }

    function fileCreated(file: EditorTabProps){
        editorStore.openTab(file);
    }

    function fileDeleted(file: EditorTabProps){
        editorStore.closeTab(file);
    }

    return {
        fileClicked,
        fileCreated,
        fileDeleted
    }
}
