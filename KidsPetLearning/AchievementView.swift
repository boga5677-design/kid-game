import SwiftUI

struct AchievementView: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var progress: ProgressStore

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ZStack {
            Color(red: 1.0, green: 0.97, blue: 0.91).ignoresSafeArea()

            VStack(spacing: 12) {
                HStack {
                    Button("關閉") { presentationMode.wrappedValue.dismiss() }
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                    Spacer()
                    Text("🏅 成就徽章")
                        .font(.system(size: 26, weight: .heavy, design: .rounded))
                        .foregroundColor(.brown)
                    Spacer()
                    Text("⭐ \(progress.stars)")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        badge("🌟", "初次挑戰", progress.gamesPlayed >= 1)
                        badge("🎯", "專注新秀", progress.gamesPlayed >= 5)
                        badge("🔥", "連勝新秀", progress.stars >= 10)
                        badge("🧠", "記憶小高手", progress.stars >= 20)
                        badge("🔢", "數學達人", progress.stars >= 30)
                        badge("🏆", "毛孩學霸", progress.stars >= 50)
                    }
                    .padding(14)
                }
            }
        }
    }

    private func badge(_ icon: String, _ title: String, _ unlocked: Bool) -> some View {
        VStack(spacing: 8) {
            Text(unlocked ? icon : "🔒")
                .font(.system(size: 45))
            Text(title)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(unlocked ? "已獲得" : "繼續加油")
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundColor(.brown.opacity(0.65))
        }
        .frame(maxWidth: .infinity, minHeight: 145)
        .background(unlocked ? Color(red: 1.0, green: 0.93, blue: 0.70) : Color.gray.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: 23))
    }
}
