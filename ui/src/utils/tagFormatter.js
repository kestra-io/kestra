/**
 * Format blueprint tags with proper capitalization for abbreviations and special cases
 * This utility handles common tech abbreviations, brand names, and special casing
 */

// Map of lowercase tags to their proper display format
const TAG_DISPLAY_MAP = {
    // ===== Technical Abbreviations (ALL CAPS) =====
    "ai": "AI",
    "api": "API",
    "aws": "AWS",
    "gcp": "GCP",
    "cli": "CLI",
    "sql": "SQL",
    "elt": "ELT",
    "etl": "ETL",
    "ssh": "SSH",
    "ftp": "FTP",
    "http": "HTTP",
    "https": "HTTPS",
    "rest": "REST",
    "json": "JSON",
    "xml": "XML",
    "yaml": "YAML",
    "csv": "CSV",
    "pdf": "PDF",
    "iot": "IoT",
    "ml": "ML",
    "ci": "CI",
    "cd": "CD",
    "cicd": "CI/CD",
    "sdk": "SDK",
    "ide": "IDE",
    "ui": "UI",
    "ux": "UX",
    
    // ===== Special Casing (Mixed Case) =====
    "saas": "SaaS",
    "paas": "PaaS",
    "iaas": "IaaS",
    "devops": "DevOps",
    "devsecops": "DevSecOps",
    "javascript": "JavaScript",
    "typescript": "TypeScript",
    
    // ===== Database/Technology Brands =====
    "postgresql": "PostgreSQL",
    "mysql": "MySQL",
    "mongodb": "MongoDB",
    "mariadb": "MariaDB",
    "elasticsearch": "Elasticsearch",
    "redis": "Redis",
    
    // ===== Cloud/Platform Brands =====
    "github": "GitHub",
    "gitlab": "GitLab",
    "bitbucket": "BitBucket",
    "nodejs": "Node.js",
    "nextjs": "Next.js",
    "vuejs": "Vue.js",
    
    // ===== Tools/Frameworks (Keep Lowercase) =====
    "dbt": "dbt",
    "npm": "npm",
    "git": "Git",
};

/**
 * Format a single tag with proper capitalization
 * @param {string} tag - The tag to format (e.g., "ai", "getting-started", "python")
 * @returns {string} - The properly formatted tag (e.g., "AI", "Getting Started", "Python")
 */
export function formatTag(tag) {
    // Handle null, undefined, or empty strings
    if (!tag) return "";
    
    const trimmedTag = tag.trim();
    const lowerTag = trimmedTag.toLowerCase();
    
    // Step 1: Check if we have a specific mapping for this tag
    // This handles all our special cases (AI, AWS, SaaS, etc.)
    if (TAG_DISPLAY_MAP[lowerTag]) {
        return TAG_DISPLAY_MAP[lowerTag];
    }
    
    // Step 2: Handle hyphenated tags (e.g., "getting-started" → "Getting Started")
    if (trimmedTag.includes("-")) {
        return trimmedTag
            .split("-")
            .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(" ");
    }
    
    // Step 3: Default fallback - Standard title case
    // First letter uppercase, rest lowercase (e.g., "python" → "Python")
    return trimmedTag.charAt(0).toUpperCase() + trimmedTag.slice(1).toLowerCase();
}

/**
 * Format an array of tags
 * @param {string[]} tags - Array of tags to format
 * @returns {string[]} - Array of formatted tags
 */
export function formatTags(tags) {
    if (!Array.isArray(tags)) return [];
    return tags.map(tag => formatTag(tag));
}
