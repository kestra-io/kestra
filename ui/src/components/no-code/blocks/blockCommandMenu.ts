import ArrowRightBold from "vue-material-design-icons/ArrowRightBold.vue"
import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
import ContentSave from "vue-material-design-icons/ContentSave.vue"
import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
import PlusCircleOutline from "vue-material-design-icons/PlusCircleOutline.vue"
import type {BlockCommandMenuItem} from "./BlockCommandMenu.vue"
import {parentPathFromLaneSentinel, sectionFromSentinel} from "./blockSections"
import type {BlockSection} from "../../../utils/flowableBlockOps"

type Translate = (key: string, named?: Record<string, unknown>) => string

const MENU_SECTION_ORDER: BlockSection[] = ["triggers", "tasks", "errors", "finally", "afterExecution"]

export interface BlockCommandMenuContext {
    t: Translate
    focusedId: string | undefined
    focusedBlockDisplayName: () => string
    sectionDisplayLabel: (section: BlockSection) => string
    laneDisplayLabelFromPath: (parentPath: string) => string
    close: () => void
    addAfterFocused: () => void
    addBeforeFocused: () => void
    insertInSection: (section: BlockSection) => void
    openFocused: () => void
    duplicateFocused: () => void
    deleteFocused: () => void
    goToSection: (section: BlockSection) => void
    saveFlow: () => void
}

function focusedSubject(ctx: BlockCommandMenuContext) {
    const sentinelSection = sectionFromSentinel(ctx.focusedId)
    const laneParentPath = parentPathFromLaneSentinel(ctx.focusedId)
    return {
        sentinelSection,
        laneParentPath,
        isRealBlock: Boolean(ctx.focusedId) && !sentinelSection && !laneParentPath,
    }
}

export function buildCommandMenuContextLabel(ctx: BlockCommandMenuContext): string {
    const {sentinelSection, laneParentPath} = focusedSubject(ctx)
    if (sentinelSection) {
        return ctx.t("block_editor.command_menu.context_selected", {name: ctx.sectionDisplayLabel(sentinelSection)})
    }
    if (laneParentPath) {
        return ctx.t("block_editor.command_menu.context_selected", {name: ctx.laneDisplayLabelFromPath(laneParentPath)})
    }
    return ctx.focusedId
        ? ctx.t("block_editor.command_menu.context_selected", {name: ctx.focusedBlockDisplayName()})
        : ctx.t("block_editor.command_menu.context_flow")
}

export function buildCommandMenuItems(ctx: BlockCommandMenuContext): BlockCommandMenuItem[] {
    const {t} = ctx
    const {sentinelSection, laneParentPath, isRealBlock} = focusedSubject(ctx)
    const then = (action: () => void) => () => {
        ctx.close()
        action()
    }

    const insertTitle = sentinelSection
        ? t("block_editor.command_menu.insert_in_section", {section: ctx.sectionDisplayLabel(sentinelSection)})
        : laneParentPath
            ? t("block_editor.command_menu.insert_in_section", {section: ctx.laneDisplayLabelFromPath(laneParentPath)})
            : ctx.focusedId
                ? t("block_editor.command_menu.insert_after", {name: ctx.focusedBlockDisplayName()})
                : t("block_editor.command_menu.insert_at_end")

    const items: BlockCommandMenuItem[] = [{
        id: "insert",
        group: t("block_editor.command_menu.group_insert"),
        title: insertTitle,
        icon: PlusCircleOutline,
        shortcut: "A",
        run: then(ctx.addAfterFocused),
    }]

    if (isRealBlock) {
        items.push({
            id: "insert-before",
            group: t("block_editor.command_menu.group_insert"),
            title: t("block_editor.command_menu.insert_before", {name: ctx.focusedBlockDisplayName()}),
            icon: PlusCircleOutline,
            shortcut: "⇧A",
            run: then(ctx.addBeforeFocused),
        })
    }

    for (const section of MENU_SECTION_ORDER) {
        items.push({
            id: `insert-${section}`,
            group: t("block_editor.command_menu.group_insert"),
            title: t("block_editor.command_menu.insert_kind", {kind: ctx.sectionDisplayLabel(section)}),
            icon: PlusCircleOutline,
            run: then(() => ctx.insertInSection(section)),
        })
    }

    if (isRealBlock) {
        const name = ctx.focusedBlockDisplayName()
        items.push({
            id: "open",
            group: t("block_editor.command_menu.group_block"),
            title: t("block_editor.command_menu.open", {name}),
            icon: OpenInNew,
            shortcut: "↵",
            run: then(ctx.openFocused),
        })
        items.push({
            id: "duplicate",
            group: t("block_editor.command_menu.group_block"),
            title: t("block_editor.command_menu.duplicate", {name}),
            icon: ContentCopy,
            shortcut: "D",
            run: then(ctx.duplicateFocused),
        })
        items.push({
            id: "delete",
            group: t("block_editor.command_menu.group_block"),
            title: t("block_editor.command_menu.delete", {name}),
            icon: DeleteOutline,
            shortcut: "⌫",
            run: then(ctx.deleteFocused),
        })
    }

    for (const section of MENU_SECTION_ORDER) {
        items.push({
            id: `goto-${section}`,
            group: t("block_editor.command_menu.group_goto"),
            title: t("block_editor.command_menu.goto", {section: ctx.sectionDisplayLabel(section)}),
            icon: ArrowRightBold,
            run: then(() => ctx.goToSection(section)),
        })
    }

    items.push({
        id: "save",
        group: t("block_editor.command_menu.group_flow"),
        title: t("block_editor.command_menu.save"),
        icon: ContentSave,
        shortcut: "⌘S",
        run: then(ctx.saveFlow),
    })

    return items
}
