package com.example.kaoshishuati.data

/**
 * 题库来源：种子库 papers 表里没有独立 source 列，来源编码在 paper.id 前缀里
 * （xiao1000_2026_* / manmanxue2027_* / politics_past_YYYY），据此归类。
 */
enum class QuestionSource(val label: String) {
    XIAO1000("肖1000"),
    MANMANXUE("漫漫学1500"),
    PAST("历年真题");

    companion object {
        fun of(paperId: String): QuestionSource? = when {
            paperId.startsWith("xiao1000") -> XIAO1000
            paperId.startsWith("manmanxue") -> MANMANXUE
            paperId.startsWith("politics_past") -> PAST
            else -> null
        }
    }
}

/**
 * 课程/学科分类。种子库把同一门学科在 questions.category 存成两套拼写
 * （肖1000/真题用全称，漫漫学用简称），dbTerms 收并两套拼写以便跨来源统一筛选。
 */
enum class Course(val label: String, val dbTerms: Set<String>) {
    MAYUAN("马原", setOf("马原", "马克思主义基本原理")),
    MAOZHONGTE("毛中特", setOf("毛中特", "毛泽东思想和中国特色社会主义理论体系概论")),
    XINSI("习思想", setOf("习思想", "习近平新时代中国特色社会主义思想概论")),
    SHIGANG("史纲", setOf("史纲", "中国近现代史纲要")),
    SIXIU("思修", setOf("思修", "思想道德与法治"));

    companion object {
        /** courses 为空表示不限学科；否则 category 命中任一选定学科即算匹配。 */
        fun matches(category: String?, courses: Set<Course>): Boolean =
            courses.isEmpty() || (category != null && courses.any { it.dbTerms.contains(category) })
    }
}

/** 专项刷题的模式。 */
enum class DrillMode(val label: String) {
    SEQUENTIAL("顺序刷题"),
    SHUFFLED("乱序刷题"),
    WRONG("错题重刷"),
}
