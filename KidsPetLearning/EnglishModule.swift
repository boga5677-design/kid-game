import SwiftUI

enum EnglishScreen: Equatable {
    case home
    case categories
    case learning(String)
    case levels
    case quiz(Int)
    case pronunciation
    case daily
    case petGrowth
    case parent
    case achievements
}

struct EnglishModuleView: View {
    let onExit: () -> Void
    @StateObject private var speech = EnglishSpeechManager()
    @State private var screen: EnglishScreen = .home

    var body: some View {
        ZStack {
            Color(red: 1.00, green: 0.97, blue: 0.91).ignoresSafeArea()
            content
        }
        .environmentObject(speech)
        .onDisappear { speech.stopListening() }
    }

    @ViewBuilder
    private var content: some View {
        switch screen {
        case .home:
            EnglishHomeView(
                onExit: onExit,
                go: { screen = $0 }
            )
        case .categories:
            EnglishCategoryView(onBack: { screen = .home }, go: { screen = $0 })
        case .learning(let category):
            EnglishLearningView(category: category, onBack: { screen = .categories })
        case .levels:
            EnglishLevelMapView(onBack: { screen = .home }, go: { screen = $0 })
        case .quiz(let level):
            EnglishQuizView(level: level, onBack: { screen = .levels })
        case .pronunciation:
            EnglishPronunciationView(onBack: { screen = .home })
        case .daily:
            EnglishDailyTaskView(onBack: { screen = .home }, goLearn: { screen = .categories })
        case .petGrowth:
            EnglishPetGrowthView(onBack: { screen = .home })
        case .parent:
            EnglishParentView(onBack: { screen = .home })
        case .achievements:
            EnglishAchievementsView(onBack: { screen = .home })
        }
    }
}

struct EnglishHeader: View {
    let title: String
    let subtitle: String?
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 50, height: 43)
                    .background(Color(red: 0.30, green: 0.55, blue: 0.90))
                    .clipShape(RoundedRectangle(cornerRadius: 15))
            }
            .buttonStyle(.plain)

            VStack(spacing: 1) {
                Text(title)
                    .font(.system(size: 24, weight: .heavy, design: .rounded))
                    .foregroundColor(.brown)
                    .lineLimit(1)
                    .minimumScaleFactor(0.68)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.system(size: 11, weight: .medium, design: .rounded))
                        .foregroundColor(.brown.opacity(0.62))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                }
            }
            .frame(maxWidth: .infinity)

            Text("⭐ \\(progress.stars)")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .frame(width: 76, height: 42)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .frame(height: 50)
    }
}

struct EnglishHomeView: View {
    @EnvironmentObject private var progress: ProgressStore
    let onExit: () -> Void
    let go: (EnglishScreen) -> Void

    private let columns = [GridItem(.flexible(), spacing: 9), GridItem(.flexible(), spacing: 9)]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 10) {
                EnglishHeader(title: "英文小教室", subtitle: "黑糖・偶貴・熊熊陪你學", onBack: onExit)

                ZStack(alignment: .bottomLeading) {
                    Image("MainVisual")
                        .resizable()
                        .scaledToFill()
                        .frame(height: 170)
                        .clipped()
                    LinearGradient(colors: [.clear, .black.opacity(0.32)], startPoint: .center, endPoint: .bottom)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ABC Time!")
                            .font(.system(size: 28, weight: .heavy, design: .rounded))
                        Text("224 個幼兒英文單字")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .padding(14)
                }
                .frame(height: 170)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))

                HStack(spacing: 9) {
                    EnglishStatus(title: "今日單字", value: "\\(progress.englishTodayWords)/5", icon: "book.fill", tint: Color(red: 0.86, green: 0.96, blue: 0.77))
                    EnglishStatus(title: "英文關卡", value: "\\(progress.englishUnlockedLevel)/20", icon: "map.fill", tint: Color(red: 1.0, green: 0.91, blue: 0.70))
                }

                Button { go(.levels) } label: {
                    HStack(spacing: 12) {
                        Text("🏆")
                            .font(.system(size: 37))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("開始英文闖關")
                                .font(.system(size: 21, weight: .heavy, design: .rounded))
                            Text("20 關・聽音選圖")
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                        }
                        Spacer()
                        Image(systemName: "play.circle.fill")
                            .font(.system(size: 31))
                    }
                    .foregroundColor(.brown)
                    .padding(.horizontal, 14)
                    .frame(maxWidth: .infinity, minHeight: 78)
                    .background(Color(red: 1.00, green: 0.76, blue: 0.45))
                    .clipShape(RoundedRectangle(cornerRadius: 22))
                }
                .buttonStyle(.plain)

                LazyVGrid(columns: columns, spacing: 9) {
                    EnglishMenuTile(icon: "book.closed.fill", title: "單字學習", subtitle: "分類學 224 字", tint: Color(red: 0.80, green: 0.93, blue: 0.70)) { go(.categories) }
                    EnglishMenuTile(icon: "waveform.and.mic", title: "AI 發音", subtitle: "跟讀＋口說評分", tint: Color(red: 1.0, green: 0.82, blue: 0.72)) { go(.pronunciation) }
                    EnglishMenuTile(icon: "calendar.badge.checkmark", title: "每日任務", subtitle: "每天學 5 個", tint: Color(red: 0.87, green: 0.82, blue: 1.0)) { go(.daily) }
                    EnglishMenuTile(icon: "pawprint.fill", title: "寵物成長", subtitle: "三毛孩一起升級", tint: Color(red: 0.76, green: 0.90, blue: 1.0)) { go(.petGrowth) }
                    EnglishMenuTile(icon: "person.2.fill", title: "家長模式", subtitle: "查看學習紀錄", tint: Color(red: 1.0, green: 0.82, blue: 0.88)) { go(.parent) }
                    EnglishMenuTile(icon: "medal.fill", title: "英文成就", subtitle: "收集學習徽章", tint: Color(red: 1.0, green: 0.91, blue: 0.66)) { go(.achievements) }
                }
            }
            .padding(.horizontal, 13)
            .padding(.top, 8)
            .padding(.bottom, 18)
        }
    }
}

struct EnglishStatus: View {
    let title: String
    let value: String
    let icon: String
    let tint: Color

    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .bold))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 13, weight: .bold, design: .rounded))
                Text(value).font(.system(size: 20, weight: .heavy, design: .rounded))
            }
            Spacer(minLength: 0)
        }
        .foregroundColor(.brown)
        .padding(.horizontal, 11)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(tint)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct EnglishMenuTile: View {
    let icon: String
    let title: String
    let subtitle: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                Image(systemName: icon)
                    .font(.system(size: 24, weight: .semibold))
                    .frame(width: 45, height: 45)
                    .background(Color.white.opacity(0.78))
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text(subtitle)
                        .font(.system(size: 11, weight: .medium, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                }
                Spacer(minLength: 0)
            }
            .foregroundColor(.brown)
            .padding(.horizontal, 9)
            .frame(maxWidth: .infinity, minHeight: 78)
            .background(tint)
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}
