export function getCsrfToken(): string | null {
    const meta = document.querySelector("meta[name=\"csrf-token\"]")
    return meta ? meta.getAttribute("content") : null
}
