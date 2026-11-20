#!/usr/bin/env bash
# Usage: repro.sh <tsc>. Two identical copies of `pkg`, whose Arrayable is only reachable through a
# wildcard subpath export: 6.0.3 and 7.0.2 emit it, 7.1 reports TS2883.
set -euo pipefail
TSC="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
DIR="$(mktemp -d)"
cd "$DIR"

for copy in lib app; do
    mkdir -p "$copy/node_modules/pkg/es/utils"
    cat > "$copy/node_modules/pkg/package.json" <<'EOF'
{
  "name": "pkg",
  "version": "1.0.0",
  "types": "es/index.d.ts",
  "exports": {
    ".": {"types": "./es/index.d.ts", "import": "./es/index.mjs"},
    "./es/*.mjs": {"types": "./es/*.d.ts", "import": "./es/*.mjs"},
    "./es/*": {"types": ["./es/*.d.ts", "./es/*/index.d.ts"], "import": "./es/*.mjs"}
  }
}
EOF
    echo 'export type Arrayable<T> = T | T[];' > "$copy/node_modules/pkg/es/utils/typescript.d.ts"
    cat > "$copy/node_modules/pkg/es/index.d.ts" <<'EOF'
import type { Arrayable } from "./utils/typescript.js";
export interface Rule { max?: number }
export type Rules = Record<string, Arrayable<Rule>>;
EOF
done

mkdir -p lib/src app/src
cat > lib/src/rules.ts <<'EOF'
import type { Rules } from "pkg";

export function useRules(rules: Rules) {
    return { first: Object.values(rules)[0] };
}
EOF
echo 'export type { Rules } from "pkg";' > app/src/types.ts
cat > app/tsconfig.json <<'EOF'
{
  "compilerOptions": {
    "strict": true, "declaration": true, "emitDeclarationOnly": true, "outDir": "out", "rootDir": "..",
    "target": "es2022", "module": "esnext", "moduleResolution": "bundler", "types": []
  },
  "files": ["src/types.ts", "../lib/src/rules.ts"]
}
EOF

node "$TSC" -p app --pretty false && echo "OK: $(grep -o 'import("[^"]*")' app/out/lib/src/rules.d.ts | sort -u | tr '\n' ' ')"
