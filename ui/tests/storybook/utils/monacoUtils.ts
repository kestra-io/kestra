import {expect, userEvent, waitFor, within} from "storybook/test";

export function getMonacoFilter(canvas: ReturnType<typeof within>) {
    return within(canvas.getByTestId("monaco-filter")).getByTestId("monaco-editor");
}

export async function assertMonacoFilterContentToBe(canvas: ReturnType<typeof within>, expectedText: string): Promise<void> {
    // We need to replace non-breaking spaces with regular spaces because Monaco editor uses non-breaking spaces
    await waitFor(() => expect(getMonacoFilter(canvas)).toHaveTextContent(expectedText, {normalizeWhitespace: true}));
}

export async function getMonacoFilterInput(canvas: ReturnType<typeof within>): Promise<HTMLElement> {
    return waitFor(() => within(getMonacoFilter(canvas)).getByRole("textbox"));
}

export async function refreshMonacoFilter(canvas: ReturnType<typeof within>) {
    await userEvent.click(canvas.getByTestId("trigger-refresh-button"));
}

export async function clearMonacoInput(user: ReturnType<typeof userEvent.setup>, canvas: ReturnType<typeof within>): Promise<void> {
    return user.clear(await getMonacoFilterInput(canvas))
}

export function isColoredAsError(element: HTMLElement): boolean {
    const rgb = window.getComputedStyle(element)!.color!.match(/\d+/g)!.map(Number);
    // Assert red is dominant (e.g., red > 200, green & blue < 100)
    return rgb[0] > 150 && rgb[1] < 60 && rgb[2] < 60;
}
