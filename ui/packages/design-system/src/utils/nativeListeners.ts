// Vue falls native listeners through to a component's root element; Vue tools v4 rejects them unless typed here.
declare module "vue" {
    interface AllowedComponentProps {
        onClick?: (event: HTMLElementEventMap["click"]) => void
        onKeydown?: (event: HTMLElementEventMap["keydown"]) => void
    }
}

export {}
