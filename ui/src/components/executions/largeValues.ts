// Monaco holds the whole document on the main thread, so a large value froze the tab for tens
// of seconds. Past this budget a value is offered as a download instead of being rendered.
export const MAX_INLINE_BYTES = 10 * 1024;

export function isTooLargeToRender(text: string | undefined): boolean {
    return (text?.length ?? 0) > MAX_INLINE_BYTES;
}

export function downloadJson(text: string, filename: string): void {
    const url = URL.createObjectURL(new Blob([text], {type: "application/json"}));
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
}
