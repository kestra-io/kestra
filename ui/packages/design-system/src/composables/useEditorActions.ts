import * as monaco from "monaco-editor/esm/vs/editor/editor.api"

type AnyEditor = monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor

interface FoldingRegions {
    length: number
    isCollapsed: (index: number) => boolean
    getStartLineNumber: (index: number) => number
    toRegion: (index: number) => unknown
}

interface FoldingModel {
    textModel: {getLineContent: (line: number) => string}
    regions: FoldingRegions
    toggleCollapseState: (regions: unknown[]) => void
}

export interface EditorActionsContext {
    t: (key: string) => string
    readOnly: boolean
    inline: boolean
    lang?: string
    canFoldFromNavbar: boolean
    onSave: (value?: string) => void
    onExecute: (value?: string) => void
    onConfirm: (value?: string) => void
    autoFold: (shouldFold?: boolean) => void
}

export function registerEditorActions(editor: AnyEditor, ctx: EditorActionsContext) {
    const {KeyCode, KeyMod} = monaco

    if (!ctx.readOnly) {
        editor.addAction({
            id: "kestra-save",
            label: ctx.t("save"),
            keybindings: [KeyMod.CtrlCmd | KeyCode.KeyS],
            contextMenuGroupId: "navigation",
            contextMenuOrder: 1.5,
            run: (e) => ctx.onSave(e.getValue()),
        })
    } else if (ctx.lang === "json") {
        (editor as monaco.editor.IStandaloneCodeEditor).getAction?.("editor.action.formatDocument")?.run()
    }

    editor.addAction({
        id: "moveCursor",
        label: "Move cursor",
        run: (e, args?: {lineNumber: number, column: number}) => {
            if (!args?.lineNumber || !args?.column) return
            e.setPosition({lineNumber: args.lineNumber, column: args.column})
            e.revealPositionInCenter({lineNumber: args.lineNumber, column: args.column})
            e.focus()
        },
    })

    editor.addAction({
        id: "kestra-execute",
        label: ctx.t("execute flow behaviour"),
        keybindings: [KeyMod.CtrlCmd | KeyCode.KeyE],
        contextMenuGroupId: "navigation",
        contextMenuOrder: 1.5,
        run: (e) => ctx.onExecute(e.getValue()),
    })

    editor.addAction({
        id: "confirm",
        label: ctx.t("confirm"),
        keybindings: [KeyMod.CtrlCmd | KeyCode.Enter],
        contextMenuGroupId: "navigation",
        contextMenuOrder: 1.5,
        run: (e) => ctx.onConfirm(e.getValue()),
    })

    if (ctx.inline) {
        editor.addAction({id: "prevent-ctrl-h", label: "Prevent CTRL + H", keybindings: [KeyMod.CtrlCmd | KeyCode.KeyH], run: () => {}})
        editor.addAction({id: "prevent-f1", label: "Prevent F1", keybindings: [KeyCode.F1], run: () => {}})
        if (!ctx.readOnly) {
            editor.addAction({id: "prevent-ctrl-f", label: "Prevent CTRL + F", keybindings: [KeyMod.CtrlCmd | KeyCode.KeyF], run: () => {}})
        }
    }

    if (!ctx.canFoldFromNavbar) return

    editor.addAction({
        id: "fold-multiline",
        label: ctx.t("fold_all_multi_lines"),
        keybindings: [KeyCode.F10],
        contextMenuGroupId: "fold",
        contextMenuOrder: 1.5,
        async run(e) {
            const foldingContrib = e.getContribution("editor.contrib.folding") as unknown as {
                getFoldingModel?: () => Promise<FoldingModel>
            } | null
            const foldingModel = await foldingContrib?.getFoldingModel?.()
            if (!foldingModel) return

            const {textModel, regions} = foldingModel
            const toToggle = []
            for (let i = regions.length - 1; i >= 0; i--) {
                if (regions.isCollapsed(i) === false) {
                    const startLineNumber = regions.getStartLineNumber(i)
                    if (textModel.getLineContent(startLineNumber).trim().endsWith("|")) {
                        toToggle.push(regions.toRegion(i))
                    }
                }
            }
            foldingModel.toggleCollapseState(toToggle)
        },
    })

    if (localStorage.getItem("autofoldTextEditor") === "true") {
        ctx.autoFold(true)
    }
}
