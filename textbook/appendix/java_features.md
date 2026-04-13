# Java 16-26 新特性速查

本书用到的 Java 现代特性，按版本排列。

## Java 8 (2014) — 基础

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| Lambda | `(a, b) -> a + b` | Ch02 Strategy |
| 方法引用 | `String::length` | Ch02 Strategy |
| `@FunctionalInterface` | `@FunctionalInterface interface Command { void execute(); }` | Ch13 Command |
| `Optional` | `Optional.of(x).map(f).orElse(default)` | Ch15 CoR |
| Stream API | `list.stream().filter().map().collect()` | Ch01 Iterator |
| `default` 方法 | `default void forEach(Consumer c) {...}` | Ch04 Adapter |

## Java 10 (2018)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `var` 局部推断 | `var list = new ArrayList<String>()` | Ch01 Iterator |
| `List.copyOf()` | `List.copyOf(mutableList)` | Ch11 Prototype |

## Java 13 (2019)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| Text Blocks | `"""..."""` | Ch10 Builder |

## Java 14 (2020)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| Switch 表达式 | `switch (x) { case A -> 1; case B -> 2; }` | Ch14 State |
| `record` 预览 | `record Point(int x, int y) {}` | Ch08 Observer |

## Java 16 (2021)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `record` 正式 | `record Event(String type, Object data) {}` | Ch08 Observer |
| `instanceof` 模式匹配 | `if (obj instanceof String s) { s.length(); }` | Ch20 Memento |

## Java 17 (2021 LTS)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `sealed` 接口/类 | `sealed interface Shape permits Circle, Rect {}` | Ch06 Factory Method |
| `permits` | 限制子类范围 | Ch06 Factory Method |

## Java 21 (2023 LTS)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| Record Patterns | `case Point(var x, var y)` | Ch20 Memento |
| Switch 模式匹配 | `switch (shape) { case Circle c -> ... }` | Ch22 Visitor |
| Virtual Threads | `Thread.startVirtualThread(() -> ...)` | Ch21 Mediator |
| Structured Concurrency | `StructuredTaskScope` (preview) | Ch21 Mediator |

## Java 22-26 (2024-2026)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `_` 未命名变量 | `case Rect(var _, var h) -> h` | Ch22 Visitor |
| `import static java.lang.IO.println` | 简化输出 | 全书 Main 类 |
| Compact Constructor | `public Email { address = address.toLowerCase(); }` | Ch10 Builder |

---

## 常用组合技

```java
// sealed + record + switch = 代数数据类型 + 模式匹配（Ch12, Ch22, Ch23）
sealed interface Expr permits Num, Add {}
record Num(int value) implements Expr {}
record Add(Expr left, Expr right) implements Expr {}

String pretty(Expr e) {
    return switch (e) {
        case Num(var v) -> String.valueOf(v);
        case Add(var l, var r) -> "(%s + %s)".formatted(pretty(l), pretty(r));
    };
}
```
