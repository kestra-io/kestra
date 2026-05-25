"""
To run it locally, add OPENAI_API_KEY env variable and pip install gitpython openai
"""
import json
import re
import subprocess
import sys
import os
import git
from openai import OpenAI

client = OpenAI()


def translate_text(text, target_language):
    prompt = f"""Translate the text provided after "----------" into {target_language} for use in Kestra's orchestration UI. Follow these guidelines:
        - Output Only the Translation: Provide only the translated text, with no additional commentary or explanation.
        - Maintain Technical Accuracy: Use correct translations for technical terms (avoid literal translations that change the meaning).
        - Reserved English Terms (Do Not Translate): Keep the following terms in English (adjusting capitalization or plural forms as needed): kv store, namespace, flow, subflow, task, log, blueprint, id, trigger, label, key, value, input, output, port, worker, backfill, healthcheck, min, max. For example, in German, "log" must remain "Log" in phrases: translate "Log level" as "Log-Ebene" (not "Protokoll-Ebene"), and "Task logs" stays "Task Logs" (not "Aufgabenprotokolle"). Important: do not alter "flow" or "namespace" at all – keep them exactly as "flow" and "namespace."
        - UI Terminology Consistency: Ensure the translation sounds natural for a software interface. Avoid overly formal or word-for-word translations that feel unnatural in a UI. Use terminology that users expect in the target language. For example, in German translations:
          - State → Zustand (not "Staat")
          - Execution → Ausführung (not "Hinrichtung")
          - Theme → Modus (not "Thema")
          - Concurrency → Nebenläufigkeit (not "Konkurrenz")
          - Tenant (in multi-tenant context) → Mandant (not "Mieter")
          - Expand (UI control) → Ausklappen (not "Erweitern")
          - Tab (interface element) → Registerkarte (not "Reiter")
          - Creation → Erstellung (not "Schöpfung")
          Apply similar context-appropriate translations in other languages to avoid false friends or misleading terms.
        - State Labels in English: Keep status labels that are in all caps (e.g. WARNING, FAILED, SUCCESS, PAUSED, RUNNING) in English and in their original uppercase format.
        - Preserve Variables: Do not translate or change any placeholders enclosed in double curly braces (e.g. `{{label}}`, `{{key}}`). Leave them exactly as they are. For example, "System {{label}}" should remain "System {{label}}" in the translated text (do not translate "label" or remove the braces).

        If the loaded dictionary has no key-value pairs to translate, it means we're adding a new language, and we need to translate all the keys from English to {target_language}.

        Here is the text to translate:
        ----------
        {text}
        """

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {
                    "role": "system",
                    "content": f"You are a software engineer translating textual UI elements into {target_language} while keeping technical terms in English.",
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            temperature=0.1,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"Error during translation: {e}")
        return text # Return original if translation fails

def unflatten_dict(d, sep="|"):
    result = {}
    for k, v in d.items():
        keys = k.split(sep)
        current = result
        for key in keys[:-1]:
            current = current.setdefault(key, {})
        current[keys[-1]] = v
    return result


def flatten_dict(d, parent_key="", sep="|"):
    items = []
    for k, v in d.items():
        new_key = f"{parent_key}{sep}{k}" if parent_key else k
        if isinstance(v, dict):
            items.extend(flatten_dict(v, new_key, sep=sep).items())
        else:
            items.append((new_key, v))
    return dict(items)


def load_en_changes_from_last_commits(input_file, commit_range=50):
    repo = git.Repo(".")
    # Fetch all remote branches (including fork commits merged into remotes)
    repo.git.fetch("--all")

    # Get the two most recent commits that modified the input_file.
    commits = list(repo.iter_commits(paths=input_file, max_count=2))
    if len(commits) < 2:
        return {}

    # Compare the current working file with the version from the previous commit.
    previous_commit = commits[1]
    try:
        previous_version = previous_commit.tree / input_file
        return json.loads(previous_version.data_stream.read())
    except Exception:
        return {}


def load_en_dict(file_path):
    with open(file_path, "r") as f:
        return json.load(f)


def detect_changes(current_dict, previous_dict):
    added_keys = []
    changed_keys = []

    current_flat = flatten_dict(current_dict)
    previous_flat = flatten_dict(previous_dict)

    for key in current_flat:
        if key not in previous_flat:
            added_keys.append(key)
        elif current_flat[key] != previous_flat[key]:
            changed_keys.append(key)

    return set(added_keys + changed_keys)


def get_keys_to_translate(file_path="ui/src/translations/en.json"):
    current_en_dict = load_en_dict(file_path)
    previous_en_dict = load_en_changes_from_last_commits(file_path)

    keys_to_translate = detect_changes(current_en_dict, previous_en_dict)
    en_flat = flatten_dict(current_en_dict)
    return {k: en_flat[k] for k in keys_to_translate}


def remove_en_prefix(dictionary, prefix="en|"):
    return {k[len(prefix):]: v for k, v in dictionary.items() if k.startswith(prefix)}

def main(language_code, target_language, input_file="ui/src/translations/en.json", retranslate_modified_keys=False):
    with open(f"ui/src/translations/{language_code}.json", "r") as f:
        target_dict = json.load(f)[language_code]

    to_translate = get_keys_to_translate(input_file)
    to_translate = remove_en_prefix(to_translate)

    target_flat = flatten_dict(target_dict)
    translated_flat_dict = {}

    # Only re-translate if the key is not already in the target dict or is empty
    for k, v in to_translate.items():
        # If we already have a non-empty translation, skip unless forced to re-translate
        if k in target_flat and target_flat[k] and not retranslate_modified_keys:
            print(f"Skipping re-translation for '{k}' since a translation already exists.")
            continue
        new_translation = translate_text(v, target_language)
        translated_flat_dict[k] = new_translation
        print(f"Translating {k}:{v} to {target_language} -> '{new_translation}'.")

    target_flat.update(translated_flat_dict)

    target_flat = {k: v for k, v in target_flat.items() if k in remove_en_prefix(flatten_dict(load_en_dict(input_file)))}

    updated_target_dict = unflatten_dict(target_flat)

    # Sort keys to keep output stable
    with open(f"ui/src/translations/{language_code}.json", "w") as f:
        json.dump({language_code: updated_target_dict}, f, ensure_ascii=False, indent=2, sort_keys=True)


# ─── Design-system locale.ts helpers ─────────────────────────────────────────

def _needs_quoting(key: str) -> bool:
    """Return True if a JS/TS object key requires quoting."""
    return not re.match(r'^[a-zA-Z_$][a-zA-Z0-9_$]*$', key)


def _dict_to_ts_literal(obj: dict, indent: int = 1) -> str:
    """
    Recursively convert a Python dict to a TypeScript object-literal string
    with 4-space indentation, trailing commas, and correct key quoting.
    """
    pad = "    " * indent
    outer_pad = "    " * (indent - 1)
    lines = []
    for key, value in obj.items():
        formatted_key = f'"{key}"' if _needs_quoting(key) else key
        if isinstance(value, dict):
            lines.append(
                f"{pad}{formatted_key}: {_dict_to_ts_literal(value, indent + 1)},"
            )
        else:
            lines.append(
                f"{pad}{formatted_key}: {json.dumps(value, ensure_ascii=False)},"
            )
    return "{\n" + "\n".join(lines) + "\n" + outer_pad + "}"


def read_locale_ts(file_path: str) -> dict:
    """
    Read a *.locale.ts file and return its content as a Python dict.

    The files use the pattern ``export default { ... }`` with plain JS object
    literal syntax (no TypeScript type annotations), so we can safely evaluate
    them with Node.js's ``Function`` constructor without a full TS compile step.
    """
    # Build an inline Node.js CJS snippet that evaluates the file and prints JSON.
    node_code = (
        "var fs = require('fs');"
        f"var raw = fs.readFileSync({json.dumps(file_path)}, 'utf-8');"
        "var lit = raw.replace(/^export\\s+default\\s+/, '').replace(/;\\s*$/, '');"
        "process.stdout.write(JSON.stringify(new Function('return ' + lit)()));"
    )
    result = subprocess.run(
        ["node", "--eval", node_code],
        capture_output=True,
        text=True,
        check=True,
    )
    return json.loads(result.stdout)


def write_locale_ts(file_path: str, translations: dict) -> None:
    """Write a translations dict back to a *.locale.ts file."""
    content = "export default " + _dict_to_ts_literal(translations) + "\n"
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)


def find_locale_ts_files(components_dir: str) -> list[str]:
    """Recursively find all *.locale.ts files under *components_dir*."""
    results = []
    for root, _dirs, files in os.walk(components_dir):
        for name in files:
            if name.endswith(".locale.ts"):
                results.append(os.path.join(root, name))
    return sorted(results)


def translate_locale_ts_files(
    language_code: str,
    target_language: str,
    components_dir: str = "ui/packages/design-system/src/components",
    retranslate_modified_keys: bool = False,
) -> None:
    """
    For every *.locale.ts file in *components_dir*, translate any English keys
    that are missing (or have been modified) for *language_code*.

    Strategy
    --------
    Rather than tracking git history for each locale file, we rely on the
    simpler invariant that is always correct: if the target language is missing
    a key that exists in English, that key needs to be translated.  This means
    the function is idempotent and safe to re-run.
    """
    locale_files = find_locale_ts_files(components_dir)

    for locale_file in locale_files:
        print(f"\n--- Processing {locale_file} for {language_code} ---")

        try:
            translations = read_locale_ts(locale_file)
        except Exception as exc:
            print(f"  ERROR reading {locale_file}: {exc}")
            continue

        if "en" not in translations:
            print(f"  Skipping — no 'en' section found.")
            continue

        english_flat = flatten_dict(translations["en"])

        # Build or load the existing target-language section
        if language_code not in translations:
            print(f"  Language section '{language_code}' is missing — will create it.")
            translations[language_code] = {}

        target_flat = flatten_dict(translations[language_code])
        updated = False

        for key, en_value in english_flat.items():
            already_translated = key in target_flat and target_flat[key]
            if already_translated and not retranslate_modified_keys:
                continue

            translated_value = translate_text(en_value, target_language)
            target_flat[key] = translated_value
            print(f"  Translated [{language_code}] '{key}': '{en_value}' → '{translated_value}'")
            updated = True

        if not updated:
            print(f"  No new keys to translate for '{language_code}'.")
            continue

        # Remove any keys in the target language that no longer exist in English
        target_flat = {k: v for k, v in target_flat.items() if k in english_flat}

        translations[language_code] = unflatten_dict(target_flat)

        # Preserve the canonical language order: en first, then alphabetical
        ordered = {"en": translations["en"]}
        for lang in sorted(k for k in translations if k != "en"):
            ordered[lang] = translations[lang]

        write_locale_ts(locale_file, ordered)
        print(f"  Written: {locale_file}")


if __name__ == "__main__":
    # Default to 'false' if no argument is provided
    bool_from_ci = False
    if len(sys.argv) > 1 and sys.argv[1].lower() == "true":
        bool_from_ci = True

    # ── Translate main JSON files ─────────────────────────────────────────────
    main(language_code="de", target_language="German", retranslate_modified_keys=bool_from_ci)
    main(language_code="es", target_language="Spanish", retranslate_modified_keys=bool_from_ci)
    main(language_code="fr", target_language="French", retranslate_modified_keys=bool_from_ci)
    main(language_code="hi", target_language="Hindi", retranslate_modified_keys=bool_from_ci)
    main(language_code="it", target_language="Italian", retranslate_modified_keys=bool_from_ci)
    main(language_code="ja", target_language="Japanese", retranslate_modified_keys=bool_from_ci)
    main(language_code="ko", target_language="Korean", retranslate_modified_keys=bool_from_ci)
    main(language_code="pl", target_language="Polish", retranslate_modified_keys=bool_from_ci)
    main(language_code="pt", target_language="Portuguese", retranslate_modified_keys=bool_from_ci)
    main(language_code="pt_BR", target_language="Portuguese (Brazil)", retranslate_modified_keys=bool_from_ci)
    main(language_code="ru", target_language="Russian", retranslate_modified_keys=bool_from_ci)
    main(language_code="zh_CN", target_language="Simplified Chinese (Mandarin)", retranslate_modified_keys=bool_from_ci)

    # ── Translate design-system *.locale.ts files ─────────────────────────────
    translate_locale_ts_files(language_code="de", target_language="German", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="es", target_language="Spanish", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="fr", target_language="French", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="hi", target_language="Hindi", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="it", target_language="Italian", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="ja", target_language="Japanese", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="ko", target_language="Korean", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="pl", target_language="Polish", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="pt", target_language="Portuguese", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="pt_BR", target_language="Portuguese (Brazil)", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="ru", target_language="Russian", retranslate_modified_keys=bool_from_ci)
    translate_locale_ts_files(language_code="zh_CN", target_language="Simplified Chinese (Mandarin)", retranslate_modified_keys=bool_from_ci)
