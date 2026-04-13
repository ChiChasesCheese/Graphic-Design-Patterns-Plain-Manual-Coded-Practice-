# 章节映射表

Textbook 章节按**学习难度**重新排列，非原书顺序。

| Textbook | 模式 | 类型 | 原书目录 | 难度 | 引入的 Java 特性 | 引入的 Python 特性 |
|----------|------|------|----------|------|-----------------|-------------------|
| 01 | Iterator | 行为 | src/ch01 | ★☆☆ | `var`, enhanced for | generator, `yield` |
| 02 | Strategy | 行为 | src/ch10 | ★☆☆ | lambda, `::` | `lambda`, `Callable` |
| 03 | Template Method | 行为 | src/ch03 | ★☆☆ | `@Override`, abstract | `abc.ABC` |
| 04 | Adapter | 结构 | src/ch02 | ★☆☆ | default method | context manager |
| 05 | Singleton | 创建 | src/ch05 | ★☆☆ | `enum` | `__new__`, module singleton |
| 06 | Factory Method | 创建 | src/ch04 | ★★☆ | `sealed interface` | `Protocol`, `@classmethod` |
| 07 | Facade | 结构 | src/ch15 | ★☆☆ | package-private | `__all__`, `_` |
| 08 | Observer | 行为 | src/ch17 | ★★☆ | `record` | `@dataclass` |
| 09 | Decorator | 结构 | src/ch12 | ★★☆ | try-with-resources | `functools.wraps` |
| 10 | Builder | 创建 | src/ch07 | ★★☆ | text block, record+builder | `field()`, `__post_init__` |
| 11 | Prototype | 创建 | src/ch06 | ★★☆ | record wither | `dataclasses.replace()` |
| 12 | Composite | 结构 | src/ch11 | ★★☆ | sealed + record 代数类型 | `match` 基础 |
| 13 | Command | 行为 | src/ch22 | ★★☆ | `@FunctionalInterface` | `__call__`, `partial` |
| 14 | State | 行为 | src/ch19 | ★★☆ | switch expression | `enum.Enum`, match/case |
| 15 | Chain of Resp. | 行为 | src/ch14 | ★★☆ | `Optional` 链 | match + guard |
| 16 | Proxy | 结构 | src/ch21 | ★★☆ | 动态代理 | descriptor protocol |
| 17 | Bridge | 结构 | src/ch09 | ★★★ | `ServiceLoader` | `importlib` |
| 18 | Abstract Factory | 创建 | src/ch08 | ★★★ | sealed+record 家族 | `__init_subclass__` |
| 19 | Flyweight | 结构 | src/ch20 | ★★★ | `@ValueBased` | `__slots__` |
| 20 | Memento | 行为 | src/ch18 | ★★★ | record pattern matching | match class pattern |
| 21 | Mediator | 行为 | src/ch16 | ★★★ | virtual threads | `asyncio` |
| 22 | Visitor | 行为 | src/ch13 | ★★★ | switch+sealed 替代双分派 | match 嵌套模式 |
| 23 | Interpreter | 行为 | src/ch23 | ★★★ | **综合** capstone | **综合** capstone |
