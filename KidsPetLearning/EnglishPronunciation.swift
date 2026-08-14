import SwiftUI

struct EnglishPronunciationView: View {
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore
    @EnvironmentObject private var speech: EnglishSpeechManager

    @State private var words: [EnglishWord] = []
    @State private var index = 0
    @State private var score = 0
    @State private var result = "按下開始，先聽示範；示範結束 0.5 秒後才會開麥克風。"

    var body: some View {
        VStack(spacing: 10) {
            EnglishHeader(title: "AI 發音", subtitle: "示範音不會被麥克風收進去", onBack: onBack)

            if words.isEmpty {
                Spacer()
                Text("準備單字中…")
                Spacer()
            } else {
                let word = words[index]
                VStack(spacing: 8) {
                    Text(word.emoji)
                        .font(.system(size: 88))
                    Text(word.english)
                        .font(.system(size: 38, weight: .heavy, design: .rounded))
                        .foregroundColor(Color(red: 0.23, green: 0.44, blue: 0.78))
                        .lineLimit(1)
                        .minimumScaleFactor(0.55)
                    Text(word.chinese)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(.brown)
                }
                .padding(14)
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 24))

                HStack(spacing: 9) {
                    PronounceButton(flag: "🇺🇸", title: "美式") { speech.speak(word.english, language: "en-US") }
                    PronounceButton(flag: "🇬🇧", title: "英式") { speech.speak(word.english, language: "en-GB") }
                }

                Button {
                    startPractice(word)
                } label: {
                    HStack(spacing: 8) {
                        if speech.isPreparingMic {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("示範結束，0.5 秒後收音")
                        } else if speech.isListening {
                            Image(systemName: "mic.fill")
                            Text("正在聽你說…")
                        } else {
                            Image(systemName: "play.fill")
                            Text("開始跟讀")
                        }
                    }
                    .font(.system(size: 18, weight: .heavy, design: .rounded))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, minHeight: 55)
                    .background(speech.isListening ? Color.red : Color(red: 0.30, green: 0.62, blue: 0.43))
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                }
                .buttonStyle(.plain)
                .disabled(speech.isPreparingMic || speech.isListening)

                VStack(spacing: 4) {
                    Text("發音分數：\\(score)")
                        .font(.system(size: 25, weight: .heavy, design: .rounded))
                        .foregroundColor(score >= 80 ? .green : .orange)
                    if !speech.heard.isEmpty {
                        Text("辨識：\\(speech.heard)")
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundColor(.brown.opacity(0.75))
                            .lineLimit(2)
                    }
                    Text(result)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundColor(.brown)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .minimumScaleFactor(0.75)
                }
                .padding(10)
                .frame(maxWidth: .infinity, minHeight: 92)
                .background(Color(red: 1.0, green: 0.94, blue: 0.78))
                .clipShape(RoundedRectangle(cornerRadius: 18))

                HStack(spacing: 9) {
                    Button { previous() } label: {
                        Label("上一個", systemImage: "chevron.left")
                            .frame(maxWidth: .infinity, minHeight: 47)
                            .background(Color.gray.opacity(0.16))
                            .clipShape(RoundedRectangle(cornerRadius: 15))
                    }
                    Button { next() } label: {
                        Label("下一個", systemImage: "chevron.right")
                            .frame(maxWidth: .infinity, minHeight: 47)
                            .background(Color(red: 0.80, green: 0.90, blue: 1.0))
                            .clipShape(RoundedRectangle(cornerRadius: 15))
                    }
                }
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .buttonStyle(.plain)

                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
        .padding(.bottom, 8)
        .onAppear {
            speech.requestPermissions()
            words = Array(EnglishWordBank.everyday.shuffled().prefix(20))
        }
        .onDisappear { speech.stopListening() }
    }

    private func startPractice(_ word: EnglishWord) {
        score = 0
        result = "先聽示範…"
        speech.speakThenListen(word.english) { heard in
            let value = similarityScore(target: word.english, heard: heard)
            score = value
            if value >= 85 {
                progress.answerEnglish(correct: true)
                result = "很棒！發音非常接近。"
            } else if value >= 60 {
                Feedback.tap()
                result = "不錯，再清楚一點會更好。"
            } else {
                Feedback.error()
                result = heard.isEmpty ? "沒有聽清楚，請確認麥克風權限後再試一次。" : "再聽一次示範，慢慢跟著念。"
            }
        }
    }

    private func next() {
        guard !words.isEmpty else { return }
        speech.stopListening()
        index = (index + 1) % words.count
        score = 0
        result = "準備好了就開始跟讀。"
    }

    private func previous() {
        guard !words.isEmpty else { return }
        speech.stopListening()
        index = (index - 1 + words.count) % words.count
        score = 0
        result = "準備好了就開始跟讀。"
    }

    private func similarityScore(target: String, heard: String) -> Int {
        let a = normalize(target)
        let b = normalize(heard)
        guard !a.isEmpty, !b.isEmpty else { return 0 }
        if a == b { return 100 }
        let distance = levenshtein(Array(a), Array(b))
        let length = max(a.count, b.count)
        return max(0, Int((1.0 - Double(distance) / Double(length)) * 100.0))
    }

    private func normalize(_ text: String) -> String {
        text.lowercased()
            .replacingOccurrences(of: "-", with: " ")
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private func levenshtein(_ lhs: [Character], _ rhs: [Character]) -> Int {
        if lhs.isEmpty { return rhs.count }
        if rhs.isEmpty { return lhs.count }
        var previous = Array(0...rhs.count)
        for (i, lc) in lhs.enumerated() {
            var current = [i + 1]
            for (j, rc) in rhs.enumerated() {
                current.append(min(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + (lc == rc ? 0 : 1)
                ))
            }
            previous = current
        }
        return previous[rhs.count]
    }
}
