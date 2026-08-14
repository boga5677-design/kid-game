import Foundation
import AVFoundation
import Speech

final class EnglishSpeechManager: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var afterSpeech: (() -> Void)?
    private var tapInstalled = false
    private var timedOut = false

    @Published var heard = ""
    @Published var isListening = false
    @Published var isPreparingMic = false

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func requestPermissions() {
        SFSpeechRecognizer.requestAuthorization { _ in }
        AVAudioSession.sharedInstance().requestRecordPermission { _ in }
    }

    func speak(_ text: String, language: String = "en-US") {
        stopListening()
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: language)
        utterance.rate = language.hasPrefix("zh") ? 0.46 : 0.42
        utterance.pitchMultiplier = 1.05
        synthesizer.speak(utterance)
    }

    func speakThenListen(_ text: String, completion: @escaping (String) -> Void) {
        stopListening()
        synthesizer.stopSpeaking(at: .immediate)
        isPreparingMic = true
        afterSpeech = { [weak self] in
            guard let self = self else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.isPreparingMic = false
                self.startListening(completion: completion)
            }
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.42
        utterance.pitchMultiplier = 1.05
        synthesizer.speak(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let callback = self.afterSpeech
            self.afterSpeech = nil
            callback?()
        }
    }

    func startListening(completion: @escaping (String) -> Void) {
        heard = ""
        timedOut = false
        task?.cancel()
        task = nil

        guard SFSpeechRecognizer.authorizationStatus() == .authorized else {
            completion("")
            return
        }

        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            request = req

            let node = audioEngine.inputNode
            let format = node.outputFormat(forBus: 0)
            if tapInstalled {
                node.removeTap(onBus: 0)
                tapInstalled = false
            }
            node.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                req.append(buffer)
            }
            tapInstalled = true

            audioEngine.prepare()
            try audioEngine.start()
            isListening = true

            task = recognizer?.recognitionTask(with: req) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    if let text = result?.bestTranscription.formattedString {
                        self.heard = text
                    }
                    if result?.isFinal == true || error != nil {
                        guard !self.timedOut else { return }
                        let final = self.heard
                        self.stopListening()
                        completion(final)
                    }
                }
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 4.5) { [weak self] in
                guard let self = self, self.isListening else { return }
                self.timedOut = true
                let final = self.heard
                self.stopListening()
                completion(final)
            }
        } catch {
            stopListening()
            completion("")
        }
    }

    func stopListening() {
        if audioEngine.isRunning { audioEngine.stop() }
        if tapInstalled {
            audioEngine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        isListening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    deinit {
        stopListening()
    }
}
