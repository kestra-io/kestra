export function isColoredAsError(element: HTMLElement): boolean {
    const rgb = window.getComputedStyle(element)!.color!.match(/\d+/g)!.map(Number);
    // Assert red is dominant (e.g., red > 200, green & blue < 100)
    return rgb[0] > 150 && rgb[1] < 60 && rgb[2] < 60;
}
