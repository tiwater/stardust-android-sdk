# 对话信息页设计升级文档

## 1. 背景与目标

### 1.1 现状问题

`ConversationInfoScreen`（对话信息页）是 Sample APP 中用于展示实时对话记录的核心调试页面，但目前其视觉设计风格与其他页面（`SettingsScreen`、`ComplexSessionConfigScreen`）存在明显割裂：

| 维度 | 对话信息页（现状） | 设置页 / 高级配置页 |
|------|-------------------|---------------------|
| 颜色系统 | `AppColors.Background`（硬编码白色） | `MaterialTheme.colorScheme.background`（主题适配） |
| 卡片容器色 | `Color.White`（硬编码） | `MaterialTheme.colorScheme.surfaceVariant` |
| TopAppBar 背景 | `AppColors.Background`（固定） | 跟随主题默认值 |
| 间距体系 | 魔法数字（`8.dp`、`12.dp`、`16.dp`） | `Spacing.sm / md / lg` 统一常量 |
| 暗黑模式 | **不支持**（全白硬编码） | 完整支持 |
| 卡片圆角 | 记录卡 `8.dp`，摘要卡 `12.dp` | `AppSectionCard` 统一 `16.dp` |
| FilterChip 色 | 硬编码 `AppColors.Blue50 / Gray200` | 应走 `colorScheme.secondaryContainer / outline` |

### 1.2 升级目标

1. **统一主题层**：所有颜色引用改为 `MaterialTheme.colorScheme.*`，告别 `AppColors.*` 硬编码；
2. **支持暗黑模式**：页面在深色系统主题下能正确呈现；
3. **对齐间距规范**：全面采用 `Spacing.*` 常量替换魔法数字；
4. **统一组件语言**：记录卡片设计向 `AppSectionCard` 的圆角与层次规范靠拢；
5. **保留功能完整性**：不改变过滤、展开/折叠、音频播放等任何交互逻辑。

---

## 2. 设计规范（参考基准）

以下规范均来自现有 `ui/theme/Theme.kt`、`ui/theme/Color.kt` 以及 `ui/component/AppSectionCard.kt`，是 Sample APP 已确立的设计语言。

### 2.1 颜色 Token 映射

```
MaterialTheme.colorScheme.background        → 页面 Scaffold 背景
MaterialTheme.colorScheme.surface           → 卡片基础容器色（替代 Color.White）
MaterialTheme.colorScheme.surfaceVariant    → 摘要卡片、JSON 代码块背景（替代 AppColors.Gray50/Gray100）
MaterialTheme.colorScheme.onSurface         → 主要文字（替代 AppColors.Gray900）
MaterialTheme.colorScheme.onSurfaceVariant  → 次要文字、时间戳（替代 AppColors.Gray400/Gray500）
MaterialTheme.colorScheme.outline           → 边框、分隔线（替代 AppColors.Gray200）
MaterialTheme.colorScheme.primary           → 角色标签高亮色（用户语音/文字）
MaterialTheme.colorScheme.secondaryContainer     → FilterChip 选中背景（替代 AppColors.Blue50）
MaterialTheme.colorScheme.onSecondaryContainer   → FilterChip 选中文字（替代 AppColors.Blue600）
MaterialTheme.colorScheme.error             → 错误/停止状态（替代 AppColors.Orange600 的播放停止）
```

> **注意**：助手角色（Green600）与函数调用角色（Orange600）在 `LightColors` 中没有直接映射 Token，这两个语义色在现行主题中是 APP 级品牌色，**建议继续保留为 `AppColors.Green600 / Orange600` 引用**，或在 `Theme.kt` 中补充 `tertiary / tertiaryContainer` 槽位。

### 2.2 间距常量

```kotlin
object Spacing {
    val xs  =  4.dp   // 图标与文字之间
    val sm  =  8.dp   // 列表卡片间距、内部小间隔
    val md  = 16.dp   // 页面水平边距、卡片内边距
    val lg  = 24.dp   // 区块间距
    val xxl = 48.dp   // 空状态顶部留白
}
```

### 2.3 圆角规范

| 层级 | 数值 | 使用场景 |
|------|------|----------|
| 大卡片 | `16.dp` | 摘要卡片（对齐 `AppSectionCard`） |
| 记录卡片 | `12.dp` | 列表中每条对话记录卡 |
| 小徽章 / 标签 | `8.dp` | `AudioInfoChip`、JSON 代码块背景 |

### 2.4 阴影 / 海拔

设置页和高级配置页统一使用 `tonalElevation`（色调海拔）而非投影阴影：

```kotlin
// 推荐
Surface(tonalElevation = 1.dp, ...)   // 记录卡片
Surface(tonalElevation = 0.dp, ...)   // 摘要卡片（surfaceVariant 本身已区分）

// 不推荐（现状）
CardDefaults.cardElevation(defaultElevation = 1.dp)  // 投影阴影
```

---

## 3. 各组件改造方案

### 3.1 `ConversationInfoScreen` — Scaffold

**现状**
```kotlin
Scaffold(
    containerColor = AppColors.Background,
    topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.conversation_info)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppColors.Background,
            ),
        )
    }
)
```

**升级后**
```kotlin
Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.conversation_info)) },
            // 移除显式 colors，跟随 MaterialTheme 默认（自动支持暗黑模式）
        )
    }
)
```

---

### 3.2 `ConversationSummaryCard` — 统计摘要卡片

**现状**
```kotlin
Card(
    colors = CardDefaults.cardColors(containerColor = AppColors.Gray50),
    shape = RoundedCornerShape(12.dp),
)
```

**升级后**
```kotlin
Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),          // 对齐 AppSectionCard
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 0.dp,
) { ... }
```

同步调整内部文字颜色：

| 元素 | 现状 | 升级后 |
|------|------|--------|
| 空状态文字 | `AppColors.Gray400` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| 总计标题 | 默认（未指定） | `MaterialTheme.colorScheme.onSurface` |

---

### 3.3 `StatChip` — 统计徽章

**现状**：角色计数颜色全部硬编码（`AppColors.Blue600`、`AppColors.Green600`、`AppColors.Orange600`）

**升级后**：

- **标签文字**：`AppColors.Gray500` → `MaterialTheme.colorScheme.onSurfaceVariant`
- **用户语音数字**（Blue600）：改为 `MaterialTheme.colorScheme.primary`
- **用户文本数字**（Blue400）：改为 `MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)`
- **助手数字**（Green600）：保留 `AppColors.Green600`（需补充主题 Token 后统一）
- **函数数字**（Orange600）：保留 `AppColors.Orange600`（同上）

---

### 3.4 `RecordFilterChipRow` — 过滤 Chip 行

**现状**
```kotlin
FilterChip(
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppColors.Blue50,
        selectedLabelColor = AppColors.Blue600,
    ),
    border = FilterChipDefaults.filterChipBorder(
        borderColor = AppColors.Gray200,
        selectedBorderColor = AppColors.Blue600,
        ...
    ),
)
```

**升级后**：移除所有 `colors` 和 `border` 的显式参数，使用 Material3 默认主题驱动样式：

```kotlin
FilterChip(
    selected = selectedFilter == filter,
    onClick = { onFilterSelected(filter) },
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    // 不再传递 colors / border，由 colorScheme.secondaryContainer /
    // onSecondaryContainer / outline 自动映射
)
```

这样 FilterChip 在亮色 / 暗色模式下均能正确渲染。

---

### 3.5 `ConversationRecordCard` — 记录卡片

**现状**
```kotlin
Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    shape = RoundedCornerShape(8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
) { ... }
```

**升级后**
```kotlin
Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),             // 圆角提升至 12.dp
    color = MaterialTheme.colorScheme.surface,     // 替代硬编码白色
    tonalElevation = 1.dp,                         // 色调海拔代替投影阴影
) { ... }
```

内部间距统一改用 `Spacing.*`：

| 位置 | 现状 | 升级后 |
|------|------|--------|
| 卡片内边距 | `12.dp` | `Spacing.md`（16.dp）|
| 标题行与内容间距 | `8.dp` | `Spacing.sm` |
| 图标与文字间距 | `4.dp` | `Spacing.xs` |
| 时间戳颜色 | `AppColors.Gray400` | `MaterialTheme.colorScheme.onSurfaceVariant` |

---

### 3.6 `ExpandableText` — 可展开文本

| 元素 | 现状 | 升级后 |
|------|------|--------|
| 正文颜色 | 默认（未指定） | `MaterialTheme.colorScheme.onSurface` |
| 展开/折叠按钮色 | `AppColors.Blue600` | `MaterialTheme.colorScheme.primary` |

---

### 3.7 `ExpandableJsonText` — 可展开 JSON 代码块

**现状**
```kotlin
.background(AppColors.Gray50, RoundedCornerShape(4.dp))
```

**升级后**
```kotlin
.background(
    MaterialTheme.colorScheme.surfaceVariant,
    RoundedCornerShape(Spacing.sm),  // 8.dp
)
```

展开/折叠按钮同 3.6 统一为 `MaterialTheme.colorScheme.primary`。

---

### 3.8 `AudioInfoChip` — 音频分段徽章

**现状**
```kotlin
.background(AppColors.Gray100, RoundedCornerShape(4.dp))
// 图标 tint: AppColors.Gray500
// 文字 color: AppColors.Gray500
```

**升级后**
```kotlin
.background(
    MaterialTheme.colorScheme.surfaceVariant,
    RoundedCornerShape(Spacing.sm),
)
// 图标 tint: MaterialTheme.colorScheme.onSurfaceVariant
// 文字 color: MaterialTheme.colorScheme.onSurfaceVariant
```

---

### 3.9 `AudioPlaybackButton` — 播放/停止按钮

| 状态 | 现状 | 升级后 |
|------|------|--------|
| 播放 | `AppColors.Blue600` | `MaterialTheme.colorScheme.primary` |
| 停止 | `AppColors.Orange600` | `MaterialTheme.colorScheme.error` |

---

### 3.10 `EmptyStateMessage` — 空状态

| 元素 | 现状 | 升级后 |
|------|------|--------|
| 文字颜色 | `AppColors.Gray400` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| 顶部留白 | `padding(top = 48.dp)` | `padding(top = Spacing.xxl)` |

---

### 3.11 列表底部提示文字

```kotlin
// 现状
color = AppColors.Gray400

// 升级后
color = MaterialTheme.colorScheme.onSurfaceVariant
```

---

## 4. 差异对比总结

| 组件 | 改动类型 | 关键变更 |
|------|----------|----------|
| `Scaffold` | 颜色 | `AppColors.Background` → `colorScheme.background` |
| `TopAppBar` | 颜色 | 移除硬编码白色，跟随主题 |
| `ConversationSummaryCard` | 颜色 + 圆角 | `AppColors.Gray50` → `surfaceVariant`；圆角 12→16 |
| `StatChip` | 颜色 | 标签色走 `onSurfaceVariant`；用户计数走 `primary` |
| `RecordFilterChipRow` | 颜色 | 移除所有硬编码，走 Material3 默认 |
| `ConversationRecordCard` | 颜色 + 圆角 + 阴影 | `Color.White` → `surface`；8→12 圆角；投影→色调海拔 |
| `ExpandableText` | 颜色 | 展开按钮走 `primary` |
| `ExpandableJsonText` | 颜色 + 圆角 | `Gray50` → `surfaceVariant`；`4.dp` → `Spacing.sm` |
| `AudioInfoChip` | 颜色 | `Gray100/Gray500` → `surfaceVariant/onSurfaceVariant` |
| `AudioPlaybackButton` | 颜色 | 播放走 `primary`，停止走 `error` |
| `EmptyStateMessage` | 颜色 + 间距 | `Gray400` → `onSurfaceVariant`；`48.dp` → `Spacing.xxl` |
| **间距（全局）** | 间距 | 所有魔法数字改为 `Spacing.*` 常量 |

---

## 5. 暗黑模式效果预期

升级后，`DarkColors` 将自动生效：

```
background        → 0xFF0F172A（深蓝黑）
surface           → 0xFF1E293B（深蓝灰）
surfaceVariant    → 0xFF334155（中蓝灰）
onSurface         → 0xFFF1F5F9（浅灰白）
onSurfaceVariant  → 0xFF94A3B8（中灰）
primary           → AppColors.Blue400（0xFF60A5FA，亮蓝）
secondaryContainer→ 0xFF1E3A5F（深蓝，FilterChip 选中背景）
```

页面整体将从「纯白硬编码」变为「深色主题友好」，与 SettingsScreen 和 ComplexSessionConfigScreen 在夜间模式下保持视觉一致。

---

## 6. 实施建议

1. **优先级高**：先修复暗黑模式兼容性（`Scaffold`、`TopAppBar`、`ConversationRecordCard` 的硬编码白色），避免在深色系统下出现明显视觉 bug；
2. **分步推进**：颜色改动与间距改动可分两个 commit，便于 Review；
3. **补充主题 Token**（可选）：如希望助手色（Green）和函数色（Orange）也完整支持暗黑模式，建议在 `Theme.kt` 的 `LightColors` 和 `DarkColors` 中为 `tertiary / onTertiary / tertiaryContainer / onTertiaryContainer` 赋值，并在相关组件中引用；
4. **无需改动**：`ExpandableText` 的折叠逻辑、`LazyColumn` 的 `key` 策略、音频播放交互均无需变动。
