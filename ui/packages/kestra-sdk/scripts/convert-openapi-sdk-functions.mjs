import fs from "fs/promises";
import path from "path";
import ts from "typescript";

const printer = ts.createPrinter({
    newLine: ts.NewLineKind.LineFeed,
});

const isExported = (node) =>
    Array.isArray(node.modifiers) &&
    node.modifiers.some(
        (modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword,
    );

const isArrowFunction = (node) => Boolean(node && ts.isArrowFunction(node));

const isConvertibleExport = (statement) => {
    if (!ts.isVariableStatement(statement)) {
        return false;
    }

    if (!isExported(statement)) {
        return false;
    }

    const declarations = statement.declarationList.declarations;
    return (
        declarations.length === 1 &&
        ts.isIdentifier(declarations[0].name) &&
        isArrowFunction(declarations[0].initializer)
    );
};

const createFunctionDeclarationFromVariable = (statement) => {
    const declaration = statement.declarationList.declarations[0];
    const arrow = declaration.initializer;
    const exported =
        statement.modifiers?.filter(
            (modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword,
        ) ?? [];
    const isAsync =
        arrow.modifiers?.some(
            (modifier) => modifier.kind === ts.SyntaxKind.AsyncKeyword,
        ) ?? false;
    const body = ts.isBlock(arrow.body)
        ? arrow.body
        : ts.factory.createBlock(
              [ts.factory.createReturnStatement(arrow.body)],
              true,
          );

    return ts.factory.createFunctionDeclaration(
        [
            ...exported,
            ...(isAsync
                ? [ts.factory.createModifier(ts.SyntaxKind.AsyncKeyword)]
                : []),
        ],
        arrow.asteriskToken,
        declaration.name,
        arrow.typeParameters,
        arrow.parameters,
        arrow.type,
        body,
    );
};

const getSourceFiles = async (directory) => {
    const entries = await fs.readdir(directory, { withFileTypes: true });
    const files = [];

    for (const entry of entries) {
        const fullPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            files.push(...(await getSourceFiles(fullPath)));
            continue;
        }

        if (entry.isFile() && entry.name.endsWith(".ts")) {
            files.push(fullPath);
        }
    }

    return files;
};

const transformFile = async (filePath) => {
    const originalText = await fs.readFile(filePath, "utf8");
    const sourceFile = ts.createSourceFile(
        filePath,
        originalText,
        ts.ScriptTarget.Latest,
        true,
        ts.ScriptKind.TS,
    );

    const replacements = [];

    for (let i = 0; i < sourceFile.statements.length; i++) {
        const statement = sourceFile.statements[i];
        if (!isConvertibleExport(statement)) {
            continue;
        }

        const nextStatement = createFunctionDeclarationFromVariable(statement);
        let printed = printer
            .printNode(ts.EmitHint.Unspecified, nextStatement, sourceFile)
            .trimEnd();

        // Preserve leading blank lines from the original trivia
        const leadingTrivia = originalText.slice(
            statement.getFullStart(),
            statement.getStart(sourceFile),
        );
        const leadingBlankLines = leadingTrivia.match(/^\n+/)?.[0] ?? "";
        if (leadingBlankLines && leadingBlankLines.includes("\n\n")) {
            printed = leadingBlankLines + printed;
        }

        replacements.push({
            end: statement.end,
            start: statement.getFullStart(),
            text: printed,
        });
    }

    if (replacements.length === 0) {
        return false;
    }

    let updatedText = originalText;
    for (const replacement of replacements.sort(
        (left, right) => right.start - left.start,
    )) {
        const before = updatedText.slice(0, replacement.start);
        const after = updatedText.slice(replacement.end);
        updatedText = `${before}${replacement.text}${after}`;
    }

    if (updatedText !== originalText) {
        await fs.writeFile(
            filePath,
            `${updatedText.endsWith("\n") ? updatedText : `${updatedText}\n`}`,
        );
        return true;
    }

    return false;
};

const main = async () => {
    const outputPath = process.argv[2];
    if (!outputPath) {
        throw new Error(
            "Expected openapi-ts output path as the first argument.",
        );
    }

    const sdkDir = path.join(outputPath, "sdk");
    const files = await getSourceFiles(sdkDir);

    let changed = 0;
    for (const filePath of files) {
        if (await transformFile(filePath)) {
            changed += 1;
        }
    }

    process.stdout.write(`Converted ${changed} file(s) in ${sdkDir}\n`);
};

main().catch((error) => {
    console.error(
        error instanceof Error ? (error.stack ?? error.message) : error,
    );
    process.exit(1);
});
