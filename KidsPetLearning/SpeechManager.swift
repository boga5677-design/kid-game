import Foundation
import AVFoundation

final class SpeechManager: ObservableObject {
    private let synth = AVSpeechSynthesizer()
    private var pending: DispatchWorkItem?

    func speak(_ text: String, after delay: TimeInterval = 0.5) {
        pending?.cancel()
        synth.stopSpeaking(at: .immediate)

        let task = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = AVSpeechSynthesisVoice(language: "zh-TW")
            utterance.rate = 0.46
            utterance.pitchMultiplier = 1.03
            self.synth.speak(utterance)
        }
        pending = task
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: task)
    }

    func replay(_ text: String) {
        speak(text, after: 0)
    }

    func stop() {
        pending?.cancel()
        synth.stopSpeaking(at: .immediate)
    }
}
