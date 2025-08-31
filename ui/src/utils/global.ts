declare global {
    interface String {
        capitalize(): string;
        hashCode(): number;
    }
}

export function capitalize(str: string) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}

String.prototype.capitalize = function () {
    return capitalize(this.toString());
}

export function hashCode(str: string) {
    let hash = 0;
    if (str.length === 0) return hash;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = (hash << 5) - hash + char;
        hash |= 0; // Convert to 32bit integer
    }
    return hash;
}

String.prototype.hashCode = function () {
    return hashCode(this.toString());
}