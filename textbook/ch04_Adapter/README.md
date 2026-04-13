# Chapter 04: Adapter

> 类型: 结构 | 难度: ★☆☆ | 原书: `src/ch02_Adapter/` | 前置: Ch03

## 模式速览

Adapter（适配器）模式的作用是把一个类的接口转换成客户端期望的另一个接口，让原本因接口不兼容而无法协作的类能够一起工作。

```
Client  →→→  <<Target>>     ←←←←←←←←←←←←←←←←
             interface           Adapter
             + request()    →→→  + request()
                                     ↓ 委托
                                 Adaptee
                                 + specificRequest()
```

**两种变体**:

| 变体 | 机制 | 适用场景 |
|------|------|----------|
| Class Adapter | 继承 Adaptee，实现 Target | 只需适配单一 Adaptee，且可以覆盖其行为 |
| Object Adapter | 组合持有 Adaptee 引用 | 需要适配多个 Adaptee，或 Adaptee 是 final 类 |

---

## 本章新语言特性

### Java: `interface` `default` 方法 (Java 8+)

Java 8 引入了接口的 `default` 方法，允许在不破坏现有实现类的前提下向接口添加新方法。这本身就是一种"在接口层面内置的适配能力"，减少了许多需要手写 Adapter 的场景。

```java
// java.lang.Iterable 上的 default 方法示例
public interface Iterable<T> {
    Iterator<T> iterator(); // 抽象方法，实现类必须提供

    // default 方法：所有实现类自动获得，无需修改已有代码
    default void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        for (T t : this) {
            action.accept(t); // 内部仍然依赖 iterator()
        }
    }
}
```

没有 `default` 之前，若要给 `Iterable` 加 `forEach`，唯一选择是写一个 Adapter 包装类。

---

### Python: 上下文管理器协议 (`__enter__` / `__exit__`)

Python 的 `with` 语句要求对象实现 `__enter__` 和 `__exit__` 两个方法。如果一个对象有 `.close()` 但没有这两个方法，可以用 `contextlib.closing` 将其适配为上下文管理器——这是标准库提供的现成 Adapter。

```python
import urllib.request
from contextlib import closing

# urllib.request.urlopen 返回的对象有 .close()，但不是真正的上下文管理器
# closing() 扮演 Adapter 的角色，添加 __enter__ / __exit__
with closing(urllib.request.urlopen('http://example.com')) as page:
    content = page.read()
# 离开 with 块后，closing 自动调用 page.close()
```

`contextlib.closing` 的实现极简：

```python
class closing:
    def __init__(self, thing):
        self.thing = thing          # 持有 Adaptee 引用（Object Adapter）

    def __enter__(self):
        return self.thing

    def __exit__(self, *exc_info):
        self.thing.close()          # 将协议调用委托给 Adaptee 的 close()
```

---

## Java 实战: `Arrays.asList()` + `InputStreamReader`

### 源码解析

#### 1. `java.util.Arrays.asList(T... a)` — 数组 → List

```java
// JDK 源码（简化版）
public class Arrays {
    // 将数组适配为 List 接口（Object Adapter）
    public static <T> List<T> asList(T... a) {
        return new ArrayList<>(a); // 注意：这是 Arrays 的私有内部类，不是 java.util.ArrayList
    }

    // 私有内部类：Adapter 本体
    // Adaptee = 底层数组 a[]
    // Target  = java.util.List<E>
    private static class ArrayList<E> extends AbstractList<E>
            implements RandomAccess, java.io.Serializable {

        private final E[] a; // 直接持有原始数组引用，不复制

        ArrayList(E[] array) {
            a = Objects.requireNonNull(array);
        }

        @Override
        public int size() { return a.length; }

        @Override
        public E get(int index) { return a[index]; } // 委托给数组

        @Override
        public E set(int index, E element) {
            E oldValue = a[index];
            a[index] = element; // 修改会反映到原始数组
            return oldValue;
        }
        // add() / remove() 未实现 → 会抛 UnsupportedOperationException
        // 这是 fixed-size 语义：List 视图，不支持结构变更
    }
}
```

**关键点**：`Arrays.asList` 是典型的 Object Adapter。它让数组"伪装"成 `List`，使数组能被所有接受 `Collection` 的 API 使用，而无需复制数据。

---

#### 2. `java.io.InputStreamReader` — 字节流 → 字符流

```java
// 构造：将字节流 + 字符集 适配为字符流
InputStream raw = System.in;                        // Adaptee: 字节流
Reader reader = new InputStreamReader(raw, "UTF-8"); // Target:  字符流

// JDK 源码核心逻辑（简化）
public class InputStreamReader extends Reader {
    private final StreamDecoder sd; // 委托给内部解码器

    // Adapter 构造器：绑定 Adaptee（InputStream）与编码
    public InputStreamReader(InputStream in, String charsetName) {
        super(in);
        sd = StreamDecoder.forInputStreamReader(in, this, charsetName);
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        return sd.read(cbuf, offset, length); // 委托：字节解码为字符
    }
}
```

`InputStreamReader` 是 Java I/O 体系中最经典的 Adapter：字节世界（`InputStream`）与字符世界（`Reader`）之间的桥梁。`new BufferedReader(new InputStreamReader(System.in))` 这一惯用写法，正是 Adapter + Decorator 的组合使用。

---

#### 3. `java.util.Collections.enumeration(Collection<T>)` — 现代 → 遗留

```java
// 将现代 Iterator 适配为遗留的 Enumeration（向后兼容）
List<String> list = List.of("a", "b", "c");
Enumeration<String> legacy = Collections.enumeration(list); // Adapter

// JDK 源码
public static <T> Enumeration<T> enumeration(final Collection<T> c) {
    return new Enumeration<>() {
        private final Iterator<T> i = c.iterator(); // 持有 Iterator（Adaptee）

        public boolean hasMoreElements() { return i.hasNext(); }  // 接口转换
        public T nextElement()           { return i.next();    }  // 接口转换
    };
}
```

这是一个反向适配的例子：将新 API（`Iterator`）包装成老 API（`Enumeration`），以兼容只认识 `Enumeration` 的遗留代码（如旧版 `Vector`、`Hashtable`）。

---

### 现代重写：`default` 方法降低对 Adapter 的需求

```java
// Java 8 之前：若要给所有 Collection 加 removeIf，必须写 Adapter 包装类
class RemoveIfAdapter<E> implements Collection<E> {
    private final Collection<E> delegate;
    // ... 需要实现 Collection 的全部方法，仅为了加一个 removeIf
}

// Java 8 之后：直接在 Collection 接口加 default 方法，所有实现类自动继承
public interface Collection<E> extends Iterable<E> {
    default boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        boolean removed = false;
        final Iterator<E> each = iterator();
        while (each.hasNext()) {
            if (filter.test(each.next())) {
                each.remove();
                removed = true;
            }
        }
        return removed;
    }
}
// ArrayList、LinkedList、HashSet... 全部无需改动，直接获得 removeIf 能力
```

`default` 方法是 Java 对"接口演进"问题的解法——当你控制 Target 接口时，可以通过 `default` 方法原地扩展，而不必为每个旧实现类写 Adapter。

---

## Python 实战: `io.TextIOWrapper` + `csv.DictReader`

### 源码解析

#### 1. `io.TextIOWrapper` — 二进制流 → 文本流

```python
import io

# open() 在底层实际上返回的是 TextIOWrapper（Adapter）
f = open('data.txt', 'r', encoding='utf-8')
print(type(f))  # <class '_io.TextIOWrapper'>

# 手动构造：同 InputStreamReader 的 Python 版
binary_stream = open('data.txt', 'rb')           # Adaptee: 二进制流
text_stream   = io.TextIOWrapper(binary_stream,  # Adapter: 文本流
                                 encoding='utf-8')

# TextIOWrapper 的核心职责：
#   - 持有一个 BufferedIOBase（二进制 Adaptee）
#   - 将 read() / readline() 等调用透明地做编解码转换
#   - 暴露 TextIOBase 接口（Target）

line = text_stream.readline()  # 内部：从 binary_stream 读字节，解码为 str
```

`io.TextIOWrapper` 与 Java 的 `InputStreamReader` 几乎是一一对应的 Adapter。

---

#### 2. `csv.DictReader` — 列表行 → 字典行

```python
import csv, io

raw_csv = "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai\n"

# csv.reader（Adaptee）：每行返回 list[str]
reader = csv.reader(io.StringIO(raw_csv))
for row in reader:
    print(row)  # ['name', 'age', 'city'], ['Alice', '30', 'Beijing'], ...

# csv.DictReader（Adapter）：每行返回 dict，以第一行表头为键
dict_reader = csv.DictReader(io.StringIO(raw_csv))
for row in dict_reader:
    print(row)  # {'name': 'Alice', 'age': '30', 'city': 'Beijing'}
    print(row['name'])  # 直接用列名访问，无需记住列索引

# DictReader 内部持有 csv.reader 实例（Object Adapter），
# 每次 __next__ 时从底层 reader 取一行 list，再 zip(fieldnames, row) 转为 dict
```

`csv.DictReader` 是纯粹的 Object Adapter：目标接口是"按列名访问"，被适配者是"按索引访问"，Adapter 完成了从位置语义到命名语义的转换。

---

#### 3. `contextlib.closing` — 任意可关闭对象 → 上下文管理器

```python
from contextlib import closing
import urllib.request

# 场景：urllib 返回的对象有 .close()，但早期版本不支持 with 语句
# closing() 作为 Adapter，将 .close() 适配为上下文管理器协议

with closing(urllib.request.urlopen('http://httpbin.org/get')) as response:
    data = response.read()
    print(data[:100])
# __exit__ 触发时，closing 调用 response.close()

# 同样适用于任何有 .close() 的对象：数据库连接、socket、自定义资源
class LegacyResource:
    def close(self):
        print("资源已释放")
    def fetch(self):
        return "数据"

with closing(LegacyResource()) as res:
    print(res.fetch())  # 使用资源
# 自动调用 res.close()，输出"资源已释放"
```

---

### Pythonic 重写：鸭子类型 + `__getattr__` 透明委托

Python 的鸭子类型（duck typing）天然减少了对 Adapter 的需求——只要两个对象有相同的方法名，它们就可以互换使用，无需显式接口声明。但当 API 确实不同时，`__getattr__` 可以创建几乎透明的 Adapter：

```python
# 透明 Adapter：将 Adaptee 的所有属性/方法转发，只覆盖需要适配的部分
class Adapter:
    def __init__(self, adaptee, **adapted_methods):
        self._adaptee = adaptee
        # 允许在构造时注入适配映射（方法名替换）
        self.__dict__.update(adapted_methods)

    def __getattr__(self, name):
        # 所有未在本类定义的属性访问，透明转发给 Adaptee
        return getattr(self._adaptee, name)

# 使用示例：将 LegacyAPI 适配为新接口
class LegacyAPI:
    def old_fetch_data(self):
        return {"result": [1, 2, 3]}

class ModernClient:
    def process(self, source):
        data = source.fetch()  # 期望 .fetch() 方法
        return data["result"]

legacy = LegacyAPI()
# 用 Adapter 桥接：将 fetch 映射到 old_fetch_data
adapted = Adapter(legacy, fetch=legacy.old_fetch_data)
client = ModernClient()
print(client.process(adapted))  # [1, 2, 3]，ModernClient 无需修改
```

```python
# 对比：如果两个类方法名恰好相同，Python 中根本不需要 Adapter
class FileSource:
    def read(self): return "来自文件的数据"

class NetworkSource:
    def read(self): return "来自网络的数据"

def process(source):
    return source.read()  # 只要有 .read()，不管是什么类型

# 两者均可直接使用，无需任何适配代码
process(FileSource())    # ✓
process(NetworkSource()) # ✓
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 何时需要 Adapter | 接口不匹配时（编译器强制） | API 不匹配时（运行时发现） |
| Class Adapter | `extends Adaptee implements Target` | 多继承: `class Adapter(Target, Adaptee)` |
| Object Adapter | 组合: 持有 Adaptee 引用 | 组合 + `__getattr__` 透明委托 |
| 减少 Adapter 需求 | `default` 方法 (Java 8+) | 鸭子类型天然兼容 |
| 类型安全 | 编译期检查接口兼容性 | 运行时 `AttributeError` |
| 典型标准库示例 | `InputStreamReader`、`Arrays.asList` | `io.TextIOWrapper`、`contextlib.closing` |

Python 的鸭子类型意味着你需要写 Adapter 的频率远低于 Java——如果两个类碰巧有相同的方法名，它们就能直接配合工作，不需要任何桥接代码。Java 严格的接口体系则意味着，只要接口不完全匹配，你就**必须**显式写一个 Adapter，编译器不会放行。

这并不是说哪种方式更优。Java 的强制接口检查让错误在编译期暴露，重构大型代码库时更安全；Python 的灵活性让小型适配更轻量，但 `AttributeError` 只在运行时才出现。

---

## 动手练习

**04.1 Java** — 写一个 Adapter，使 `Map<String, String>` 能当作 `Properties` 对象使用（遗留 API 兼容）。`Properties` 继承自 `Hashtable`，而 `Map` 是现代接口，它们的 `getProperty` / `get` 语义有微妙差异，注意处理。

**04.2 Python** — 写一个 Adapter，使 `requests.Response` 对象表现得像一个文件对象（具备 `.read()`、`.readline()`、`.readlines()` 方法）。这样就能把 HTTP 响应直接传给任何接受 file-like object 的函数（如 `csv.reader`）。

**04.3 跨语言思考** — Adapter 什么时候是代码异味，什么时候是合理模式？

> **参考答案**：如果你同时控制 Target 接口和 Adaptee 实现，应该直接修改其中一个使其兼容，而不是写 Adapter——Adapter 是为了整合**你无法修改的**外部代码而存在的。若两端代码都在你的掌控之内，Adapter 往往意味着前期设计不足。

---

## 回顾与连接

- **Adapter vs Decorator (Ch09)**：两者都"包装"单个对象，结构几乎相同（都持有被包装对象的引用）。区别在于**意图**：Adapter 改变接口（让不兼容的接口可以协作），Decorator 保持接口不变但增强行为。
- **Adapter vs Facade (Ch07)**：Facade 包装的是**整个子系统**（多个类），对外提供一个简化的统一入口；Adapter 只包装**单个对象**，目的是接口转换而非简化。
- **Adapter vs Bridge (Ch17)**：Bridge 是在设计之初就将抽象与实现分离，两者并行演化；Adapter 是事后补救，专门修复已有接口之间的不匹配。用一句话区分：Bridge 是预防针，Adapter 是创可贴。
