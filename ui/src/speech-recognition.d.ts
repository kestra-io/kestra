// The Web Speech API recognizer and the webkit-prefixed globals it is exposed under.
// TypeScript's DOM lib ships the result side (SpeechRecognitionEvent, SpeechRecognitionResult,
// SpeechRecognitionAlternative) but not the recognizer itself, which is still an unofficial
// Community Group draft, nor webkitSpeechRecognition / webkitAudioContext. Only the members the
// app uses are declared, and the constructors are reached through window rather than declared
// as globals, so a future lib.dom that adds them cannot collide with this file.

interface SpeechRecognition extends EventTarget {
    continuous: boolean
    interimResults: boolean
    onresult: ((this: SpeechRecognition, event: SpeechRecognitionEvent) => void) | null
    onend: ((this: SpeechRecognition, event: Event) => void) | null
    start(): void
    stop(): void
    abort(): void
}

interface SpeechRecognitionConstructor {
    new (): SpeechRecognition
}

interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
    webkitAudioContext?: typeof AudioContext
}
