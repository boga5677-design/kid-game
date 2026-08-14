import SwiftUI

struct EnglishCategoryView: View {
    let onBack: () -> Void
    let go: (EnglishScreen) -> Void
    @EnvironmentObject private var speech: EnglishSpeechManager

    private let columns = [GridItem(.flexible(), spacing: 9), GridItem(.flexible(), spacing: 9)]

    var body: some View {
        VStack(spacing: 8) {
            EnglishHeader(title: "單字學習", subtitle: "選一個主題開始", onBack: onBack)

            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: columns, spacing: 9) {
                    ForEach(Array(EnglishWordBank.categories.enumerated()), id: \.offset) { _, item in
                        Button {
                            speech.speak(item.0, language: "zh-TW")
                            go(.learning(item.0))
                        } label: {
                            HStack(spacing: 8) {
                                Text(item.1)
                                    .font(.system(size: 35))
                                    .frame(width: 50, height: 50)
                                    .background(Color.white.opacity(0.75))
                                    .clipShape(RoundedRectangle(cornerRadius: 15))
                                Text(item.0)
                                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                                    .foregroundColor(.brown)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.7)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 9)
                            .frame(maxWidth: .infinity, minHeight: 72)
                            .background(categoryColor(item.0))
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.bottom, 16)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
    }

    private func categoryColor(_ category: String) -> Color {
        let palette: [Color] = [
            Color(red: 0.86, green: 0.95, blue: 0.78),
            Color(red: 1.0, green: 0.86, blue: 0.72),
            Color(red: 0.89, green: 0.82, blue: 1.0),
            Color(red: 0.78, green: 0.90, blue: 1.0),
            Color(red: 1.0, green: 0.82, blue: 0.87),
            Color(red: 0.77, green: 0.94, blue: 0.90)
        ]
        let idx = abs(category.hashValue) % palette.count
        return palette[idx]
    }
}

struct EnglishLearningView: View {
    let category: String
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore
    @EnvironmentObject private var speech: EnglishSpeechManager
    @State private var index = 0
    @State private var learnedIDs: Set<UUID> = []
    @State private var message = ""

    private var words: [EnglishWord] {
        EnglishWordBank.all.filter { $0.category == category }
    }

    var body: some View {
        VStack(spacing: 9) {
            EnglishHeader(title: category, subtitle: "\\(min(index + 1, max(words.count, 1))) / \\(words.count)", onBack: onBack)

            if words.isEmpty {
                Spacer()
                Text("這個主題目前沒有單字")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                Spacer()
            } else {
                let word = words[index]

                VStack(spacing: 8) {
                    Text(word.emoji)
                        .font(.system(size: 92))
                        .frame(maxWidth: .infinity, minHeight: 125)

                    Text(word.english)
                        .font(.system(size: 36, weight: .heavy, design: .rounded))
                        .foregroundColor(Color(red: 0.24, green: 0.43, blue: 0.76))
                        .lineLimit(1)
                        .minimumScaleFactor(0.54)

                    Text(word.chinese)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(.brown)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)

                    HStack(spacing: 10) {
                        PronounceButton(flag: "🇺🇸", title: "美式") { speech.speak(word.english, language: "en-US") }
                        PronounceButton(flag: "🇬🇧", title: "英式") { speech.speak(word.english, language: "en-GB") }
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 25))
                .shadow(color: .black.opacity(0.06), radius: 4, y: 2)

                if !message.isEmpty {
                    Text(message)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.green)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(Color.green.opacity(0.10))
                        .clipShape(RoundedRectangle(cornerRadius: 13))
                }

                HStack(spacing: 9) {
                    Button {
                        index = max(0, index - 1)
                        speakCurrent()
                    } label: {
                        Label("上一個", systemImage: "chevron.left")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .frame(maxWidth: .infinity, minHeight: 51)
                            .background(Color.gray.opacity(index == 0 ? 0.12 : 0.22))
                            .clipShape(RoundedRectangle(cornerRadius: 17))
                    }
                    .buttonStyle(.plain)
                    .disabled(index == 0)

                    Button {
                        let word = words[index]
                        if !learnedIDs.contains(word.id) {
                            learnedIDs.insert(word.id)
                            progress.learnedEnglishWord(category: category)
                        } else {
                            Feedback.success()
                        }
                        message = "答對鈴聲！這個單字會了 ⭐"
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
                            next()
                        }
                    } label: {
                        Text("✅ 會了")
                            .font(.system(size: 18, weight: .heavy, design: .rounded))
                            .frame(maxWidth: .infinity, minHeight: 51)
                            .foregroundColor(.white)
                            .background(Color.green)
                            .clipShape(RoundedRectangle(cornerRadius: 17))
                    }
                    .buttonStyle(.plain)

                    Button {
                        next()
                    } label: {
                        Label("下一個", systemImage: "chevron.right")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .frame(maxWidth: .infinity, minHeight: 51)
                            .background(Color(red: 0.80, green: 0.90, blue: 1.0))
                            .clipShape(RoundedRectangle(cornerRadius: 17))
                    }
                    .buttonStyle(.plain)
                }

                Spacer(minLength: 0)
            }
        }
        .foregroundColor(.brown)
        .padding(.horizontal, 12)
        .padding(.top, 7)
        .padding(.bottom, 8)
        .onAppear { speakCurrent() }
    }

    private func next() {
        guard !words.isEmpty else { return }
        index = (index + 1) % words.count
        message = ""
        speakCurrent()
    }

    private func speakCurrent() {
        guard words.indices.contains(index) else { return }
        speech.speak(words[index].english, language: "en-US")
    }
}

struct PronounceButton: View {
    let flag: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(flag)
                Image(systemName: "speaker.wave.2.fill")
                Text(title)
                    .fontWeight(.bold)
            }
            .font(.system(size: 16, design: .rounded))
            .foregroundColor(.brown)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(Color(red: 0.89, green: 0.94, blue: 1.0))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
