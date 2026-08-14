import SwiftUI

// MARK: - 迷宮

struct MazeGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var player = CGPoint(x: 30, y: 30)
    @State private var feedback = ""
    @State private var good = false
    @State private var started = false

    var body: some View {
        VStack(spacing: 8) {
            GeometryReader { geo in
                let goal = CGPoint(x: geo.size.width - 34, y: geo.size.height - 34)
                let walls = mazeWalls(in: geo.size)

                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(Color(red: 0.88, green: 0.95, blue: 1.0))

                    ForEach(Array(walls.enumerated()), id: \.offset) { _, wall in
                        RoundedRectangle(cornerRadius: 7)
                            .fill(Color.brown.opacity(0.78))
                            .frame(width: wall.width, height: wall.height)
                            .position(x: wall.midX, y: wall.midY)
                    }

                    Text("🎁")
                        .font(.system(size: 40))
                        .position(goal)

                    Text("⭐")
                        .font(.system(size: 38))
                        .position(player)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    let p = CGPoint(
                                        x: min(max(value.location.x, 24), geo.size.width - 24),
                                        y: min(max(value.location.y, 24), geo.size.height - 24)
                                    )
                                    let box = CGRect(x: p.x - 16, y: p.y - 16, width: 32, height: 32)
                                    if !walls.contains(where: { $0.intersects(box) }) {
                                        player = p
                                        started = true
                                    }
                                }
                                .onEnded { _ in
                                    if distance(player, goal) < 48 {
                                        progress.reward()
                                        good = true
                                        feedback = "找到寶箱！ ⭐ +1"
                                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) {
                                            player = CGPoint(x: 30, y: 30)
                                            feedback = ""
                                        }
                                    } else if !started {
                                        Feedback.error()
                                        feedback = "從星星開始拖曳喔～"
                                        good = false
                                    }
                                }
                        )
                }
            }

            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear {
            question = "拖曳星星，繞過障礙走到寶箱。"
            speech.speak(question)
        }
    }

    private func mazeWalls(in size: CGSize) -> [CGRect] {
        [
            CGRect(x: size.width * 0.19, y: 15, width: size.width * 0.075, height: size.height * 0.58),
            CGRect(x: size.width * 0.39, y: size.height * 0.34, width: size.width * 0.075, height: size.height * 0.61),
            CGRect(x: size.width * 0.59, y: 15, width: size.width * 0.075, height: size.height * 0.58),
            CGRect(x: size.width * 0.79, y: size.height * 0.34, width: size.width * 0.075, height: size.height * 0.61)
        ]
    }

    private func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
        hypot(a.x - b.x, a.y - b.y)
    }
}

// MARK: - 連連看

private struct MatchCard: Identifiable {
    let id = UUID()
    let pairID: Int
    let side: Int
    let emoji: String
}

struct MatchGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var left: [MatchCard] = []
    @State private var right: [MatchCard] = []
    @State private var matched: Set<Int> = []
    @State private var selected: MatchCard?
    @State private var dragStart: MatchCard?
    @State private var dragPoint: CGPoint?
    @State private var feedback = ""
    @State private var good = false

    private let icons = ["🍎", "🐸", "🐼", "🍌"]

    var body: some View {
        VStack(spacing: 8) {
            GeometryReader { geo in
                let cardW = min(126.0, geo.size.width * 0.34)
                let cardH = max(70.0, (geo.size.height - 38) / 4)
                let top: CGFloat = 5
                let rowGap: CGFloat = 8

                ZStack {
                    ForEach(Array(matched), id: \.self) { pairID in
                        if let li = left.firstIndex(where: { $0.pairID == pairID }),
                           let ri = right.firstIndex(where: { $0.pairID == pairID }) {
                            Path { path in
                                let start = CGPoint(
                                    x: cardW + 5,
                                    y: top + CGFloat(li) * (cardH + rowGap) + cardH / 2
                                )
                                let end = CGPoint(
                                    x: geo.size.width - cardW - 5,
                                    y: top + CGFloat(ri) * (cardH + rowGap) + cardH / 2
                                )
                                path.move(to: start)
                                path.addLine(to: end)
                            }
                            .stroke(lineColor(pairID), style: StrokeStyle(lineWidth: 7, lineCap: .round))
                        }
                    }

                    if let start = dragStart, let point = dragPoint,
                       let li = left.firstIndex(where: { $0.id == start.id }) {
                        Path { path in
                            let startPoint = CGPoint(
                                x: cardW + 5,
                                y: top + CGFloat(li) * (cardH + rowGap) + cardH / 2
                            )
                            path.move(to: startPoint)
                            path.addLine(to: point)
                        }
                        .stroke(Color.purple.opacity(0.8), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    }

                    HStack(alignment: .top) {
                        VStack(spacing: rowGap) {
                            ForEach(left) { card in
                                matchCardView(card, width: cardW, height: cardH, geometry: geo)
                            }
                        }

                        Spacer()

                        VStack(spacing: rowGap) {
                            ForEach(right) { card in
                                matchCardView(card, width: cardW, height: cardH, geometry: geo)
                            }
                        }
                    }
                    .padding(.horizontal, 5)
                    .padding(.top, top)
                }
                .coordinateSpace(name: "matchSpace")
            }

            HStack {
                Text("進度：\(matched.count) / 4")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundColor(.brown)
                Spacer()
            }
            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    @ViewBuilder
    private func matchCardView(_ card: MatchCard, width: CGFloat, height: CGFloat, geometry: GeometryProxy) -> some View {
        let isMatched = matched.contains(card.pairID)
        let isSelected = selected?.id == card.id || dragStart?.id == card.id

        Text(card.emoji)
            .font(.system(size: min(46, height * 0.48)))
            .frame(width: width, height: height)
            .background(isMatched ? Color.green.opacity(0.14) : (isSelected ? Color.yellow.opacity(0.25) : Color.white))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(isMatched ? Color.green : (isSelected ? Color.orange : Color.clear), lineWidth: 3)
            )
            .contentShape(Rectangle())
            .onTapGesture {
                guard !isMatched else { return }
                choose(card)
            }
            .gesture(
                DragGesture(minimumDistance: 8, coordinateSpace: .named("matchSpace"))
                    .onChanged { value in
                        guard card.side == 0, !isMatched else { return }
                        dragStart = card
                        dragPoint = value.location
                    }
                    .onEnded { value in
                        guard card.side == 0, !isMatched else { return }
                        let target = rightCard(at: value.location, size: geometry.size, cardWidth: width, cardHeight: height)
                        if let target = target {
                            tryPair(card, target)
                        } else {
                            Feedback.error()
                            good = false
                            feedback = "把線拖到右邊的大方格喔～"
                        }
                        dragStart = nil
                        dragPoint = nil
                    }
            )
    }

    private func reset() {
        left = icons.enumerated().map { MatchCard(pairID: $0.offset, side: 0, emoji: $0.element) }
        right = icons.enumerated().map { MatchCard(pairID: $0.offset, side: 1, emoji: $0.element) }.shuffled()
        matched = []
        selected = nil
        feedback = ""
        question = "找出一樣的圖案，拖曳或點選把它們連起來。"
        speech.speak(question)
    }

    private func choose(_ card: MatchCard) {
        if let selected = selected {
            if selected.id == card.id {
                self.selected = nil
            } else if selected.side != card.side {
                tryPair(selected, card)
                self.selected = nil
            } else {
                self.selected = card
                Feedback.tap()
            }
        } else {
            selected = card
            Feedback.tap()
            feedback = "已選取，請再點相同圖案"
            good = true
        }
    }

    private func tryPair(_ a: MatchCard, _ b: MatchCard) {
        if a.side != b.side && a.pairID == b.pairID {
            matched.insert(a.pairID)
            Feedback.tap()
            good = true
            feedback = "連對了！"
            if matched.count == 4 {
                progress.reward()
                feedback = "全部配對完成！ ⭐ +1"
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { reset() }
            }
        } else {
            Feedback.error()
            good = false
            feedback = "不是這一對，再看看～"
        }
    }

    private func rightCard(at point: CGPoint, size: CGSize, cardWidth: CGFloat, cardHeight: CGFloat) -> MatchCard? {
        let gap: CGFloat = 8
        let top: CGFloat = 5
        let xMin = size.width - cardWidth - 12
        guard point.x >= xMin else { return nil }

        for (index, card) in right.enumerated() {
            let minY = top + CGFloat(index) * (cardHeight + gap)
            let rect = CGRect(x: xMin, y: minY, width: cardWidth + 12, height: cardHeight)
            if rect.insetBy(dx: -8, dy: -8).contains(point), !matched.contains(card.pairID) {
                return card
            }
        }
        return nil
    }

    private func lineColor(_ id: Int) -> Color {
        [.pink, .blue, .orange, .green][id % 4]
    }
}

// MARK: - 記憶挑戰

struct MemoryGameView: View {
    @EnvironmentObject private var progress: ProgressStore
    @Binding var question: String
    let speech: SpeechManager

    @State private var sequence: [Int] = []
    @State private var input: [Int] = []
    @State private var highlighted: Int?
    @State private var accepting = false
    @State private var feedback = ""
    @State private var good = false

    private let colors: [Color] = [.red, .blue, .green, .orange]

    var body: some View {
        VStack(spacing: 10) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(0..<4, id: \.self) { idx in
                    Button {
                        tap(idx)
                    } label: {
                        Text("\(idx + 1)")
                            .font(.system(size: 32, weight: .heavy, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 125)
                            .background(colors[idx])
                            .opacity(highlighted == idx ? 1.0 : 0.76)
                            .scaleEffect(highlighted == idx ? 1.04 : 1.0)
                            .clipShape(RoundedRectangle(cornerRadius: 24))
                    }
                    .buttonStyle(.plain)
                    .disabled(!accepting)
                }
            }

            Text(accepting ? "已完成 \(input.count) / \(sequence.count)" : "先記住 \(sequence.count) 個順序")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(.brown)

            FeedbackBanner(text: feedback, good: good)
        }
        .onAppear { reset() }
    }

    private func reset() {
        sequence = (0..<(3 + progress.difficulty)).map { _ in Int.random(in: 0..<4) }
        input = []
        accepting = false
        highlighted = nil
        feedback = ""
        question = "先記住亮起來的順序。"
        speech.speak(question)

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
            flashStep(0)
        }
    }

    private func flashStep(_ index: Int) {
        guard index < sequence.count else {
            accepting = true
            question = "請照剛才的順序點顏色方塊。"
            speech.speak(question, after: 0.35)
            return
        }

        highlighted = sequence[index]
        Feedback.tap()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.43) {
            highlighted = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
                flashStep(index + 1)
            }
        }
    }

    private func tap(_ idx: Int) {
        guard accepting else { return }
        input.append(idx)
        let pos = input.count - 1

        if sequence[pos] != idx {
            Feedback.error()
            good = false
            feedback = "順序不對，再挑戰一次～"
            accepting = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) { reset() }
        } else if input.count == sequence.count {
            progress.reward()
            good = true
            feedback = "記憶成功！ ⭐ +1"
            accepting = false
            if progress.difficulty < 3 {
                progress.difficulty += 1
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.95) { reset() }
        } else {
            Feedback.tap()
            good = true
            feedback = "很好！繼續～"
        }
    }
}
