import SwiftUI
import UIKit
import AudioToolbox

enum GameType: String, CaseIterable, Identifiable {
    case find, same, colorShape, maze, match, count, math, memory

    var id: String { rawValue }

    var title: String {
        switch self {
        case .find: return "找一找"
        case .same: return "找一樣"
        case .colorShape: return "顏色圖形"
        case .maze: return "迷宮"
        case .match: return "連連看"
        case .count: return "數一數"
        case .math: return "數學小高手"
        case .memory: return "記憶挑戰"
        }
    }

    var subtitle: String {
        switch self {
        case .find: return "專注搜尋"
        case .same: return "觀察細節"
        case .colorShape: return "辨識形狀"
        case .maze: return "手眼協調"
        case .match: return "拖曳配對"
        case .count: return "數量概念"
        case .math: return "基本加減法"
        case .memory: return "短期記憶"
        }
    }

    var symbol: String {
        switch self {
        case .find: return "magnifyingglass"
        case .same: return "circle.grid.cross"
        case .colorShape: return "square.on.circle"
        case .maze: return "point.topleft.down.to.point.bottomright.curvepath"
        case .match: return "link"
        case .count: return "number"
        case .math: return "plus.forwardslash.minus"
        case .memory: return "brain.head.profile"
        }
    }

    var tint: Color {
        switch self {
        case .find: return Color(red: 0.80, green: 0.93, blue: 0.67)
        case .same: return Color(red: 1.00, green: 0.84, blue: 0.71)
        case .colorShape: return Color(red: 0.88, green: 0.78, blue: 1.00)
        case .maze: return Color(red: 0.76, green: 0.85, blue: 1.00)
        case .match: return Color(red: 1.00, green: 0.77, blue: 0.85)
        case .count: return Color(red: 0.73, green: 0.93, blue: 0.90)
        case .math: return Color(red: 0.75, green: 0.86, blue: 1.00)
        case .memory: return Color(red: 1.00, green: 0.84, blue: 0.70)
        }
    }
}

final class ProgressStore: ObservableObject {
    @Published var stars: Int { didSet { defaults.set(stars, forKey: "stars") } }
    @Published var gamesPlayed: Int { didSet { defaults.set(gamesPlayed, forKey: "gamesPlayed") } }
    @Published var difficulty: Int { didSet { defaults.set(difficulty, forKey: "difficulty") } }

    // English learning progress is integrated into the same app and shares the star total.
    @Published var englishUnlockedLevel: Int { didSet { defaults.set(englishUnlockedLevel, forKey: "englishUnlockedLevel") } }
    @Published var englishTodayWords: Int { didSet { defaults.set(englishTodayWords, forKey: "englishTodayWords") } }
    @Published var englishCorrect: Int { didSet { defaults.set(englishCorrect, forKey: "englishCorrect") } }
    @Published var englishAttempts: Int { didSet { defaults.set(englishAttempts, forKey: "englishAttempts") } }
    @Published var englishCompletedThemes: Set<String> {
        didSet { defaults.set(Array(englishCompletedThemes), forKey: "englishCompletedThemes") }
    }

    private let defaults = UserDefaults.standard
    private let openedAt = Date()
    private let dailyKey: String

    init() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        dailyKey = formatter.string(from: Date())

        stars = defaults.integer(forKey: "stars")
        gamesPlayed = defaults.integer(forKey: "gamesPlayed")
        let savedDifficulty = defaults.integer(forKey: "difficulty")
        difficulty = savedDifficulty == 0 ? 1 : min(max(savedDifficulty, 1), 3)

        englishUnlockedLevel = max(1, defaults.integer(forKey: "englishUnlockedLevel"))
        englishCorrect = defaults.integer(forKey: "englishCorrect")
        englishAttempts = defaults.integer(forKey: "englishAttempts")
        englishCompletedThemes = Set(defaults.stringArray(forKey: "englishCompletedThemes") ?? [])

        if defaults.string(forKey: "englishDailyDate") == dailyKey {
            englishTodayWords = defaults.integer(forKey: "englishTodayWords")
        } else {
            englishTodayWords = 0
            defaults.set(dailyKey, forKey: "englishDailyDate")
            defaults.set(0, forKey: "englishTodayWords")
        }
    }

    var englishAccuracy: Int {
        guard englishAttempts > 0 else { return 0 }
        return Int(Double(englishCorrect) / Double(englishAttempts) * 100)
    }

    var sessionMinutes: Int {
        max(1, Int(Date().timeIntervalSince(openedAt) / 60))
    }

    func reward() {
        stars += 1
        gamesPlayed += 1
        Feedback.success()
    }

    func learnedEnglishWord(category: String) {
        stars += 1
        englishTodayWords += 1
        englishCompletedThemes.insert(category)
        defaults.set(dailyKey, forKey: "englishDailyDate")
        Feedback.success()
    }

    func answerEnglish(correct: Bool) {
        englishAttempts += 1
        if correct {
            englishCorrect += 1
            stars += 1
            Feedback.success()
        } else {
            Feedback.error()
        }
    }

    func finishEnglishLevel(_ level: Int) {
        if level >= englishUnlockedLevel && englishUnlockedLevel < 20 {
            englishUnlockedLevel = min(20, level + 1)
        }
    }
}

enum Feedback {
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        AudioServicesPlaySystemSound(1104)
    }

    static func error() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
        AudioServicesPlaySystemSound(1053)
    }

    static func tap() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
