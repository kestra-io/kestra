export const ZERO_WIDTH_CHAR_REGEX = /\u200B/;
export const ZERO_WIDTH_CHAR_MESSAGE = "Id namespace and tenantId cannot contain zero-width Unicode characters (e.g. U+200B).";

export const hasZeroWidthChar = (value?: string) => typeof value === "string" && ZERO_WIDTH_CHAR_REGEX.test(value);

export const hasZeroWidthCharInIdentifiers = (
    candidate?: {id?: string; namespace?: string; tenantId?: string},
    fields: (keyof {id?: string; namespace?: string; tenantId?: string})[] = ["id", "namespace", "tenantId"]
) => fields.some(field => hasZeroWidthChar(candidate?.[field]));
