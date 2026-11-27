// Teleport targets that App.vue and AppTopNavBar.vue render in the app, created as static body nodes
// before any story mounts, since a decorator-rendered sibling can be created after the Teleport resolves.
export const LAYOUT_SLOT_IDS = ["topnav-title-slot", "topnav-description-slot", "topnav-actions-slot", "bottom-actions-slot"]

export function installLayoutSlots(): void {
    for (const id of LAYOUT_SLOT_IDS) {
        if (!document.getElementById(id)) {
            const el = document.createElement(id === "topnav-title-slot" ? "span" : "div")
            el.id = id
            document.body.appendChild(el)
        }
    }
}
