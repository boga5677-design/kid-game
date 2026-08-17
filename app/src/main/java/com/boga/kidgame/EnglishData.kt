package com.boga.kidgame

data class EnglishWord(val category: String, val emoji: String, val english: String, val chinese: String)

object EnglishWordBank {
    val everyday = listOf(
        EnglishWord("生活用品", "🪥", "Toothbrush", "牙刷"),
        EnglishWord("生活用品", "🧼", "Soap", "肥皂"),
        EnglishWord("生活用品", "🧴", "Shampoo", "洗髮精"),
        EnglishWord("生活用品", "🧻", "Tissue", "衛生紙"),
        EnglishWord("生活用品", "🥄", "Spoon", "湯匙"),
        EnglishWord("生活用品", "🍴", "Fork", "叉子"),
        EnglishWord("生活用品", "🥤", "Cup", "杯子"),
        EnglishWord("生活用品", "🍽️", "Plate", "盤子"),
        EnglishWord("生活用品", "🪑", "Chair", "椅子"),
        EnglishWord("生活用品", "🛏️", "Bed", "床"),
        EnglishWord("生活用品", "🚪", "Door", "門"),
        EnglishWord("生活用品", "🪟", "Window", "窗戶"),
        EnglishWord("生活用品", "💡", "Light", "燈"),
        EnglishWord("生活用品", "📕", "Book", "書"),
        EnglishWord("生活用品", "✏️", "Pencil", "鉛筆"),
        EnglishWord("生活用品", "🎒", "Bag", "書包"),
        EnglishWord("生活用品", "☂️", "Umbrella", "雨傘"),
        EnglishWord("生活用品", "🧸", "Toy", "玩具"),
        EnglishWord("交通工具", "🚗", "Car", "汽車"),
        EnglishWord("交通工具", "🚌", "Bus", "公車"),
        EnglishWord("交通工具", "🚕", "Taxi", "計程車"),
        EnglishWord("交通工具", "🚲", "Bicycle", "腳踏車"),
        EnglishWord("交通工具", "🏍️", "Motorcycle", "機車"),
        EnglishWord("交通工具", "🚆", "Train", "火車"),
        EnglishWord("交通工具", "🚇", "Subway", "捷運"),
        EnglishWord("交通工具", "✈️", "Airplane", "飛機"),
        EnglishWord("交通工具", "🚢", "Ship", "船"),
        EnglishWord("交通工具", "🚑", "Ambulance", "救護車"),
        EnglishWord("交通工具", "🚒", "Fire truck", "消防車"),
        EnglishWord("動物", "🐶", "Dog", "狗"),
        EnglishWord("動物", "🐱", "Cat", "貓"),
        EnglishWord("動物", "🐰", "Rabbit", "兔子"),
        EnglishWord("動物", "🐻", "Bear", "熊"),
        EnglishWord("動物", "🐼", "Panda", "熊貓"),
        EnglishWord("動物", "🦁", "Lion", "獅子"),
        EnglishWord("動物", "🐯", "Tiger", "老虎"),
        EnglishWord("動物", "🐘", "Elephant", "大象"),
        EnglishWord("動物", "🐵", "Monkey", "猴子"),
        EnglishWord("動物", "🐦", "Bird", "鳥"),
        EnglishWord("動物", "🐟", "Fish", "魚"),
        EnglishWord("動物", "🐢", "Turtle", "烏龜"),
        EnglishWord("動物", "🐸", "Frog", "青蛙"),
        EnglishWord("動物", "🦋", "Butterfly", "蝴蝶"),
        EnglishWord("植物", "🌳", "Tree", "樹"),
        EnglishWord("植物", "🌱", "Seedling", "幼苗"),
        EnglishWord("植物", "🌿", "Leaf", "葉子"),
        EnglishWord("植物", "🌹", "Rose", "玫瑰"),
        EnglishWord("植物", "🌻", "Sunflower", "向日葵"),
        EnglishWord("植物", "🌷", "Tulip", "鬱金香"),
        EnglishWord("植物", "🌵", "Cactus", "仙人掌"),
        EnglishWord("植物", "🌾", "Grass", "草"),
        EnglishWord("食物", "🍚", "Rice", "飯"),
        EnglishWord("食物", "🍞", "Bread", "麵包"),
        EnglishWord("食物", "🥚", "Egg", "蛋"),
        EnglishWord("食物", "🥛", "Milk", "牛奶"),
        EnglishWord("食物", "🧀", "Cheese", "起司"),
        EnglishWord("食物", "🍗", "Chicken", "雞肉"),
        EnglishWord("食物", "🍜", "Noodles", "麵"),
        EnglishWord("食物", "🍲", "Soup", "湯"),
        EnglishWord("水果", "🍎", "Apple", "蘋果"),
        EnglishWord("水果", "🍌", "Banana", "香蕉"),
        EnglishWord("水果", "🍊", "Orange", "橘子"),
        EnglishWord("水果", "🍇", "Grapes", "葡萄"),
        EnglishWord("水果", "🍓", "Strawberry", "草莓"),
        EnglishWord("水果", "🍉", "Watermelon", "西瓜"),
        EnglishWord("水果", "🍍", "Pineapple", "鳳梨"),
        EnglishWord("水果", "🥭", "Mango", "芒果"),
        EnglishWord("蔬菜", "🥕", "Carrot", "胡蘿蔔"),
        EnglishWord("蔬菜", "🥦", "Broccoli", "花椰菜"),
        EnglishWord("蔬菜", "🌽", "Corn", "玉米"),
        EnglishWord("蔬菜", "🍅", "Tomato", "番茄"),
        EnglishWord("蔬菜", "🥒", "Cucumber", "小黃瓜"),
        EnglishWord("蔬菜", "🥬", "Lettuce", "生菜"),
        EnglishWord("蔬菜", "🥔", "Potato", "馬鈴薯"),
        EnglishWord("蔬菜", "🍄", "Mushroom", "蘑菇"),
        EnglishWord("運動", "⚽", "Soccer", "足球"),
        EnglishWord("運動", "🏀", "Basketball", "籃球"),
        EnglishWord("運動", "⚾", "Baseball", "棒球"),
        EnglishWord("運動", "🎾", "Tennis", "網球"),
        EnglishWord("運動", "🏸", "Badminton", "羽球"),
        EnglishWord("運動", "🏊", "Swimming", "游泳"),
        EnglishWord("運動", "🏃", "Running", "跑步"),
        EnglishWord("動作", "🚶", "Walk", "走路"),
        EnglishWord("動作", "🏃", "Run", "跑"),
        EnglishWord("動作", "🦘", "Jump", "跳"),
        EnglishWord("動作", "🪑", "Sit", "坐"),
        EnglishWord("動作", "🧍", "Stand", "站"),
        EnglishWord("動作", "🍽️", "Eat", "吃"),
        EnglishWord("動作", "🥤", "Drink", "喝"),
        EnglishWord("動作", "😴", "Sleep", "睡覺"),
        EnglishWord("動作", "👏", "Clap", "拍手"),
        EnglishWord("動作", "👋", "Wave", "揮手"),
        EnglishWord("動作", "😁", "Smile", "微笑"),
        EnglishWord("動作", "🎤", "Sing", "唱歌"),
        EnglishWord("身體部位", "🙂", "Head", "頭"),
        EnglishWord("身體部位", "👀", "Eyes", "眼睛"),
        EnglishWord("身體部位", "👂", "Ears", "耳朵"),
        EnglishWord("身體部位", "👃", "Nose", "鼻子"),
        EnglishWord("身體部位", "👄", "Mouth", "嘴巴"),
        EnglishWord("身體部位", "🦷", "Teeth", "牙齒"),
        EnglishWord("身體部位", "💪", "Arm", "手臂"),
        EnglishWord("身體部位", "✋", "Hand", "手"),
        EnglishWord("身體部位", "🦵", "Leg", "腿"),
        EnglishWord("身體部位", "🦶", "Foot", "腳"),
        EnglishWord("顏色", "🔴", "Red", "紅色"),
        EnglishWord("顏色", "🔵", "Blue", "藍色"),
        EnglishWord("顏色", "🟡", "Yellow", "黃色"),
        EnglishWord("顏色", "🟢", "Green", "綠色"),
        EnglishWord("顏色", "🟠", "Orange", "橘色"),
        EnglishWord("顏色", "🟣", "Purple", "紫色"),
        EnglishWord("顏色", "🩷", "Pink", "粉紅色"),
        EnglishWord("顏色", "⚫", "Black", "黑色"),
        EnglishWord("顏色", "⚪", "White", "白色"),
        EnglishWord("形狀", "●", "Circle", "圓形"),
        EnglishWord("形狀", "▲", "Triangle", "三角形"),
        EnglishWord("形狀", "■", "Square", "正方形"),
        EnglishWord("形狀", "▭", "Rectangle", "長方形"),
        EnglishWord("形狀", "★", "Star", "星形"),
        EnglishWord("形狀", "♥", "Heart", "愛心"),
        EnglishWord("形狀", "⬭", "Oval", "橢圓形"),
        EnglishWord("形狀", "◆", "Diamond", "菱形"),
        EnglishWord("形狀", "⬟", "Pentagon", "五邊形"),
        EnglishWord("形狀", "⬢", "Hexagon", "六邊形"),
    )

    val numbers: List<EnglishWord> = (0..100).map { n ->
        // v0.7.6：數字分類直接顯示阿拉伯數字，不再所有題目都用同一個 🔢 圖示。
        EnglishWord("數字", n.toString(), numberEnglish(n), numberChinese(n))
    }

    val all: List<EnglishWord> = everyday + numbers

    val categories = listOf(
        "生活用品" to "🧸", "交通工具" to "🚗", "動物" to "🐶", "植物" to "🌻",
        "食物" to "🍞", "水果" to "🍎", "蔬菜" to "🥕", "運動" to "⚽",
        "動作" to "🏃", "身體部位" to "👀", "顏色" to "🌈", "形狀" to "🔷", "數字" to "🔢"
    )


    val categoryDescriptions: Map<String, String> = linkedMapOf(
        "生活用品" to "家裡與幼兒園每天會看到",
        "交通工具" to "汽車、公車、捷運與飛機",
        "動物" to "常見寵物與動物園動物",
        "植物" to "樹、花、葉子與小草",
        "食物" to "三餐與孩子常吃的食物",
        "水果" to "生活中常見的水果",
        "蔬菜" to "餐桌上的常見蔬菜",
        "運動" to "球類、游泳與戶外活動",
        "動作" to "走、跑、跳、吃與睡",
        "身體部位" to "眼睛、耳朵、手與腳",
        "形狀" to "圓形、三角形、正方形與星形",
        "數字" to "從 0 學到 100",
        "顏色" to "生活中常見的顏色"
    )

    fun wordsIn(category: String): List<EnglishWord> = all.filter { it.category == category }

    fun categoryDescription(category: String): String =
        categoryDescriptions[category] ?: "一起學習生活中常見的英文"

    fun levelWords(level: Int): List<EnglishWord> {
        if (all.isEmpty()) return emptyList()
        val start = ((level.coerceAtLeast(1) - 1) * 5) % all.size
        return (0 until 5).map { all[(start + it) % all.size] }
    }

    private fun numberEnglish(n: Int): String {
        val ones = listOf("Zero","One","Two","Three","Four","Five","Six","Seven","Eight","Nine")
        val teens = mapOf(10 to "Ten", 11 to "Eleven", 12 to "Twelve", 13 to "Thirteen", 14 to "Fourteen", 15 to "Fifteen", 16 to "Sixteen", 17 to "Seventeen", 18 to "Eighteen", 19 to "Nineteen")
        val tens = mapOf(20 to "Twenty", 30 to "Thirty", 40 to "Forty", 50 to "Fifty", 60 to "Sixty", 70 to "Seventy", 80 to "Eighty", 90 to "Ninety")
        if (n < 10) return ones[n]
        teens[n]?.let { return it }
        if (n == 100) return "One hundred"
        if (n % 10 == 0) return tens[n] ?: ""
        val base = tens[(n / 10) * 10] ?: ""
        return "$base-${ones[n % 10].lowercase()}"
    }

    private fun numberChinese(n: Int): String {
        val digits = listOf("零","一","二","三","四","五","六","七","八","九")
        if (n < 10) return digits[n]
        if (n == 10) return "十"
        if (n < 20) return "十${digits[n % 10]}"
        if (n < 100 && n % 10 == 0) return "${digits[n / 10]}十"
        if (n < 100) return "${digits[n / 10]}十${digits[n % 10]}"
        return "一百"
    }
}
