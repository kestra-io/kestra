export function uid() {
    return String.fromCharCode(Math.floor(Math.random() * 26) + 97) +
        Math.random().toString(16).slice(2) +
        Date.now().toString(16).slice(4);
}
