export interface Tab {
    uid: string
    button: {
        icon: any
        label: string
        disabled?: boolean
        disabledTooltip?: string
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
    /** Panel width in percent when this element opens its own panel; defaults to an even share. */
    preferredSize?: number,
    deserialize: (uid: string, allowCreate: boolean) => Tab | undefined
}