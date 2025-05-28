declare module "splitpanes" {
    import {DefineComponent} from "vue";

    export interface PaneProps {
        size?: number | string;
        minSize?: number | string;
        maxSize?: number | string;
    }

    export interface SplitpanesProps {
        horizontal?: boolean;
        pushOtherPanes?: boolean;
        dblClickSplitter?: boolean;
        rtl?: boolean;
        firstSplitter?: boolean;
    }

    export interface SplitpanesEmits {
        (
            e:
                | "ready"
                | "resize"
                | "resized"
                | "pane-click"
                | "pane-maximize"
                | "pane-add"
                | "pane-remove"
                | "splitter-click"
        ): void;
    }

    export const Splitpanes: DefineComponent<
        SplitpanesProps,
        any,
        SplitpanesEmits
    >;
    export const Pane: DefineComponent<PaneProps>;
}