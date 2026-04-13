# GoF 设计模式: Java vs Python 双语对照

> 以 JDK 标准库 + Python 标准库/主流框架的**真实源码**为素材，23 章覆盖全部 GoF 模式。
> 每章同时教会一个设计模式和两种语言的现代特性。

## 适合谁

- 有基础编程经验，想系统理解设计模式的开发者
- 同时使用 Java 和 Python，想理解两种语言设计哲学差异的人
- 想了解 Java 21-26 / Python 3.10-3.13 新特性的人

## 学习路线图

```
入门 ★☆☆                   进阶 ★★☆                        高级 ★★★
──────────              ──────────────                  ──────────────
01 Iterator             06 Factory Method               17 Bridge
02 Strategy      →      07 Facade                →      18 Abstract Factory
03 Template Method      08 Observer                     19 Flyweight
04 Adapter              09 Decorator                    20 Memento
05 Singleton            10 Builder                      21 Mediator
                        11 Prototype                    22 Visitor
                        12 Composite                    23 Interpreter
                        13 Command
                        14 State
                        15 Chain of Responsibility
                        16 Proxy
```

## 每章结构

| 节 | 内容 |
|----|------|
| 模式速览 | 一段话 + 结构图 |
| 本章新语言特性 | 先学语法，再看模式 |
| Java 实战 | JDK 真实源码解析 + 现代重写 |
| Python 实战 | stdlib/框架源码解析 + Pythonic 重写 |
| 两种哲学 | Java vs Python 对比分析 |
| 动手练习 | Java + Python + 跨语言思考题 |
| 回顾与连接 | 前后模式关联 |

## 配套代码

本 textbook 是 [GOF 项目](../src/) 的学习伴侣：
- `src/chXX_Pattern/` — 教科书原版 Java 实现
- `src/chXX_Pattern/usage.md` — 多语言真实应用案例
- `textbook/chXX_Pattern/` — 双语对照深度解析（本书）

## 环境要求

- **Java**: 21+（推荐 26，启用 `--enable-preview`）
- **Python**: 3.10+（推荐 3.12+）
