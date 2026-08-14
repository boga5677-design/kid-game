import SwiftUI

// MARK: - 找一找

private struct FindItem: Identifiable {
    let id = UUID()
    let kind: Int
    var found = false
}

struct FindGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var items: [FindItem] = []
    @State private var target = 0
    @State private var feedback = ""
    @State private var good = false

    private let symbols = ["●", "■", "▲", "★"]
    private let names = ["圓形", "正方形", "三角形", "星星"]
    private let colors: [Color] = [.red, .blue, .green, .orange]

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("找到 \(foundCount) / \(targetCount)")
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundColor(.brown)
                Spacer()
            }

            GeometryReader { geo in
                let spacing: CGFloat = 8
                let w = (geo.size.width - spacing * 3) / 4
                let h = (geo.size.height - spacing * 4) / 5

                VStack(spacing: spacing) {
                    ForEach(0..<5, id: \.self) { row in
                        HStack(spacing: spacing) {
                            ForEach(0..<4, id: \.self) { col in
                                let idx = row * 4 + col
                                if idx < items.count {
                                    Button {
                                        tap(idx)
                                    } label: {
                                        Text(items[idx].found ? "" : symbols[items[idx].kind])
                                            .font(.system(size: min(w, h) * 0.62, weight: .bold))
                                            .foregroundColor(colors[items[idx].kind])
                                            .frame(width: w, height: h)
                                            .background(Color.white)
                                            .clipShape(RoundedRectangle(cornerRadius: 16))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }

            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private var targetCount: Int { items.filter { $0.kind == target }.count }
    private var foundCount: Int { items.filter { $0.kind == target && $0.found }.count }

    private func reset() {
        target = Int.random(in: 0..<4)
        var generated = (0..<20).map { _ in FindItem(kind: Int.random(in: 0..<4)) }
        if !generated.contains(where: { $0.kind == target }) {
            generated[0] = FindItem(kind: target)
        }
        items = generated
        question = "請找出所有\(names[target])。"
        speech.speak(question)
        feedback = ""
    }

    private func tap(_ idx: Int) {
        guard !items[idx].found else { return }
        if items[idx].kind == target {
            items[idx].found = true
            Feedback.tap()
            if foundCount == targetCount {
                progress.reward()
                feedback = "全部找到了！ ⭐ +1"
                good = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { reset() }
            }
        } else {
            Feedback.error()
            feedback = "再看看～"
            good = false
        }
    }
}

// MARK: - 找一樣

struct SameGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var answers: [String] = []
    @State private var feedback = ""
    @State private var good = false

    private let correct = "🌼"

    var body: some View {
        VStack(spacing: 10) {
            Text(correct)
                .font(.system(size: 72))
                .frame(maxWidth: .infinity, minHeight: 110)
                .background(Color(red: 1.0, green: 0.93, blue: 0.78))
                .clipShape(RoundedRectangle(cornerRadius: 22))

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(answers, id: \.self) { answer in
                    BigAnswerButton(title: answer, tint: .white) {
                        if answer == correct {
                            progress.reward()
                            good = true
                            feedback = "答對了！ ⭐ +1"
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) { reset() }
                        } else {
                            Feedback.error()
                            good = false
                            feedback = "不是這一朵，再看看～"
                        }
                    }
                    .frame(minHeight: 105)
                }
            }

            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private func reset() {
        answers = ["🌼", "🌻", "🌸", "🌺"].shuffled()
        question = "請找出和上面完全一樣的花朵。"
        speech.speak(question)
        feedback = ""
    }
}

// MARK: - 顏色圖形

private struct ColorShapeItem: Identifiable {
    let id = UUID()
    let circle: Bool
    let colorIndex: Int
    var found = false
}

struct ColorShapeGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var items: [ColorShapeItem] = []
    @State private var feedback = ""
    @State private var good = false

    private let colors: [Color] = [.blue, .red, .green]

    var body: some View {
        VStack(spacing: 8) {
            GeometryReader { geo in
                let spacing: CGFloat = 9
                let w = (geo.size.width - spacing * 2) / 3
                let h = (geo.size.height - spacing * 3) / 4

                VStack(spacing: spacing) {
                    ForEach(0..<4, id: \.self) { row in
                        HStack(spacing: spacing) {
                            ForEach(0..<3, id: \.self) { col in
                                let idx = row * 3 + col
                                if idx < items.count {
                                    Button {
                                        tap(idx)
                                    } label: {
                                        Text(items[idx].found ? "" : (items[idx].circle ? "●" : "■"))
                                            .font(.system(size: min(w, h) * 0.58, weight: .bold))
                                            .foregroundColor(colors[items[idx].colorIndex])
                                            .frame(width: w, height: h)
                                            .background(Color.white)
                                            .clipShape(RoundedRectangle(cornerRadius: 17))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }

            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private func reset() {
        var v = (0..<12).map { _ in
            ColorShapeItem(circle: Bool.random(), colorIndex: Int.random(in: 0..<3))
        }
        if !v.contains(where: { $0.circle && $0.colorIndex == 0 }) {
            v[0] = ColorShapeItem(circle: true, colorIndex: 0)
        }
        items = v
        question = "請點出所有藍色圓形。"
        speech.speak(question)
        feedback = ""
    }

    private func tap(_ idx: Int) {
        guard !items[idx].found else { return }
        if items[idx].circle && items[idx].colorIndex == 0 {
            items[idx].found = true
            Feedback.tap()
            if !items.contains(where: { $0.circle && $0.colorIndex == 0 && !$0.found }) {
                progress.reward()
                feedback = "全部找到了！ ⭐ +1"
                good = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { reset() }
            }
        } else {
            Feedback.error()
            feedback = "這個不是藍色圓形～"
            good = false
        }
    }
}

// MARK: - 數一數

struct CountGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var target = 5
    @State private var answers: [Int] = []
    @State private var feedback = ""
    @State private var good = false

    var body: some View {
        VStack(spacing: 12) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 8) {
                ForEach(0..<target, id: \.self) { _ in
                    Text("🍎")
                        .font(.system(size: 37))
                        .frame(maxWidth: .infinity, minHeight: 56)
                }
            }
            .padding(8)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20))

            HStack(spacing: 10) {
                ForEach(answers, id: \.self) { n in
                    BigAnswerButton(title: "\(n)", tint: Color(red: 0.84, green: 0.92, blue: 1.0)) {
                        choose(n)
                    }
                    .frame(height: 92)
                }
            }

            Spacer(minLength: 0)
            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private func reset() {
        target = Int.random(in: 3...10)
        var set: Set<Int> = [target]
        while set.count < 3 {
            set.insert(max(1, target + Int.random(in: -2...2)))
        }
        answers = Array(set).shuffled()
        question = "請數一數，畫面上有幾顆蘋果？"
        speech.speak(question)
        feedback = ""
    }

    private func choose(_ n: Int) {
        if n == target {
            progress.reward()
            feedback = "答對了！ ⭐ +1"
            good = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { reset() }
        } else {
            Feedback.error()
            feedback = "再數一次看看～"
            good = false
        }
    }
}

// MARK: - 數學

struct MathGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var a = 2
    @State private var b = 1
    @State private var plus = true
    @State private var answer = 3
    @State private var answers: [Int] = []
    @State private var feedback = ""
    @State private var good = false

    var body: some View {
        VStack(spacing: 12) {
            Text("\(a)  \(plus ? "+" : "−")  \(b)  =  ?")
                .font(.system(size: 46, weight: .heavy, design: .rounded))
                .foregroundColor(.brown)
                .minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity, minHeight: 105)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 20))

            ForEach(answers, id: \.self) { n in
                BigAnswerButton(title: "\(n)", tint: Color(red: 1.0, green: 0.92, blue: 0.78)) {
                    choose(n)
                }
                .frame(height: 82)
            }

            Spacer(minLength: 0)
            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private func reset() {
        let maxValue = progress.difficulty == 1 ? 10 : (progress.difficulty == 2 ? 15 : 20)
        plus = Bool.random()
        if plus {
            a = Int.random(in: 1...max(2, maxValue / 2))
            b = Int.random(in: 1...max(2, maxValue / 2))
            answer = a + b
            question = "請算算看，\(a) 加 \(b) 等於多少？"
        } else {
            a = Int.random(in: 2...maxValue)
            b = Int.random(in: 1..<a)
            answer = a - b
            question = "請算算看，\(a) 減 \(b) 等於多少？"
        }

        var set: Set<Int> = [answer]
        while set.count < 3 {
            set.insert(max(0, answer + Int.random(in: -3...3)))
        }
        answers = Array(set).shuffled()
        speech.speak(question)
        feedback = ""
    }

    private func choose(_ n: Int) {
        if n == answer {
            progress.reward()
            if progress.gamesPlayed > 0 && progress.gamesPlayed % 8 == 0 && progress.difficulty < 3 {
                progress.difficulty += 1
            }
            feedback = "答對了！ ⭐ +1"
            good = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { reset() }
        } else {
            Feedback.error()
            feedback = "再算一次看看～"
            good = false
        }
    }
}
