// Vue falls native listeners through to a component's root element; Vue tools v4 rejects them unless typed here.
type Listener<K extends keyof HTMLElementEventMap> = (event: HTMLElementEventMap[K]) => void

declare module "vue" {
    interface AllowedComponentProps {
        onBlur?: Listener<"blur">
        onClick?: Listener<"click">
        onContextmenu?: Listener<"contextmenu">
        onDblclick?: Listener<"dblclick">
        onDragleave?: Listener<"dragleave">
        onDragover?: Listener<"dragover">
        onDragstart?: Listener<"dragstart">
        onDrop?: Listener<"drop">
        onFocus?: Listener<"focus">
        onKeydown?: Listener<"keydown">
        onKeyup?: Listener<"keyup">
        onMousedown?: Listener<"mousedown">
        onMouseenter?: Listener<"mouseenter">
        onMouseleave?: Listener<"mouseleave">
        onPaste?: Listener<"paste">
        onPointerdown?: Listener<"pointerdown">
    }
}

export {}
