const TITLE_SEPARATOR = "|"

function documentBaseTitle() {
    const separatorIndex = document.title.lastIndexOf(TITLE_SEPARATOR)
    return separatorIndex >= 0
        ? document.title.substring(separatorIndex + 1).trim()
        : document.title
}

export function setDocumentTitle(title?: string | null) {
    const baseTitle = documentBaseTitle()
    const pageTitle = title?.trim()

    if (!pageTitle) {
        document.title = baseTitle
        return
    }

    document.title = baseTitle ? `${pageTitle} | ${baseTitle}` : pageTitle
}
