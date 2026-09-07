package com.example.kaoshishuati.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 字体族：默认走系统中文（Noto Sans CJK），CJK 字距 0、行高加大利于长题干阅读
private val SansCJK = FontFamily.Default

val Typography = Typography(
    displayLarge   = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Normal,    fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp),
    displayMedium  = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Normal,    fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall   = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),

    headlineLarge  = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineSmall  = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.sp),

    titleLarge     = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium    = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge      = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    bodyMedium     = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    bodySmall      = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),

    labelLarge     = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall     = TextStyle(fontFamily = SansCJK, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)