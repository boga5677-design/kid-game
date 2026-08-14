import SwiftUI

struct RootView: View {
    @EnvironmentObject private var progress: ProgressStore
    @State private var game: GameType?
    @State private var showAchievements = false
    @State private var showEnglish = false

    var body: some View {
        ZStack {
            Color(red: 1.00, green: 0.97, blue: 0.91)
                .ignoresSafeArea()

            if showEnglish {
                EnglishModuleView {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        showEnglish = false
                    }
                }
                .transition(.opacity)
            } else if let game = game {
                GameScreen(game: game) {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        self.game = nil
                    }
                }
                .transition(.opacity)
            } else {
                HomeView(
                    onGame: { selected in
                        Feedback.tap()
                        withAnimation(.easeInOut(duration: 0.18)) {
                            game = selected
                        }
                    },
                    onAchievements: {
                        showAchievements = true
                    },
                    onEnglish: {
                        Feedback.tap()
                        withAnimation(.easeInOut(duration: 0.18)) {
                            showEnglish = true
                        }
                    }
                )
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showAchievements) {
            AchievementView()
                .environmentObject(progress)
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var progress: ProgressStore
    let onGame: (GameType) -> Void
    let onAchievements: () -> Void
    let onEnglish: () -> Void

    private let columns = [
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10)
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 10) {
                topBar
                challengeCard
                mascotCard
                englishEntry

                HStack {
                    Text("選一個遊戲開始")
                        .font(.system(size: 21, weight: .bold, design: .rounded))
                        .foregroundColor(.brown)
                    Spacer()
                }

                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(GameType.allCases) { game in
                        GameTile(game: game) {
                            onGame(game)
                        }
                    }
                }

                Button(action: onAchievements) {
                    HStack {
                        Image(systemName: "medal.fill")
                        Text("我的成就")
                            .fontWeight(.bold)
                        Spacer()
                        Text("⭐ \(progress.stars)")
                            .fontWeight(.bold)
                    }
                    .font(.system(size: 18, design: .rounded))
                    .foregroundColor(.brown)
                    .padding(.horizontal, 16)
                    .frame(height: 54)
                    .background(Color(red: 1.00, green: 0.92, blue: 0.66))
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)
            .padding(.bottom, 18)
        }
    }

    private var englishEntry: some View {
        Button(action: onEnglish) {
            HStack(spacing: 13) {
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.82))
                        .frame(width: 62, height: 62)
                    Text("ABC")
                        .font(.system(size: 20, weight: .heavy, design: .rounded))
                        .foregroundColor(Color(red: 0.26, green: 0.48, blue: 0.82))
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("英文小教室")
                        .font(.system(size: 22, weight: .heavy, design: .rounded))
                        .foregroundColor(.brown)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                    Text("單字・闖關・發音練習")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(.brown.opacity(0.72))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                Spacer(minLength: 2)
                Text("🇺🇸 🇬🇧")
                    .font(.system(size: 22))
                Image(systemName: "chevron.right")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.brown.opacity(0.7))
            }
            .padding(.horizontal, 13)
            .frame(maxWidth: .infinity, minHeight: 86)
            .background(Color(red: 0.80, green: 0.91, blue: 1.00))
            .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 3, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            Text("👦")
                .font(.system(size: 29))
                .frame(width: 52, height: 52)
                .background(Color(red: 0.84, green: 0.94, blue: 1.00))
                .clipShape(Circle())

            Text("小朋友")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .lineLimit(1)
                .minimumScaleFactor(0.75)

            Spacer()

            Text("⭐ \(progress.stars)")
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .lineLimit(1)

            Text("🪙 \(progress.stars * 10)")
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .lineLimit(1)
        }
        .foregroundColor(.brown)
        .padding(.horizontal, 12)
        .frame(height: 66)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 6, y: 3)
    }

    private var challengeCard: some View {
        VStack(spacing: 8) {
            Text("一起來挑戰吧！")
                .font(.system(size: 29, weight: .heavy, design: .rounded))
                .foregroundColor(Color(red: 0.92, green: 0.31, blue: 0.42))
                .lineLimit(1)
                .minimumScaleFactor(0.72)

            HStack(spacing: 10) {
                StatusCard(
                    icon: "calendar",
                    title: "每日任務",
                    value: "\(min(progress.gamesPlayed, 5))/5",
                    tint: Color(red: 0.88, green: 0.96, blue: 0.78)
                )
                StatusCard(
                    icon: "gift.fill",
                    title: "星星寶箱",
                    value: "\(progress.stars % 30)/30",
                    tint: Color(red: 1.00, green: 0.91, blue: 0.66)
                )
            }
        }
        .padding(12)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
    }

    private var mascotCard: some View {
        ZStack(alignment: .bottom) {
            Image("MainVisual")
                .resizable()
                .scaledToFill()
                .frame(height: 210)
                .clipped()

            LinearGradient(
                colors: [.clear, .black.opacity(0.26)],
                startPoint: .center,
                endPoint: .bottom
            )

            HStack(spacing: 16) {
                TeacherLabel(name: "偶貴老師", color: .green)
                TeacherLabel(name: "黑糖老師", color: .orange)
                TeacherLabel(name: "熊熊老師", color: .blue)
            }
            .padding(.bottom, 10)
        }
        .frame(height: 210)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 5, y: 2)
    }
}

struct TeacherLabel: View {
    let name: String
    let color: Color

    var body: some View {
        Text(name)
            .font(.system(size: 13, weight: .bold, design: .rounded))
            .foregroundColor(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .padding(.horizontal, 10)
            .frame(height: 28)
            .background(color.opacity(0.94))
            .clipShape(Capsule())
    }
}

struct StatusCard: View {
    let icon: String
    let title: String
    let value: String
    let tint: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .bold))
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(value)
                    .font(.system(size: 21, weight: .heavy, design: .rounded))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .foregroundColor(.brown)
        .padding(.horizontal, 11)
        .frame(maxWidth: .infinity, minHeight: 66)
        .background(tint)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct GameTile: View {
    let game: GameType
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: game.symbol)
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundColor(.brown)
                    .frame(width: 54, height: 54)
                    .background(Color.white.opacity(0.75))
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 3) {
                    Text(game.title)
                        .font(.system(size: 19, weight: .bold, design: .rounded))
                        .foregroundColor(.brown)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)

                    Text(game.subtitle)
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(.brown.opacity(0.72))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 86)
            .background(game.tint)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 3, y: 2)
        }
        .buttonStyle(.plain)
    }
}
