import SwiftUI

struct EnglishDailyTaskView: View {
    let onBack: () -> Void
    let goLearn: () -> Void
    @EnvironmentObject private var progress: ProgressStore

    private var complete: Bool { progress.englishTodayWords >= 5 }

    var body: some View {
        VStack(spacing: 12) {
            EnglishHeader(title: "每日任務", subtitle: "每天學 5 個英文單字", onBack: onBack)

            Spacer(minLength: 10)
            Text(complete ? "🎁" : "⭐")
                .font(.system(size: 100))
            Text("\\(min(progress.englishTodayWords, 5)) / 5")
                .font(.system(size: 42, weight: .heavy, design: .rounded))
                .foregroundColor(.brown)
            ProgressView(value: Double(min(progress.englishTodayWords, 5)), total: 5)
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .padding(.horizontal, 25)
            Text(complete ? "今天的英文任務完成了！" : "再學 \\(5 - min(progress.englishTodayWords, 5)) 個就完成")
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(.brown)
                .multilineTextAlignment(.center)

            Button(action: goLearn) {
                Text(complete ? "再學一些單字" : "去學單字")
                    .font(.system(size: 19, weight: .heavy, design: .rounded))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(Color(red: 0.30, green: 0.62, blue: 0.43))
                    .clipShape(RoundedRectangle(cornerRadius: 18))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
    }
}

struct EnglishPetGrowthView: View {
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore

    private var stage: Int {
        if progress.englishUnlockedLevel >= 20 { return 4 }
        if progress.englishUnlockedLevel >= 15 { return 3 }
        if progress.englishUnlockedLevel >= 10 { return 2 }
        if progress.englishUnlockedLevel >= 5 { return 1 }
        return 0
    }

    private let titles = ["新朋友", "開心夥伴", "冒險小隊", "學習高手", "闖關王者"]
    private let badges = ["✨", "🎀", "🎒", "👑", "🏆"]

    var body: some View {
        VStack(spacing: 10) {
            EnglishHeader(title: "寵物成長", subtitle: "三毛孩一起成長", onBack: onBack)

            ZStack(alignment: .bottom) {
                Image("MainVisual")
                    .resizable()
                    .scaledToFill()
                    .frame(height: 280)
                    .clipped()
                LinearGradient(colors: [.clear, .black.opacity(0.38)], startPoint: .center, endPoint: .bottom)
                VStack(spacing: 3) {
                    Text(badges[stage])
                        .font(.system(size: 42))
                    Text(titles[stage])
                        .font(.system(size: 25, weight: .heavy, design: .rounded))
                    Text("偶貴・黑糖・熊熊")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                }
                .foregroundColor(.white)
                .padding(.bottom, 13)
            }
            .frame(height: 280)
            .clipShape(RoundedRectangle(cornerRadius: 24))

            HStack(spacing: 8) {
                EnglishGrowthStat(title: "星星", value: "⭐ \\(progress.stars)")
                EnglishGrowthStat(title: "英文關卡", value: "\\(progress.englishUnlockedLevel)/20")
            }

            VStack(alignment: .leading, spacing: 5) {
                Text("下一階段")
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                Text(nextMessage)
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundColor(.brown.opacity(0.75))
            }
            .foregroundColor(.brown)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 19))

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
        .padding(.bottom, 8)
    }

    private var nextMessage: String {
        if progress.englishUnlockedLevel >= 20 { return "全部成長階段都解鎖了！" }
        let target = [5, 10, 15, 20].first(where: { $0 > progress.englishUnlockedLevel }) ?? 20
        return "完成第 \\(target) 關，解鎖新的成長徽章。"
    }
}

struct EnglishGrowthStat: View {
    let title: String
    let value: String
    var body: some View {
        VStack(spacing: 2) {
            Text(title).font(.system(size: 13, weight: .bold, design: .rounded))
            Text(value).font(.system(size: 20, weight: .heavy, design: .rounded))
        }
        .foregroundColor(.brown)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(Color(red: 0.88, green: 0.95, blue: 1.0))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct EnglishParentView: View {
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore

    var body: some View {
        VStack(spacing: 8) {
            EnglishHeader(title: "家長模式", subtitle: "學習紀錄只存在本機", onBack: onBack)
            ScrollView(showsIndicators: false) {
                VStack(spacing: 9) {
                    parentStat("clock.fill", "本次使用時間", "\\(progress.sessionMinutes) 分鐘")
                    parentStat("book.fill", "今日英文單字", "\\(progress.englishTodayWords) 個")
                    parentStat("checkmark.circle.fill", "英文答對率", "\\(progress.englishAccuracy)%")
                    parentStat("map.fill", "英文闖關進度", "\\(max(progress.englishUnlockedLevel - 1, 0)) / 20")
                    parentStat("star.fill", "總星星", "\\(progress.stars)")
                    parentStat("rectangle.stack.fill", "完成主題", "\\(progress.englishCompletedThemes.count) 個")

                    VStack(alignment: .leading, spacing: 5) {
                        Text("已學主題")
                            .font(.system(size: 18, weight: .heavy, design: .rounded))
                        Text(progress.englishCompletedThemes.isEmpty ? "尚未完成主題" : progress.englishCompletedThemes.sorted().joined(separator: "、"))
                            .font(.system(size: 14, weight: .medium, design: .rounded))
                            .foregroundColor(.brown.opacity(0.72))
                    }
                    .foregroundColor(.brown)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                }
                .padding(.bottom, 14)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
    }

    private func parentStat(_ icon: String, _ title: String, _ value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 23, weight: .semibold))
                .frame(width: 46, height: 46)
                .background(Color(red: 0.85, green: 0.93, blue: 1.0))
                .clipShape(Circle())
            Text(title)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Spacer()
            Text(value)
                .font(.system(size: 18, weight: .heavy, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .foregroundColor(.brown)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, minHeight: 65)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct EnglishAchievementsView: View {
    let onBack: () -> Void
    @EnvironmentObject private var progress: ProgressStore
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        VStack(spacing: 8) {
            EnglishHeader(title: "英文成就", subtitle: "繼續收集徽章吧", onBack: onBack)
            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: columns, spacing: 10) {
                    badge("🐾", "初次學習", progress.englishTodayWords >= 1)
                    badge("📖", "單字達人", progress.englishCorrect >= 10)
                    badge("🎧", "聽力高手", progress.englishCorrect >= 25)
                    badge("🗺️", "完成 5 關", progress.englishUnlockedLevel > 5)
                    badge("🥇", "完成 10 關", progress.englishUnlockedLevel > 10)
                    badge("👑", "完成 20 關", progress.englishUnlockedLevel >= 20)
                }
                .padding(.bottom, 14)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 7)
    }

    private func badge(_ icon: String, _ title: String, _ unlocked: Bool) -> some View {
        VStack(spacing: 6) {
            Text(unlocked ? icon : "🔒")
                .font(.system(size: 42))
            Text(title)
                .font(.system(size: 16, weight: .heavy, design: .rounded))
                .foregroundColor(.brown)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(unlocked ? "已獲得" : "繼續加油")
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(.brown.opacity(0.62))
        }
        .frame(maxWidth: .infinity, minHeight: 128)
        .background(unlocked ? Color(red: 1.0, green: 0.93, blue: 0.70) : Color.gray.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
