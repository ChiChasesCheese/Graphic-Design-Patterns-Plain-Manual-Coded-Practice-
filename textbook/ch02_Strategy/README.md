# Chapter 02: Strategy

> 类型: 行为 | 难度: ★☆☆ | 原书: `src/ch10_Strategy/` | 前置: Ch01

---

## 模式速览

**Strategy 解决什么问题？**

当一个操作有多种实现方式（算法）时，朴素做法是在客户端写 `if/else` 或 `switch` 来选择逻辑。这样每次新增一种算法，都要修改客户端代码，违反开闭原则。

Strategy 模式将"可变的算法"封装成独立对象，客户端只依赖统一接口，算法可以在**运行时自由替换**，彼此完全解耦。

**结构速览**

```
         ┌──────────────┐         uses         ┌─────────────────┐
         │   Context    │ ──────────────────►  │  <<interface>>  │
         │              │                      │    Strategy     │
         │ - strategy   │                      │ + execute()     │
         │ + doWork()   │                      └────────┬────────┘
         └──────────────┘                               │
                                           ┌────────────┴────────────┐
                                           ▼                         ▼
                                  ┌────────────────┐      ┌────────────────────┐
                                  │ConcreteStrategy│      │ConcreteStrategyB   │
                                  │      A         │      │                    │
                                  └────────────────┘      └────────────────────┘
```

核心关系：Context **组合** Strategy（has-a），而非继承（is-a）。运行时只需把 Context 持有的 strategy 字段换成另一个实现，行为即改变。

---

## 本章新语言特性

### Java: lambda 表达式与方法引用

Java 8 之前，只有一个抽象方法的接口（函数式接口）必须用匿名内部类实现；Java 8 引入 lambda 和方法引用后，Strategy 的实现可以极度简化。

```java
// 1. lambda 表达式 —— 内联定义策略
Comparator<String> byLength = (a, b) -> a.length() - b.length();

// 2. 方法引用 —— 直接引用已有方法
//    语法: 类名::静态方法  /  实例::实例方法  /  类名::实例方法
Comparator<String> byLengthRef = Comparator.comparingInt(String::length);

// 3. 实例方法引用（绑定到具体对象）
String prefix = "hello";
Predicate<String> startsWith = prefix::startsWith;
```

`@FunctionalInterface` 注解标记接口只有一个抽象方法，编译器会强制检查，同时允许 lambda 直接赋值给该接口类型。

```java
@FunctionalInterface
interface FormatStrategy {
    String format(String text);  // 唯一抽象方法
}
```

### Python: lambda、一等函数与 `typing.Callable`

Python 函数本身就是对象，可以赋值给变量、作为参数传递、存入列表——这叫**一等函数（first-class function）**。

```python
# 1. 具名函数作为策略
def to_upper(text: str) -> str:
    return text.upper()

# 2. lambda —— 单表达式匿名函数
to_lower = lambda text: text.lower()

# 3. 类型标注: typing.Callable[[参数类型, ...], 返回类型]
from typing import Callable

def apply(text: str, strategy: Callable[[str], str]) -> str:
    return strategy(text)

apply("hello", to_upper)   # "HELLO"
apply("WORLD", to_lower)   # "world"

# 4. 列表推导式快速批量应用策略
strategies = [str.upper, str.lower, str.title]
results = [s("hello world") for s in strategies]
# ["HELLO WORLD", "hello world", "Hello World"]
```

---

## Java 实战: `java.util.Comparator`

### 源码解析

`Comparator<T>` 是 Java 标准库中最典型的 Strategy 接口：

```java
// JDK 源码（简化）
@FunctionalInterface
public interface Comparator<T> {
    // 这就是"策略方法"
    int compare(T o1, T o2);

    // 默认方法：组合策略（链式调用）
    default Comparator<T> thenComparing(Comparator<? super T> other) { ... }
    default Comparator<T> reversed() { ... }
}
```

`Collections.sort(list, comparator)` 是 **Context**：它不关心"怎么比较"，只负责"按给定策略排序"。

```java
import java.util.*;
import java.util.function.*;

List<String> words = Arrays.asList("banana", "fig", "apple", "kiwi");

// --- 内置策略 ---
words.sort(Comparator.naturalOrder());         // 字典序
words.sort(Comparator.reverseOrder());         // 反向字典序

// --- 提取 key 后比较（最常用写法）---
words.sort(Comparator.comparingInt(String::length));  // 按长度升序

// --- 链式组合：先按长度，长度相同再按字典序 ---
words.sort(
    Comparator.comparingInt(String::length)
              .thenComparing(Comparator.naturalOrder())
);

// --- 作用于复杂对象 ---
record User(String name, int age) {}
List<User> users = List.of(
    new User("Alice", 30),
    new User("Bob", 25),
    new User("Carol", 30)
);

users.stream()
     .sorted(Comparator.comparingInt(User::age)
                       .thenComparing(User::name))
     .forEach(System.out::println);
```

`java.util.function` 包中的 `Predicate<T>` 和 `Function<T, R>` 也是 Strategy 的变体：

```java
// Predicate<T>: 判断策略（返回 boolean）
Predicate<String> isLong   = s -> s.length() > 5;
Predicate<String> startsA  = s -> s.startsWith("A");
Predicate<String> combined = isLong.and(startsA);  // 组合策略

// Function<T, R>: 变换策略（返回另一个类型）
Function<String, Integer> strLen  = String::length;
Function<String, String>  toUpper = String::toUpperCase;
Function<String, String>  pipeline = toUpper.andThen(s -> "[" + s + "]");
System.out.println(pipeline.apply("hello"));  // "[HELLO]"
```

### 现代重写

Java 8 之前，每个策略都需要完整的匿名内部类，样板代码多且难以阅读：

```java
// Java 8 之前：匿名内部类（verbose）
Collections.sort(words, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.length() - b.length();
    }
});

// Java 8+：lambda（简洁）
words.sort((a, b) -> a.length() - b.length());

// Java 8+：方法引用（最惯用）
words.sort(Comparator.comparingInt(String::length));
```

三种写法语义完全相同，现代代码应优先使用方法引用或 `Comparator.comparing*` 工厂方法，可读性最高。

---

## Python 实战: `sorted()` 的 `key=` 参数

### 源码解析

Python 内置的 `sorted()` 和列表方法 `.sort()` 都接受一个 `key` 参数，该参数接收一个函数——这个函数就是排序策略：

```python
from typing import Callable

words = ["banana", "fig", "apple", "kiwi"]

# --- 内置函数作为策略 ---
sorted(words, key=len)              # 按长度升序: ['fig', 'kiwi', 'apple', 'banana']
sorted(words, key=str.lower)        # 大小写不敏感排序

# --- lambda 内联策略 ---
sorted(words, key=lambda w: w[-1])  # 按最后一个字母排序

# --- 作用于对象 ---
from dataclasses import dataclass

@dataclass
class User:
    name: str
    age: int

users = [User("Alice", 30), User("Bob", 25), User("Carol", 30)]
sorted(users, key=lambda u: u.age)                    # 按年龄升序
sorted(users, key=lambda u: (u.age, u.name))          # 多字段：先年龄后姓名
```

`operator` 模块提供了专门的 Strategy 辅助函数，比 lambda 更高效（C 实现）：

```python
import operator

# operator.attrgetter: 提取对象属性（等价于 lambda u: u.age）
sorted(users, key=operator.attrgetter("age"))

# 多属性：先 age 再 name
sorted(users, key=operator.attrgetter("age", "name"))

# operator.itemgetter: 提取字典/序列中的元素
data = [{"name": "Alice", "score": 88}, {"name": "Bob", "score": 95}]
sorted(data, key=operator.itemgetter("score"))
```

**`functools.singledispatch`——按类型分派的 Strategy 变体**

```python
from functools import singledispatch

@singledispatch
def serialize(obj):
    raise NotImplementedError(f"无法序列化: {type(obj)}")

@serialize.register(int)
def _(obj: int) -> str:
    return f"INT:{obj}"

@serialize.register(list)
def _(obj: list) -> str:
    return f"LIST:[{','.join(map(str, obj))}]"

serialize(42)        # "INT:42"
serialize([1, 2, 3]) # "LIST:[1,2,3]"
```

**`typing.Protocol`——为大型项目正式定义策略接口**

```python
from typing import Protocol, runtime_checkable

@runtime_checkable
class SortKey(Protocol):
    """定义排序策略的协议：接收任意对象，返回可比较值"""
    def __call__(self, item: object) -> object: ...

def sort_with(data: list, key: SortKey) -> list:
    return sorted(data, key=key)

# 普通函数隐式满足 Protocol，无需显式继承
sort_with(words, key=len)       # len 满足 SortKey 协议
sort_with(words, key=str.lower) # str.lower 也满足
```

### Pythonic 重写

Java 需要实例化 Comparator 对象（类或 lambda）；Python 中策略通常**就是一个普通函数**：

```python
# Java 思维：定义 Strategy 类（在 Python 中通常不必要）
class LengthStrategy:
    def key(self, text: str) -> int:
        return len(text)

strategy = LengthStrategy()
sorted(words, key=strategy.key)

# Pythonic：直接传函数，更简洁
sorted(words, key=len)
```

`operator.attrgetter` 和 `operator.itemgetter` 是 Python 标准库提供的"内置策略工厂"，应优先使用：

```python
# 不推荐：手写 lambda 提取属性
sorted(users, key=lambda u: u.age)

# 推荐：attrgetter 更清晰，性能更好
import operator
sorted(users, key=operator.attrgetter("age"))
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| Strategy 载体 | Interface + class / lambda | 普通函数（first-class function） |
| 类型约束 | `Comparator<T>` 泛型接口 | `Callable[[T], R]` 类型标注 |
| 注册策略 | `Map<String, Strategy>` 或 enum | `dict` / 模块级注册表 |
| 运行时切换 | 通过构造注入或 setter 方法 | 直接传函数参数 |
| 典型写法 | `list.sort(Comparator.comparing(...))` | `sorted(data, key=...)` |
| 正式接口定义 | `@FunctionalInterface` | `typing.Protocol`（可选） |

**核心差异在于函数是否是一等公民。**

Java 直到 1.8 才通过 lambda 让函数可以"像对象一样传递"，在此之前必须将算法包装成实现了某接口的类——这正是 Strategy 模式在 Java 生态中如此普遍的根本原因。GoF 书中的 Strategy 模式，本质上是在弥补 Java 早期"函数不是一等公民"的语言缺陷。

Python 从诞生起函数就是一等公民，因此 Python 代码中 Strategy 模式常常是"隐形"的——你已经在用它，只是没有意识到那就是 Strategy。`sorted(data, key=len)` 中，`len` 就是策略对象，`sorted` 就是 Context，只不过 Python 不需要为此定义任何类。

这并不意味着 Python 中 Strategy 类毫无价值。当策略需要**持有状态**（如带缓存的排序键）或**具备多个协作方法**时，class 仍是正确选择：

```python
class WeightedScoreKey:
    """带权重的评分策略——需要状态，class 是正确选择"""
    def __init__(self, weight_age: float, weight_name: float):
        self.weight_age = weight_age
        self.weight_name = weight_name

    def __call__(self, user: User) -> float:
        return user.age * self.weight_age + len(user.name) * self.weight_name

key = WeightedScoreKey(weight_age=0.7, weight_name=0.3)
sorted(users, key=key)
```

---

## 动手练习

### 02.1 Java: TextFormatter（匿名类 vs lambda）

创建一个 `TextFormatter`，接受不同的 `FormatStrategy` 实现：

```java
// 骨架（只写 package 声明，其余自己实现）
// 目标：
// - 定义 @FunctionalInterface FormatStrategy
// - 实现 uppercase / lowercase / title case 三种策略
// - 分别用匿名内部类和 lambda 两种方式创建策略
// - TextFormatter 在运行时可以切换策略
package ch02_Strategy;
```

参考输出：
```
[匿名类] HELLO WORLD
[lambda] hello world
[方法引用] Hello World
```

### 02.2 Python: TextFormatter（函数版 vs Protocol 版）

```python
# 第一步：用普通函数实现
def format_text(text: str, strategy) -> str:
    return strategy(text)

# 测试
print(format_text("hello world", str.upper))   # HELLO WORLD
print(format_text("HELLO WORLD", str.lower))   # hello world
# title case 需要自己实现

# 第二步：用 typing.Protocol 为策略加类型约束
# （提示：定义 FormatStrategy Protocol，让类型检查器能验证传入的函数签名）
```

### 02.3 思考题: 什么时候在 Python 中用 Strategy 类？

> 在 Python 里，什么情况下应该用 Strategy **类**而不是普通函数？

参考思路：
1. **策略有状态**：如带缓存、带配置参数的策略（见上方 `WeightedScoreKey` 示例）
2. **策略有多个协作方法**：如压缩策略既需要 `compress()` 又需要 `decompress()`
3. **策略需要生命周期管理**：如需要 `__enter__`/`__exit__` 的上下文管理器策略
4. **团队规模较大**：显式的 Protocol 和类使代码意图更清晰，利于协作和类型检查

---

## 回顾与连接

**Strategy vs Template Method（下一章）**

这两个模式解决的是同一个问题的两面：

| | Strategy | Template Method |
|---|---|---|
| 关系 | 组合（has-a） | 继承（is-a） |
| 变化点 | 整个算法可替换 | 算法骨架固定，步骤可覆盖 |
| 扩展方式 | 注入不同策略对象 | 创建子类覆盖方法 |
| 运行时切换 | 可以 | 不可以（编译时确定） |

优先选择 Strategy（组合优于继承），除非算法步骤之间有严格的执行顺序依赖，且子类只需定制其中某几步。

**相关模式**

- **Command（ch13）**：Strategy 的"请求对象化"变体——将操作封装为对象，增加了撤销/重放能力
- **Iterator（ch01）**：也是一种 Strategy——不同的集合提供不同的遍历策略，客户端只依赖统一的迭代器接口
- **State（ch14）**：结构与 Strategy 几乎相同，区别在于意图——State 侧重对象在状态间的**自动转换**，Strategy 侧重客户端**主动选择**算法

**一句话总结**

> Strategy 用组合替代条件分支，将"算法的选择"从"算法的使用"中分离，使两者可以独立演化。
