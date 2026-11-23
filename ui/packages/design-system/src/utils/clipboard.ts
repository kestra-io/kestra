/**
 * Copies text to the clipboard.
 *
 * Falls back to a hidden textarea + `document.execCommand("copy")` when the Clipboard API
 * is unavailable, since `navigator.clipboard` is undefined in non-secure contexts
 * (plain HTTP on a non-localhost host).
 */
export async function copyToClipboard(text: string): Promise<void> {
    if (navigator.clipboard) {
        await navigator.clipboard.writeText(text)
        return
    }

    const node = document.createElement("textarea")
    node.style.position = "absolute"
    node.style.left = "-9999px"
    node.value = text
    document.body.appendChild(node)
    node.select()
    document.execCommand("copy")
    document.body.removeChild(node)
}
