import json
import git
from openai import OpenAI

client = OpenAI()


def translate_text(text, target_language):
    prompt = f"""Translate the text provided after "----------" to {target_language}.
                The text is intended to be displayed within a software application,
                so make sure to keep the translation consistent with the context of a software UI.
                For example, translating from English to German, you should translate:
                - "State" to "Zustand" rather than "Staat"
                - "Execution" to "Ausführung" rather than "Hinrichtung"
                - "Theme" to "Modus" rather than "Thema"
                - "Concurrency" to "Nebenläufigkeit" rather than "Konkurrenz"
                - "Tenant" to "Mandant" rather than "Mieter"
                - "Expand" to "Ausklappen" rather than "Erweitern"
                - "Tab" to "Registerkarte" rather than "Reiter"
                - "Creation" to "Erstellung" rather than "Schöpfung".

                Keep the following technical terms in the original format in English without translating them to {target_language}
                 (you can adjust the case or pluralization as needed):
                - "kv store"
                - "tenant"
                - "namespace"
                - "flow"
                - "subflow"
                - "task"
                - "log"
                - "blueprint"
                - "id"
                - "trigger"
                - "label"
                - "key"
                - "value"
                - "input"
                - "output"
                - "port"
                - "worker"
                - "backfill"
                - "healthcheck"
                - "min"
                - "max"

                Similarly, keep the states shown in capital letters like WARNING, FAILED, SUCCESS, PAUSED
                and RUNNING in the original format in English without translating them to {target_language}.

                It's essential that you keep the translation consistent with the context of a software UI
                and that you keep the above-mentioned technical terms in English. For example, never translate "log"
                to an equivalent word in {target_language} but keep it as "Log". This means:
                - "Log level" and "log_level" should be translated to "Log-Ebene" in German, rather than "Protokoll-Ebene".
                - "Task logs" should be translated to "Task Logs" in German, rather than "Aufgabenprotokolle".

                Never translate variables provided within curly braces like {{label}} or {{key}}. 
                They should remain fully unchanged in the translation. For example, the string "System {{label}}"
                should remain unchanged and be translated to "System {{label}}" in German, 
                rather than "System {{Etikett}}" or "System {{Label}}". 

                Here is the text to translate:
                ----------
                \n\n{text}
                """

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {
                    "role": "system",
                    "content": f"""You are a software engineer translating textual UI elements
                    within a software application from English into {target_language}
                    while keeping technical terms in English.""",
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            temperature=0.2,
        )
        translation = response.choices[0].message.content.strip()
        return translation
    except Exception as e:
        print(f"Error during translation: {e}")
        return text  # Return the original text if translation fails


def translate_dict(en_dict, target_language):
    translated_dict = {}
    for key, value in en_dict.items():
        if isinstance(value, dict):
            translated_value = translate_dict(value, target_language)
        else:
            translated_value = translate_text(value, target_language)
            print(f"Translating key '{key}' with value '{value}]' from English, to value '{translated_value}' in {target_language}.")
        translated_dict[key] = translated_value
    return translated_dict


def unflatten_dict(d, sep="|"):
    result = {}
    for k, v in d.items():
        keys = k.split(sep)
        d = result
        for key in keys[:-1]:
            d = d.setdefault(key, {})
        d[keys[-1]] = v
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


def load_en_changes_from_last_commits(input_file, commit_range=10):
    repo = git.Repo(".")
    commits = list(repo.iter_commits('HEAD', max_count=commit_range))
    
    # Iterate over commits and compare to find the last one that modified the file
    for commit in commits[1:]:
        previous_version = commit.tree / input_file
        if previous_version:
            return json.loads(previous_version.data_stream.read())
    return {}


def load_en_dict(file_path):
    with open(file_path, "r") as f:
        return json.load(f)


# Detect changes between the current and previous version
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
    to_translate = {k: en_flat[k] for k in keys_to_translate}
    return to_translate


def remove_en_prefix(dictionary, prefix="en|"):
    """
    Removes the 'en|' prefix from the english dictionary keys.
    """
    return {k[len(prefix):]: v for k, v in dictionary.items() if k.startswith(prefix)}


def main(
    language_code,
    target_language,
    input_file="ui/src/translations/en.json",
):
    with open(f"ui/src/translations/{language_code}.json", "r") as f:
        target_dict = json.load(f)[language_code]

    to_translate = get_keys_to_translate(input_file)
    to_translate = remove_en_prefix(to_translate)
    translated_flat_dict = translate_dict(to_translate, target_language)

    # Merge with the existing translations
    target_flat = flatten_dict(target_dict)
    target_flat.update(translated_flat_dict)
    updated_target_dict = unflatten_dict(target_flat)

    with open(f"ui/src/translations/{language_code}.json", "w") as f:
        json.dump({language_code: updated_target_dict}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main(language_code="de", target_language="German")
    main(language_code="es", target_language="Spanish")
    main(language_code="fr", target_language="French")
    main(language_code="hi", target_language="Hindi")
    main(language_code="it", target_language="Italian")
    main(language_code="ja", target_language="Japanese")
    main(language_code="ko", target_language="Korean")
    main(language_code="pl", target_language="Polish")
    main(language_code="pt", target_language="Portuguese")
    main(language_code="ru", target_language="Russian")
    main(language_code="zh_CN", target_language="Simplified Chinese (Mandarin)")
