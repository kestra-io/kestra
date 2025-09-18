import fs from "fs";
import path from "path";

export type WorkingDir = string;

/**
 * helper to handle working dir passed in CLI
 * @param workingDir by default the repository root
 */
export function getWorkingDir(workingDir?: string): WorkingDir {
    if (!workingDir) {
        throw new Error(
            "an absolute working dir is for required, this can be improved for better DX",
        );
    }
    if (!path.isAbsolute(workingDir)) {
        throw new Error(`Working directory must be an absolute path: ${workingDir}`);
    }

    if (!fs.existsSync(workingDir)) {
        throw new Error(`Working directory does not exist: ${workingDir}`);
    }

    const stat = fs.statSync(workingDir);
    if (!stat.isDirectory()) {
        throw new Error(`Working directory is not a directory: ${workingDir}`);
    }

    return workingDir;
}
