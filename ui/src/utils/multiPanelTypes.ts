export interface Tab {
    uid: string,
    button: {
        icon: any,
        label: string
    },
    potential?: boolean
    fromPanel?: boolean
    dirty?: boolean,
    component: any
}

export interface Panel {
    size: number;
    tabs: Tab[],
    dragover?: boolean,
    activeTab: Tab,
}

export interface EditorElement {
    button: {
        icon: any,
        label: string
    },
    value: string,
    component: any,
    prepend?: boolean,
    deserialize?: (value: string, allowCreate: boolean) => Tab | undefined
}

export interface DeserializableEditorElement extends EditorElement {
    deserialize: (value: string, allowCreate: boolean) => Tab | undefined
}