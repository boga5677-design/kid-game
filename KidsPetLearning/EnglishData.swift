import Foundation

struct EnglishWord: Identifiable, Equatable, Hashable {
    let id = UUID()
    let category: String
    let emoji: String
    let english: String
    let chinese: String
}

enum EnglishWordBank {
    static let everyday: [EnglishWord] = [
        EnglishWord(category: "生活用品", emoji: "🪥", english: "Toothbrush", chinese: "牙刷"),
        EnglishWord(category: "生活用品", emoji: "🧼", english: "Soap", chinese: "肥皂"),
        EnglishWord(category: "生活用品", emoji: "🧴", english: "Shampoo", chinese: "洗髮精"),
        EnglishWord(category: "生活用品", emoji: "🧻", english: "Tissue", chinese: "衛生紙"),
        EnglishWord(category: "生活用品", emoji: "🥄", english: "Spoon", chinese: "湯匙"),
        EnglishWord(category: "生活用品", emoji: "🍴", english: "Fork", chinese: "叉子"),
        EnglishWord(category: "生活用品", emoji: "🥤", english: "Cup", chinese: "杯子"),
        EnglishWord(category: "生活用品", emoji: "🍽️", english: "Plate", chinese: "盤子"),
        EnglishWord(category: "生活用品", emoji: "🪑", english: "Chair", chinese: "椅子"),
        EnglishWord(category: "生活用品", emoji: "🛏️", english: "Bed", chinese: "床"),
        EnglishWord(category: "生活用品", emoji: "🚪", english: "Door", chinese: "門"),
        EnglishWord(category: "生活用品", emoji: "🪟", english: "Window", chinese: "窗戶"),
        EnglishWord(category: "生活用品", emoji: "💡", english: "Light", chinese: "燈"),
        EnglishWord(category: "生活用品", emoji: "📕", english: "Book", chinese: "書"),
        EnglishWord(category: "生活用品", emoji: "✏️", english: "Pencil", chinese: "鉛筆"),
        EnglishWord(category: "生活用品", emoji: "🎒", english: "Bag", chinese: "書包"),
        EnglishWord(category: "生活用品", emoji: "☂️", english: "Umbrella", chinese: "雨傘"),
        EnglishWord(category: "生活用品", emoji: "🧸", english: "Toy", chinese: "玩具"),
        EnglishWord(category: "交通工具", emoji: "🚗", english: "Car", chinese: "汽車"),
        EnglishWord(category: "交通工具", emoji: "🚌", english: "Bus", chinese: "公車"),
        EnglishWord(category: "交通工具", emoji: "🚕", english: "Taxi", chinese: "計程車"),
        EnglishWord(category: "交通工具", emoji: "🚲", english: "Bicycle", chinese: "腳踏車"),
        EnglishWord(category: "交通工具", emoji: "🏍️", english: "Motorcycle", chinese: "機車"),
        EnglishWord(category: "交通工具", emoji: "🚆", english: "Train", chinese: "火車"),
        EnglishWord(category: "交通工具", emoji: "🚇", english: "Subway", chinese: "捷運"),
        EnglishWord(category: "交通工具", emoji: "✈️", english: "Airplane", chinese: "飛機"),
        EnglishWord(category: "交通工具", emoji: "🚢", english: "Ship", chinese: "船"),
        EnglishWord(category: "交通工具", emoji: "🚑", english: "Ambulance", chinese: "救護車"),
        EnglishWord(category: "交通工具", emoji: "🚒", english: "Fire truck", chinese: "消防車"),
        EnglishWord(category: "動物", emoji: "🐶", english: "Dog", chinese: "狗"),
        EnglishWord(category: "動物", emoji: "🐱", english: "Cat", chinese: "貓"),
        EnglishWord(category: "動物", emoji: "🐰", english: "Rabbit", chinese: "兔子"),
        EnglishWord(category: "動物", emoji: "🐻", english: "Bear", chinese: "熊"),
        EnglishWord(category: "動物", emoji: "🐼", english: "Panda", chinese: "熊貓"),
        EnglishWord(category: "動物", emoji: "🦁", english: "Lion", chinese: "獅子"),
        EnglishWord(category: "動物", emoji: "🐯", english: "Tiger", chinese: "老虎"),
        EnglishWord(category: "動物", emoji: "🐘", english: "Elephant", chinese: "大象"),
        EnglishWord(category: "動物", emoji: "🐵", english: "Monkey", chinese: "猴子"),
        EnglishWord(category: "動物", emoji: "🐦", english: "Bird", chinese: "鳥"),
        EnglishWord(category: "動物", emoji: "🐟", english: "Fish", chinese: "魚"),
        EnglishWord(category: "動物", emoji: "🐢", english: "Turtle", chinese: "烏龜"),
        EnglishWord(category: "動物", emoji: "🐸", english: "Frog", chinese: "青蛙"),
        EnglishWord(category: "動物", emoji: "🦋", english: "Butterfly", chinese: "蝴蝶"),
        EnglishWord(category: "植物", emoji: "🌳", english: "Tree", chinese: "樹"),
        EnglishWord(category: "植物", emoji: "🌱", english: "Seedling", chinese: "幼苗"),
        EnglishWord(category: "植物", emoji: "🌿", english: "Leaf", chinese: "葉子"),
        EnglishWord(category: "植物", emoji: "🌹", english: "Rose", chinese: "玫瑰"),
        EnglishWord(category: "植物", emoji: "🌻", english: "Sunflower", chinese: "向日葵"),
        EnglishWord(category: "植物", emoji: "🌷", english: "Tulip", chinese: "鬱金香"),
        EnglishWord(category: "植物", emoji: "🌵", english: "Cactus", chinese: "仙人掌"),
        EnglishWord(category: "植物", emoji: "🌾", english: "Grass", chinese: "草"),
        EnglishWord(category: "食物", emoji: "🍚", english: "Rice", chinese: "飯"),
        EnglishWord(category: "食物", emoji: "🍞", english: "Bread", chinese: "麵包"),
        EnglishWord(category: "食物", emoji: "🥚", english: "Egg", chinese: "蛋"),
        EnglishWord(category: "食物", emoji: "🥛", english: "Milk", chinese: "牛奶"),
        EnglishWord(category: "食物", emoji: "🧀", english: "Cheese", chinese: "起司"),
        EnglishWord(category: "食物", emoji: "🍗", english: "Chicken", chinese: "雞肉"),
        EnglishWord(category: "食物", emoji: "🍜", english: "Noodles", chinese: "麵"),
        EnglishWord(category: "食物", emoji: "🍲", english: "Soup", chinese: "湯"),
        EnglishWord(category: "水果", emoji: "🍎", english: "Apple", chinese: "蘋果"),
        EnglishWord(category: "水果", emoji: "🍌", english: "Banana", chinese: "香蕉"),
        EnglishWord(category: "水果", emoji: "🍊", english: "Orange", chinese: "橘子"),
        EnglishWord(category: "水果", emoji: "🍇", english: "Grapes", chinese: "葡萄"),
        EnglishWord(category: "水果", emoji: "🍓", english: "Strawberry", chinese: "草莓"),
        EnglishWord(category: "水果", emoji: "🍉", english: "Watermelon", chinese: "西瓜"),
        EnglishWord(category: "水果", emoji: "🍍", english: "Pineapple", chinese: "鳳梨"),
        EnglishWord(category: "水果", emoji: "🥭", english: "Mango", chinese: "芒果"),
        EnglishWord(category: "蔬菜", emoji: "🥕", english: "Carrot", chinese: "胡蘿蔔"),
        EnglishWord(category: "蔬菜", emoji: "🥦", english: "Broccoli", chinese: "花椰菜"),
        EnglishWord(category: "蔬菜", emoji: "🌽", english: "Corn", chinese: "玉米"),
        EnglishWord(category: "蔬菜", emoji: "🍅", english: "Tomato", chinese: "番茄"),
        EnglishWord(category: "蔬菜", emoji: "🥒", english: "Cucumber", chinese: "小黃瓜"),
        EnglishWord(category: "蔬菜", emoji: "🥬", english: "Lettuce", chinese: "生菜"),
        EnglishWord(category: "蔬菜", emoji: "🥔", english: "Potato", chinese: "馬鈴薯"),
        EnglishWord(category: "蔬菜", emoji: "🍄", english: "Mushroom", chinese: "蘑菇"),
        EnglishWord(category: "運動", emoji: "⚽", english: "Soccer", chinese: "足球"),
        EnglishWord(category: "運動", emoji: "🏀", english: "Basketball", chinese: "籃球"),
        EnglishWord(category: "運動", emoji: "⚾", english: "Baseball", chinese: "棒球"),
        EnglishWord(category: "運動", emoji: "🎾", english: "Tennis", chinese: "網球"),
        EnglishWord(category: "運動", emoji: "🏸", english: "Badminton", chinese: "羽球"),
        EnglishWord(category: "運動", emoji: "🏊", english: "Swimming", chinese: "游泳"),
        EnglishWord(category: "運動", emoji: "🏃", english: "Running", chinese: "跑步"),
        EnglishWord(category: "動作", emoji: "🚶", english: "Walk", chinese: "走路"),
        EnglishWord(category: "動作", emoji: "🏃", english: "Run", chinese: "跑"),
        EnglishWord(category: "動作", emoji: "🦘", english: "Jump", chinese: "跳"),
        EnglishWord(category: "動作", emoji: "🪑", english: "Sit", chinese: "坐"),
        EnglishWord(category: "動作", emoji: "🧍", english: "Stand", chinese: "站"),
        EnglishWord(category: "動作", emoji: "🍽️", english: "Eat", chinese: "吃"),
        EnglishWord(category: "動作", emoji: "🥤", english: "Drink", chinese: "喝"),
        EnglishWord(category: "動作", emoji: "😴", english: "Sleep", chinese: "睡覺"),
        EnglishWord(category: "動作", emoji: "👏", english: "Clap", chinese: "拍手"),
        EnglishWord(category: "動作", emoji: "👋", english: "Wave", chinese: "揮手"),
        EnglishWord(category: "動作", emoji: "😁", english: "Smile", chinese: "微笑"),
        EnglishWord(category: "動作", emoji: "🎤", english: "Sing", chinese: "唱歌"),
        EnglishWord(category: "身體部位", emoji: "🙂", english: "Head", chinese: "頭"),
        EnglishWord(category: "身體部位", emoji: "👀", english: "Eyes", chinese: "眼睛"),
        EnglishWord(category: "身體部位", emoji: "👂", english: "Ears", chinese: "耳朵"),
        EnglishWord(category: "身體部位", emoji: "👃", english: "Nose", chinese: "鼻子"),
        EnglishWord(category: "身體部位", emoji: "👄", english: "Mouth", chinese: "嘴巴"),
        EnglishWord(category: "身體部位", emoji: "🦷", english: "Teeth", chinese: "牙齒"),
        EnglishWord(category: "身體部位", emoji: "💪", english: "Arm", chinese: "手臂"),
        EnglishWord(category: "身體部位", emoji: "✋", english: "Hand", chinese: "手"),
        EnglishWord(category: "身體部位", emoji: "🦵", english: "Leg", chinese: "腿"),
        EnglishWord(category: "身體部位", emoji: "🦶", english: "Foot", chinese: "腳"),
        EnglishWord(category: "顏色", emoji: "🔴", english: "Red", chinese: "紅色"),
        EnglishWord(category: "顏色", emoji: "🔵", english: "Blue", chinese: "藍色"),
        EnglishWord(category: "顏色", emoji: "🟡", english: "Yellow", chinese: "黃色"),
        EnglishWord(category: "顏色", emoji: "🟢", english: "Green", chinese: "綠色"),
        EnglishWord(category: "顏色", emoji: "🟠", english: "Orange", chinese: "橘色"),
        EnglishWord(category: "顏色", emoji: "🟣", english: "Purple", chinese: "紫色"),
        EnglishWord(category: "顏色", emoji: "🩷", english: "Pink", chinese: "粉紅色"),
        EnglishWord(category: "顏色", emoji: "⚫", english: "Black", chinese: "黑色"),
        EnglishWord(category: "顏色", emoji: "⚪", english: "White", chinese: "白色"),
        EnglishWord(category: "形狀", emoji: "●", english: "Circle", chinese: "圓形"),
        EnglishWord(category: "形狀", emoji: "▲", english: "Triangle", chinese: "三角形"),
        EnglishWord(category: "形狀", emoji: "■", english: "Square", chinese: "正方形"),
        EnglishWord(category: "形狀", emoji: "▭", english: "Rectangle", chinese: "長方形"),
        EnglishWord(category: "形狀", emoji: "★", english: "Star", chinese: "星形"),
        EnglishWord(category: "形狀", emoji: "♥", english: "Heart", chinese: "愛心"),
        EnglishWord(category: "形狀", emoji: "⬭", english: "Oval", chinese: "橢圓形"),
        EnglishWord(category: "形狀", emoji: "◆", english: "Diamond", chinese: "菱形"),
        EnglishWord(category: "形狀", emoji: "⬟", english: "Pentagon", chinese: "五邊形"),
        EnglishWord(category: "形狀", emoji: "⬢", english: "Hexagon", chinese: "六邊形"),
    ]

    static let numbers: [EnglishWord] = (0...100).map { number in
        EnglishWord(category: "數字", emoji: "🔢", english: numberEnglish(number), chinese: numberChinese(number))
    }

    static let all: [EnglishWord] = everyday + numbers

    static let categories: [(String, String)] = [
        ("生活用品", "🧸"), ("交通工具", "🚗"), ("動物", "🐶"), ("植物", "🌻"),
        ("食物", "🍞"), ("水果", "🍎"), ("蔬菜", "🥕"), ("運動", "⚽"),
        ("動作", "🏃"), ("身體部位", "👀"), ("顏色", "🌈"), ("形狀", "🔷"), ("數字", "🔢")
    ]

    static func levelWords(_ level: Int) -> [EnglishWord] {
        let pool = all
        guard !pool.isEmpty else { return [] }
        let start = ((max(level, 1) - 1) * 5) % pool.count
        return (0..<5).map { pool[(start + $0) % pool.count] }
    }

    private static func numberEnglish(_ n: Int) -> String {
        let ones = ["Zero","One","Two","Three","Four","Five","Six","Seven","Eight","Nine"]
        let teens = [10:"Ten",11:"Eleven",12:"Twelve",13:"Thirteen",14:"Fourteen",15:"Fifteen",16:"Sixteen",17:"Seventeen",18:"Eighteen",19:"Nineteen"]
        let tens = [20:"Twenty",30:"Thirty",40:"Forty",50:"Fifty",60:"Sixty",70:"Seventy",80:"Eighty",90:"Ninety"]
        if n < 10 { return ones[n] }
        if let teen = teens[n] { return teen }
        if n == 100 { return "One hundred" }
        if n % 10 == 0 { return tens[n] ?? "" }
        let base = tens[(n / 10) * 10] ?? ""
        return "\(base)-\(ones[n % 10].lowercased())"
    }

    private static func numberChinese(_ n: Int) -> String {
        let digits = ["零","一","二","三","四","五","六","七","八","九"]
        if n < 10 { return digits[n] }
        if n == 10 { return "十" }
        if n < 20 { return "十\(digits[n % 10])" }
        if n < 100 && n % 10 == 0 { return "\(digits[n / 10])十" }
        if n < 100 { return "\(digits[n / 10])十\(digits[n % 10])" }
        return "一百"
    }
}
