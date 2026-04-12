# Iterator 模式 — 真实应用

核心：**用统一接口遍历不同数据源，调用方不关心内部结构。**

---

## 1. Java — `java.lang.Iterable` / for-each

整个 Java Collections 框架的基础。所有实现 `Iterable<T>` 的类都能用 for-each，
编译器把 for-each 脱糖成 `iterator()` 调用。

```java
// ArrayList、LinkedList、HashSet 底层完全不同，遍历接口完全一样
List<String> list = new ArrayList<>();
Set<String>  set  = new HashSet<>();

for (String s : list) { ... }  // 同一种写法
for (String s : set)  { ... }  // 同一种写法
```

---

## 2. Python — 迭代器协议 `__iter__` / `__next__`

Python 的 for 循环、`list()`、`sum()` 等内置函数都依赖迭代器协议，
不关心对象是列表、生成器、文件还是数据库游标。

```python
# 文件对象天然是迭代器，逐行读取不把整个文件载入内存
with open("big_file.txt") as f:
    for line in f:          # f.__iter__() / f.__next__()
        process(line)

# 自定义迭代器
class Counter:
    def __init__(self, limit):
        self.limit = limit
        self.current = 0
    def __iter__(self): return self
    def __next__(self):
        if self.current >= self.limit:
            raise StopIteration
        self.current += 1
        return self.current
```

---

## 3. JavaScript — `Symbol.iterator` + Generator

ES6 引入的可迭代协议，`for...of`、展开运算符 `...`、解构赋值都依赖它。
Generator 函数是惰性迭代器的最佳实践。

```javascript
// 无限序列：Generator 惰性求值，不会撑爆内存
function* fibonacci() {
    let [a, b] = [0, 1];
    while (true) {
        yield a;
        [a, b] = [b, a + b];
    }
}

const fib = fibonacci();
console.log(fib.next().value); // 0
console.log(fib.next().value); // 1
console.log(fib.next().value); // 1

// for...of 自动调用 Symbol.iterator
for (const n of fibonacci()) {
    if (n > 100) break;
    console.log(n);
}
```

---

## 4. Rust — `Iterator` trait

Rust 的 `Iterator` trait 是语言核心，标准库所有集合都实现它。
链式调用是零成本抽象（编译期展开，运行时无额外开销）。

```rust
let numbers = vec![1, 2, 3, 4, 5];

// 链式迭代器：filter + map + collect，编译器优化成单次循环
let result: Vec<i32> = numbers
    .iter()
    .filter(|&&x| x % 2 == 0)
    .map(|&x| x * x)
    .collect();
// result = [4, 16]
```

---

## 5. Node.js — Readable Stream

Stream 是异步迭代器，处理网络请求、文件、数据库结果集，
统一接口屏蔽了"数据从哪来"的细节。

```javascript
const { Readable } = require('stream');

// HTTP 响应、文件读取、数据库游标——接口完全一样
async function processStream(readable) {
    for await (const chunk of readable) {  // 异步迭代器
        process(chunk);
    }
}

// 调用方不需要知道数据来自文件还是网络
processStream(fs.createReadStream('file.txt'));
processStream(response.body);  // fetch Response
```

---

## Python 生态

Python 的迭代协议是语言的核心特性，远比 GoF 定义更深入地融入了语言本身。

```python
# 1. 实现迭代协议：__iter__ + __next__
class BookShelf:
    def __init__(self):
        self._books = []

    def append(self, book):
        self._books.append(book)

    def __iter__(self):
        return BookShelfIterator(self._books)

class BookShelfIterator:
    def __init__(self, books):
        self._books = books
        self._idx = 0

    def __iter__(self):          # 迭代器本身也要实现 __iter__（返回 self）
        return self

    def __next__(self):
        if self._idx >= len(self._books):
            raise StopIteration  # Python 约定：用异常终止，而非返回 None
        book = self._books[self._idx]
        self._idx += 1
        return book

shelf = BookShelf()
shelf.append("Python Cookbook")
for book in shelf:               # for 循环自动调用 __iter__ / __next__
    print(book)

# 2. 用 generator 替代手写迭代器（更 Pythonic）
class BookShelfGen:
    def __init__(self):
        self._books = []

    def __iter__(self):
        yield from self._books   # yield from 直接委托给列表迭代器

# 3. itertools — 组合迭代器的标准库
import itertools

shelf1 = ["Clean Code", "Refactoring"]
shelf2 = ["DDIA", "SRE Book"]

for book in itertools.chain(shelf1, shelf2):    # 串联多个可迭代对象
    print(book)

for book in itertools.islice(shelf1, 0, 5):     # 惰性切片，不创建副本
    print(book)

it1, it2 = itertools.tee(iter(shelf1), 2)       # 复制迭代器（各自独立消费）

books = [
    {"title": "Clean Code",   "lang": "Java"},
    {"title": "Fluent Python", "lang": "Python"},
    {"title": "DDIA",         "lang": "Python"},
]
for lang, group in itertools.groupby(books, key=lambda b: b["lang"]):
    print(lang, list(group))                     # groupby 按 key 分组（输入需已排序）

# 4. 生成器表达式 vs 列表推导式（惰性 vs 立即求值）
titles_lazy  = (b["title"] for b in books)       # 生成器：惰性，节省内存
titles_eager = [b["title"] for b in books]       # 列表：立即创建全部

# 5. 无限迭代器
counter = itertools.count(start=1, step=2)       # 1, 3, 5, 7, ...
first_10_odds = list(itertools.islice(counter, 10))
```

> **Python 洞察**：Python 的 `for` 循环、列表推导式、`zip()`、`map()` 等都基于迭代协议。
> 实现 `__iter__` + `__next__` 是让自定义类"融入"Python 生态的关键一步。
> `itertools` 提供高性能的惰性迭代组合，是处理大数据流的首选工具。

---

## 关键洞察

> Iterator 不是一个"设计"，而是一个**协议**。
> 语言/框架层面统一了这个协议之后，所有数据结构都能互换——
> 你的遍历逻辑写一次，适用于数组、链表、文件、网络流、数据库游标。
