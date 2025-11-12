export interface Tab {
    uid: string
    button: {
        icon: any
        label: string
    },
    component: any
}

export interface TabLive extends Tab {
    potential?: boolean
    fromPanel?: boolean
    dirty?: boolean,
}

export interface Panel<T extends Tab = Tab> {
    size: number;
    tabs: T[],
    dragover?: boolean,
    activeTab: T,
}

export interface EditorElement extends Tab {
    prepend?: boolean,
    deserialize: (uid: string, allowCreate: boolean) => Tab | undefined
}