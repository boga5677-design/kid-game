import SwiftUI

struct GameScreen: View {
    let game: GameType
    let onBack: () -> Void
    @StateObject private var speech = SpeechManager()
    @State private var question = ""

    var body: some View {
        VStack(spacing: 8) {
            header

            VStack(spacing: 7) {
                Text(question)
                    .font(.system(size: 23, weight: .bold, design: .rounded))
                    .foregroundColor(.brown)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .minimumScaleFactor(0.72)
                    .frame(maxWidth: .infinity, minHeight: 58)

                Button {
                    speech.replay(question)
                    Feedback.tap()
                } label: {
                    Label("朗讀題目・重播", systemImage: "speaker.wave.2.fill")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .frame(maxWidth: .infinity, minHeight: 38)
                        .foregroundColor(.white)
                        .background(Color(red: 0.23, green: 0.47, blue: 0.81))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
            }
            .padding(10)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 3, y: 2)

            Group {
                switch game {
                case .find:
                    FindGameView(question: $question, speech: speech)
                case .same:
                    SameGameView(question: $question, speech: speech)
                case .colorShape:
                    ColorShapeGameView(question: $question, speech: speech)
                case .maze:
                    MazeGameView(question: $question, speech: speech)
                case .match:
                    MatchGameView(question: $question, speech: speech)
                case .count:
                    CountGameView(question: $question, speech: speech)
                case .math:
                    MathGameView(question: $question, speech: speech)
                case .memory:
                    MemoryGameView(question: $question, speech: speech)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(Color(red: 0.96, green: 0.985, blue: 1.00).ignoresSafeArea())
        .onDisappear {
            speech.stop()
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 54, height: 44)
                    .background(Color(red: 0.31, green: 0.56, blue: 0.91))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)

            Text(game.title)
                .font(.system(size: 28, weight: .heavy, design: .rounded))
                .foregroundColor(Color(red: 0.92, green: 0.31, blue: 0.42))
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity)

            ScoreMiniView()
        }
        .frame(height: 48)
    }
}

struct ScoreMiniView: View {
    @EnvironmentObject private var progress: ProgressStore
    var body: some View {
        Text("⭐ \(progress.stars)")
            .font(.system(size: 17, weight: .bold, design: .rounded))
            .foregroundColor(.brown)
            .frame(width: 82, height: 42)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 15))
    }
}

struct FeedbackBanner: View {
    let text: String
    let good: Bool

    var body: some View {
        Group {
            if !text.isEmpty {
                Text(text)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundColor(good ? .green : .orange)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(good ? Color.green.opacity(0.12) : Color.orange.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 15))
            } else {
                Color.clear.frame(height: 44)
            }
        }
    }
}

struct BigAnswerButton: View {
    let title: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .lineLimit(2)
                .minimumScaleFactor(0.58)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(tint)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.black.opacity(0.05), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}
