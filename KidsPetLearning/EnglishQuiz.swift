import SwiftUI

struct EnglishLevelMapView: View {
    let onBack: () -> Void
    let go: (EnglishScreen) -> Void
    @EnvironmentObject private var progress: ProgressStore

    var body: some View {
        VStack(spacing: 8) {
            EnglishHeader(title: "英文闖關", subtitle: "20 關・每關 5 題", onBack: onBack)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 8) {
                    ForEach(1...20, id: \.self) { level in
                        let unlocked = level <= progress.englishUnlockedLevel
                        let complete = level < progress.englishUnlockedLevel
                        Button {
                            if unlocked { go(.quiz(level)) }
                        } label: {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(unlocked ? Color(red: 0.99, green: 0.72, blue: 0.28) : Color.gray.opacity(0.38))
                                        .frame(width: 54, height: 54)
                                    if unlocked {
                                        Text("\\(level)")
                                            .font(.system(size: 21, weight: .heavy, design: .rounded))
                                            .foregroundColor(.white)
                                    } else {
                                        Image(systemName: "lock.fill")
                                            .foregroundColor(.white)
                                    }
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("第 \\(level) 關")
                                        .font(.system(size: 19, weight: .heavy, design: .rounded))
                                    Text(complete ? "完成！ ⭐⭐⭐" : (unlocked ? "5 題聽音選圖" : "先完成上一關"))
                                        .font(.system(size: 13, weight: .medium, design: .rounded))
                                        .foregroundColor(.brown.opacity(0.68))
                                }
                                Spacer()
                                Image(systemName: complete ? "star.fill" : (unlocked ? "chevron.right" : "lock.fill"))
                                    .foregroundColor(complete ? .orange : .brown.opacity(0.6))
                            }
                            .foregroundColor(.brown)
                            .padding(.horizontal, 12)
                            .frame(maxWidth: .infinity, minHeight: 72)
                            .background(complete ? Color.green.opacity(0.12) : (unlocked ? Color.white : Color.gray.opacity(0.10)))
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                        }
                        .buttonStyle(.plain)
                        .disabled(!unlocked)
                    }
                }
                .padding(.bottom, 16)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
    }
}

struct EnglishQuizView: View {
    let level: Int
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore
    @EnvironmentObject private var speech: EnglishSpeechManager

    @State private var questionIndex = 0
    @State private var correctCount = 0
    @State private var current: EnglishWord?
    @State private var options: [EnglishWord] = []
    @State private var message = "請聽題目，選出正確圖片"
    @State private var answered = false

    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        VStack(spacing: 8) {
            EnglishHeader(title: "第 \\(level) 關", subtitle: "第 \\(min(questionIndex + 1, 5)) / 5 題", onBack: onBack)

            VStack(spacing: 6) {
                Text("聽英文，選出正確圖片")
                    .font(.system(size: 21, weight: .heavy, design: .rounded))
                    .foregroundColor(.brown)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Button {
                    if let current = current { speech.speak(current.english, language: "en-US") }
                } label: {
                    Label("朗讀題目・重播", systemImage: "speaker.wave.2.fill")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 43)
                        .background(Color(red: 0.25, green: 0.50, blue: 0.86))
                        .clipShape(RoundedRectangle(cornerRadius: 15))
                }
                .buttonStyle(.plain)
            }
            .padding(10)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20))

            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(options) { word in
                    Button {
                        choose(word)
                    } label: {
                        VStack(spacing: 4) {
                            Text(word.emoji)
                                .font(.system(size: 60))
                            Text(word.chinese)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundColor(.brown)
                                .lineLimit(1)
                                .minimumScaleFactor(0.72)
                        }
                        .frame(maxWidth: .infinity, minHeight: 132)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 21))
                        .overlay(RoundedRectangle(cornerRadius: 21).stroke(Color.black.opacity(0.05), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .disabled(answered)
                }
            }

            Text(message)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(message.contains("答對") || message.contains("完成") ? .green : .brown)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity, minHeight: 45)
                .background(Color.white.opacity(0.7))
                .clipShape(RoundedRectangle(cornerRadius: 14))

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
        .padding(.bottom, 7)
        .onAppear { prepareQuestion() }
    }

    private func prepareQuestion() {
        let levelWords = EnglishWordBank.levelWords(level)
        guard !levelWords.isEmpty else { return }
        current = levelWords[questionIndex % levelWords.count]
        var pool = EnglishWordBank.all.filter { $0.id != current?.id }.shuffled()
        var choices: [EnglishWord] = []
        if let current = current { choices.append(current) }
        choices.append(contentsOf: pool.prefix(3))
        options = choices.shuffled()
        answered = false
        message = "請聽題目，選出正確圖片"
        if let current = current {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                speech.speak(current.english, language: "en-US")
            }
        }
    }

    private func choose(_ word: EnglishWord) {
        guard let current = current, !answered else { return }
        answered = true
        let isCorrect = word.english == current.english && word.chinese == current.chinese
        progress.answerEnglish(correct: isCorrect)
        if isCorrect {
            correctCount += 1
            message = "答對了！ \\(current.english) = \\(current.chinese) ⭐"
        } else {
            message = "再加油！正確答案是 \\(current.chinese)"
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.95) {
            if questionIndex >= 4 {
                progress.finishEnglishLevel(level)
                message = "第 \\(level) 關完成！答對 \\(correctCount) / 5"
                speech.speak("Great job!", language: "en-US")
            } else {
                questionIndex += 1
                prepareQuestion()
            }
        }
    }
}
