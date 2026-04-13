# Chapter 01: Iterator

> 类型: 行为 | 难度: ★☆☆ | 原书: `src/ch01_Iterator/` | 前置: 无

---

## 模式速览

**问题**: 你有一个集合对象（数组、链表、树、数据库游标……），想让使用者逐个访问其中的元素，但又不想暴露集合的内部结构。如果直接把内部数组或链表指针给调用方，集合就失去了封装性——换一种内部实现（比如从数组改成链表）会破坏所有调用方代码。Iterator 模式的解决方案是：把"如何遍历"这个行为单独抽象成一个对象（迭代器），集合只负责产生迭代器，调用方只和迭代器打交道，彼此互不依赖。

```
      «interface»              «interface»
      Aggregate<T>             Iterator<T>
      ┌──────────┐             ┌──────────────┐
      │iterator()│────────────▶│ hasNext(): B │
      └────┬─────┘  创建       │ next(): T    │
           │                   └──────┬───────┘
           │实现                      │实现
           ▼                          ▼
    ConcreteAggregate       ConcreteIterator
    ┌──────────────────┐    ┌──────────────────┐
    │ - books: List<T> │◀───│ - shelf          │
    │ iterator()       │    │ - idx: int       │
    └──────────────────┘    │ hasNext()        │
         BookShelf          │ next()           │
                            └──────────────────┘
                             BookShelfIterator
```

**四个角色**:
- `Aggregate<T>` — 集合抽象，只声明"能产生迭代器"
- `Iterator<T>` — 迭代器抽象，只声明 `hasNext()` 和 `next()`
- `BookShelf` (ConcreteAggregate) — 具体集合，持有数据
- `BookShelfIterator` (ConcreteIterator) — 具体迭代器，持有游标

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 局部类型推断 | `var`（Java 10+） | 天生动态，无需声明 |
| 增强 for 循环 | `for (Book b : shelf)` | `for b in shelf:` |
| 惰性序列生成器 | `Stream.generate()` / `Spliterator` | `yield` / generator expression |
| 委托子迭代器 | 无直接对应，手写嵌套循环 | `yield from` |
| 字符串插值 | `String.format()` / text block | f-string（Python 3.6+） |

### `var` — Java 局部类型推断

```java
// 旧写法：类型写两遍，冗余
BookShelfIterator it = new BookShelfIterator(shelf);

// var：编译器推断类型，代码更简洁；类型仍是静态的，非动态类型
var it = new BookShelfIterator(shelf);

// 适合泛型嵌套特别长的场景
var map = new HashMap<String, List<Book>>();  // 比 HashMap<String,List<Book>> map 简洁
```

### 增强 for 循环（Java）

```java
// 语法糖，编译器脱糖为 Iterator 调用
for (Book book : shelf) {
    System.out.println(book.name());
}

// 脱糖后等价于：
java.util.Iterator<Book> _it = shelf.iterator();
while (_it.hasNext()) {
    Book book = _it.next();
    System.out.println(book.name());
}
```

### Python f-string（Python 3.6+）

```python
book_name = "Design Patterns"
page = 42

# 旧写法
print("读到第 %d 页: %s" % (page, book_name))
print("读到第 {} 页: {}".format(page, book_name))

# f-string：表达式直接嵌在字符串里，支持任意表达式
print(f"读到第 {page} 页: {book_name}")
print(f"书名长度: {len(book_name)}, 大写: {book_name.upper()}")
```

### Python `yield` — 生成器函数

```python
def count_up(n: int):
    """每次调用 next() 时才执行到下一个 yield，天生惰性"""
    for i in range(n):
        print(f"  计算 {i}")   # 只有被消费时才执行
        yield i

gen = count_up(3)
print(next(gen))   # 打印"计算 0"，然后返回 0
print(next(gen))   # 打印"计算 1"，然后返回 1
```

### Python `yield from` — 委托子迭代器

```python
def chain_shelves(*shelves):
    """把多个书架的内容连接起来，无需手写嵌套循环"""
    for shelf in shelves:
        yield from shelf   # 等价于: for book in shelf: yield book

# 调用方看不到内部有多少个 shelf，感觉就是一个连续序列
for book in chain_shelves(shelf_a, shelf_b):
    print(book)
```

---

## Java 实战: `java.util.Iterator`

### 源码解析

JDK 中 `java.util.Iterator<E>` 接口的核心定义（简化自 OpenJDK 源码）：

```java
// java.util.Iterator<E> — JDK 源码（简化）
public interface Iterator<E> {

    // 核心方法 1：是否还有下一个元素？
    // 调用方应在 next() 之前先调用此方法
    boolean hasNext();

    // 核心方法 2：返回下一个元素，并将游标前移
    // 若已无元素，抛 NoSuchElementException（而非返回 null）
    E next();

    // 可选方法：删除 next() 最近返回的元素（default 表示有默认实现）
    // default 方法是 Java 8 引入的，允许接口提供非抽象方法，维持向后兼容
    default void remove() {
        throw new UnsupportedOperationException("remove");
        // 默认抛异常——大多数迭代器不支持删除
        // 支持的实现（如 ArrayList.Itr）会覆盖此方法
    }

    // Java 8 新增：用 Consumer 消费剩余所有元素，内部是 while 循环的语法糖
    default void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }
}
```

`java.util.ArrayList` 的内部迭代器实现（`Itr` 私有内部类，简化自 OpenJDK）：

```java
// ArrayList 的私有内部类，外部不可直接访问
private class Itr implements Iterator<E> {

    int cursor;       // 下一个要返回元素的下标；初始值 0
    int lastRet = -1; // 最近一次 next() 返回元素的下标；-1 表示尚未调用 next()
    int expectedModCount = modCount;  // 构造时快照 ArrayList 的修改次数
    // modCount 是 ArrayList 父类 AbstractList 维护的结构修改计数器

    // hasNext：只需比较游标和 size，O(1)
    public boolean hasNext() {
        return cursor != size;
    }

    @SuppressWarnings("unchecked")
    public E next() {
        checkForComodification();   // fail-fast 检查：发现并发修改立刻抛异常
        int i = cursor;
        if (i >= size)
            throw new NoSuchElementException();
        Object[] elementData = ArrayList.this.elementData;  // 访问外部类字段
        if (i >= elementData.length)
            throw new ConcurrentModificationException();
        cursor = i + 1;     // 游标前移
        return (E) elementData[lastRet = i];  // 同时记录 lastRet，供 remove() 使用
    }

    public void remove() {
        if (lastRet < 0)
            throw new IllegalStateException();  // 未调用 next() 就 remove() 是错的
        checkForComodification();
        ArrayList.this.remove(lastRet);   // 删除外部 ArrayList 对应元素
        cursor = lastRet;   // 游标退回，因为后面的元素整体前移了一位
        lastRet = -1;       // 重置，防止连续 remove() 两次
        expectedModCount = modCount;  // 同步修改计数，本次 remove() 是"合法"的
    }

    // fail-fast 核心：迭代过程中若 ArrayList 被外部修改（add/remove），立刻抛异常
    // 而非静默跳过或读到脏数据——让 bug 尽早暴露
    final void checkForComodification() {
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
    }
}
```

**`Iterable` + 增强 for 的编译器转换**:

```java
// 你写的代码（需要 BookShelf 实现 java.lang.Iterable<Book>）
for (Book book : shelf) {
    System.out.println(book.name());
}

// javac 将其脱糖为（可用 javap 或 Fernflower 反编译验证）：
Iterator<Book> it = shelf.iterator();   // 调用 Iterable.iterator()
while (it.hasNext()) {
    Book book = it.next();
    System.out.println(book.name());
}
// 脱糖发生在编译期，运行时只有 while 循环，没有额外开销
```

**本章源码中的关键设计决策**（`src/ch01_Iterator/`）:

```java
// BookShelfIterator.java — idx++ 而非 ++idx
return bookShelf.getBookAt(idx++);
// idx++ : 先取第 idx 本书，然后 idx+1 → 正确
// ++idx : 先 idx+1 再取 → 永远跳过第 0 本书

// 防御性 NoSuchElementException：
if (!hasNext()) {
    throw new java.util.NoSuchElementException("No more books at index " + idx);
}
// 不返回 null！null 会让 bug 延迟到调用方使用时才暴露，更难调试
```

### 现代重写: `Spliterator` 与 `Stream`

Java 8 引入 `Stream` 作为 `Iterator` 的现代演进，核心是**声明式 + 惰性 + 可并行**：

```java
// 传统 Iterator 写法：命令式，逐步告诉 JVM "怎么做"
Iterator<Book> it = shelf.iterator();
while (it.hasNext()) {
    Book b = it.next();
    if (b.name().startsWith("D")) {
        System.out.println(b.name().toUpperCase());
    }
}

// Stream 写法：声明式，告诉 JVM "要什么结果"
shelf.snapshot()
     .stream()                        // 获取 Stream<Book>
     .filter(b -> b.name().startsWith("D"))  // 惰性过滤，不立即执行
     .map(b -> b.name().toUpperCase())       // 惰性转换
     .forEach(System.out::println);          // 终止操作，触发整条流水线

// Spliterator — Stream 的底层迭代器，支持并行分割
// ArrayList 的 Spliterator 可以把列表分成两半，交给两个线程并行处理
shelf.snapshot().parallelStream()
     .map(Book::name)
     .forEach(System.out::println);  // 多线程并发输出（顺序不保证）
```

`Spliterator` 相比 `Iterator` 的核心改进：可以 `trySplit()` 分割自身，让 `Stream.parallel()` 得以高效实现。

---

## Python 实战: `collections.abc.Iterator` + generator

### 源码解析

Python 迭代器协议（iterator protocol）是语言核心，由两个魔法方法定义：

```python
# collections/abc.py — CPython 源码（简化）
class Iterator(Iterable):
    """
    迭代器必须实现 __next__，并且 __iter__ 返回自身
    这样迭代器本身也是可迭代的（iterable），可以直接放入 for 循环
    """
    @abstractmethod
    def __next__(self):
        """返回下一个元素；无元素时抛 StopIteration（而非返回 None）"""
        raise StopIteration

    def __iter__(self):
        """迭代器的 __iter__ 返回自身"""
        return self

    # 用于 isinstance 检查的虚拟子类注册（鸭子类型的基础设施）
    @classmethod
    def __subclasshook__(cls, C):
        if cls is Iterator:
            return _check_methods(C, '__iter__', '__next__')
        return NotImplemented
```

**`for x in obj` 的脱糖过程**:

```python
# 你写的代码
for book in shelf:
    print(book)

# Python 解释器展开为（等价逻辑，非真实字节码）：
_iter = iter(shelf)          # 调用 shelf.__iter__()，获得迭代器
while True:
    try:
        book = next(_iter)   # 调用 _iter.__next__()
        print(book)
    except StopIteration:    # __next__ 抛出 StopIteration → 循环结束
        break
```

**手写类迭代器（对应 Java 的 ConcreteIterator）**:

```python
class BookShelfIterator:
    """对应 Java 的 BookShelfIterator — 持有书架引用和游标"""

    def __init__(self, shelf: "BookShelf"):
        self._shelf = shelf
        self._idx = 0

    def __iter__(self):
        return self          # 迭代器本身可迭代

    def __next__(self) -> "Book":
        if self._idx >= len(self._shelf):
            raise StopIteration    # 协议规定：无元素时抛此异常，不返回 None
        book = self._shelf[self._idx]
        self._idx += 1
        return book


class BookShelf:
    def __init__(self):
        self._books: list[str] = []

    def append(self, name: str):
        self._books.append(name)

    def __len__(self):
        return len(self._books)

    def __getitem__(self, idx: int):
        return self._books[idx]

    def __iter__(self):
        return BookShelfIterator(self)   # 每次返回全新迭代器
```

**`itertools` — 标准库的迭代器工具箱**:

```python
import itertools

books_a = ["设计模式", "重构"]
books_b = ["代码整洁之道", "程序员修炼之道"]

# chain：将多个可迭代对象首尾连接
for book in itertools.chain(books_a, books_b):
    print(book)

# islice：惰性切片，不创建中间列表——对大文件/无限序列尤其重要
first_two = list(itertools.islice(books_a, 2))

# groupby：对连续相同 key 的元素分组（数据需提前排序）
data = [("Python", "A"), ("Python", "B"), ("Java", "C")]
for lang, group in itertools.groupby(data, key=lambda x: x[0]):
    print(f"{lang}: {list(group)}")
```

### Pythonic 重写: generator 函数

在 Python 中，创建迭代器最地道的方式是**生成器函数**——用 `yield` 关键字把普通函数变成迭代器工厂，无需手写 `__iter__`/`__next__`：

```python
def fibonacci():
    """无限 Fibonacci 数列生成器——天生惰性，不会占用无限内存"""
    a, b = 0, 1
    while True:       # 无限循环没问题，因为每次 yield 都会暂停执行
        yield a       # 暂停：返回 a，保存当前帧状态，等待下次 next()
        a, b = b, a + b

# 只取前 10 项，生成器不会提前计算剩余的无限项
import itertools
for n in itertools.islice(fibonacci(), 10):
    print(n)          # 0 1 1 2 3 5 8 13 21 34
```

**生成器表达式（generator expression）** — 列表推导式的惰性版本：

```python
books = ["设计模式", "重构", "代码整洁之道"]

# 列表推导式：立即计算，全部存入内存
names_list = [b.upper() for b in books]        # ['设计模式', '重构', '代码整洁之道']

# 生成器表达式：惰性计算，括号代替方括号
names_gen = (b.upper() for b in books)         # 还没开始计算
print(next(names_gen))                          # 这时才计算第一个

# 直接作为函数参数时可省略外层括号
total_len = sum(len(b) for b in books)          # 不产生中间列表
```

**`yield from` — 委托子生成器**:

```python
def shelf_books(shelf):
    yield from shelf   # 等价于 for book in shelf: yield book

def all_books(*shelves):
    """合并多个书架，调用方无感知内部有多少个 shelf"""
    for shelf in shelves:
        yield from shelf    # 递归委托，也常见于树形结构的深度优先遍历

# 树形结构中的 yield from（预告 ch12 Composite）
class Category:
    def __init__(self, name, children=None):
        self.name = name
        self.children = children or []

    def __iter__(self):
        yield self.name                  # 先 yield 自身
        for child in self.children:
            yield from child             # 再递归 yield 子节点（深度优先）
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 核心机制 | `Iterator<E>` interface + 泛型 | `__iter__` + `__next__` duck typing |
| 类型安全 | 编译期泛型检查，`ClassCastException` 在编译时暴露 | 运行时检查，可选 mypy 静态分析 |
| 创建方式 | 手写内部类（或匿名类、lambda） | `yield` 一行搞定生成器 |
| 惰性求值 | 需要显式使用 `Stream` API（Java 8+） | generator 天生惰性，默认行为 |
| 无限序列 | `Stream.generate()` + `limit()` | `while True: yield` 自然表达 |
| 并行迭代 | `Spliterator` + `parallelStream()` | `concurrent.futures` / `asyncio` |
| 协议执行 | 编译器强制实现接口所有方法 | 运行时 duck typing，缺方法才报错 |

**为什么 Java 需要形式化的 `Iterator` 接口？**

Java 是静态类型语言，编译器必须在编译时就知道一个对象"能不能被迭代"。没有 `Iterator` 接口，编译器无法验证 `hasNext()` 和 `next()` 的存在，泛型也无法在编译期保证 `next()` 返回的类型是 `Book` 而非 `Object`。接口 + 泛型是 Java 类型系统的基础设施，是强制约定的代价，也是编译期安全的来源。

**为什么 Python 用 duck typing 就够了？**

Python 信奉"如果它走路像鸭子、叫声像鸭子，那它就是鸭子"。只要一个对象有 `__iter__` 和 `__next__`，`for` 循环就能工作，无需声明实现了某个接口。更进一步，生成器函数让"创建迭代器"这件事从"设计类层次结构"退化为"写一个带 `yield` 的函数"。Python 的代价是：类型错误只在运行时暴露，大型项目需要 mypy 补上静态检查。

**结论**: Java 的 `Iterator` 模式在 Python 里被语言本身内化了——Python 的 `for` 循环、生成器、`itertools` 就是这个模式的"内置实现"。学习这个模式帮助你理解 Python 迭代器协议的设计动机，也帮助你在两种语言之间建立概念上的映射。

---

## 动手练习

### 01.1 Java — `Range` 类

实现一个 `Range` 类，使下面的代码能够工作：

```java
// 期望行为
var range = new Range(1, 6);       // 表示 [1, 6)
for (int n : range) {
    System.out.print(n + " ");     // 输出: 1 2 3 4 5
}

// 要求：
// 1. Range 实现 Iterable<Integer>
// 2. 内部迭代器实现 java.util.Iterator<Integer>
// 3. 不使用 IntStream，手写 hasNext() / next() 逻辑
```

提示：需要一个内部类（或私有静态类）持有游标 `current`。

### 01.2 Python — `Range` 的两种写法

**写法 A — 生成器函数**（推荐的 Pythonic 方式）:

```python
def my_range(start: int, stop: int):
    # 用 yield 实现，不使用内置 range()
    # 约 3 行代码
    ...

for n in my_range(1, 6):
    print(n, end=" ")   # 期望: 1 2 3 4 5
```

**写法 B — 类 + `__iter__` / `__next__`**（对应 Java 的结构）:

```python
class MyRange:
    def __init__(self, start: int, stop: int):
        ...

    def __iter__(self):
        ...

    def __next__(self):
        ...

# 同样的调用方式
for n in MyRange(1, 6):
    print(n, end=" ")
```

思考：写法 A 用了多少行？写法 B 用了多少行？它们的行为有什么差异（提示：试着对同一个对象 `for` 两次）。

### 01.3 跨语言思考题

1. **自然度**: 对你来说，哪种写法更直觉？Java 的接口声明，还是 Python 的 `yield`？为什么？

2. **权衡**: Java 的 `Iterator` 接口要求显式声明，增加了代码量，但带来了什么好处？Python 的 duck typing 减少了代码量，但在什么情况下会让你吃到苦头？

3. **互操作**: 如果你在 Python 中实现了一个类，但忘记写 `__iter__`，只写了 `__next__`，会发生什么？在 Java 中忘记实现接口的某个方法会发生什么？两种语言的错误发现时机有什么不同？

4. **扩展**: 假设需求变了——书架不再是一个 `ArrayList`，而是从数据库分页加载数据（每次加载 100 条）。在 Java 和 Python 中分别如何修改迭代器，使调用方代码**不需要任何改动**？这正是 Iterator 模式的核心价值所在。

---

## 回顾与连接

**本章建立的概念**:
- 将"遍历"与"集合结构"分离，是 Iterator 模式的本质
- 接口（Java）和协议（Python）都是"约定"的表达方式，差异在于约定的执行时机
- 惰性求值（Java Stream / Python generator）是 Iterator 的自然演进

**与后续章节的关联**:

| 章节 | 关联方式 |
|------|----------|
| **ch12 Composite**（树形结构）| 树的深度优先/广度优先遍历本质是特殊的 Iterator，Python 的 `yield from` 让递归遍历极为简洁 |
| **ch22 Visitor**（双分派）| Visitor 在遍历（Iterator）的基础上，对每个元素执行不同操作，两者常配合使用 |
| **ch02 Strategy**（下一章）| 和 Iterator 共享同一个思想：将变化的行为（遍历策略 / 算法策略）封装在独立对象后面，调用方不感知具体实现 |

> **核心洞察**: Iterator 模式让你用同一套代码遍历书架、数据库结果集、文件系统目录树、网络数据流——因为所有这些"集合"都可以产生一个"迭代器"，而迭代器只有两个问题需要回答：还有吗？下一个是什么？
