# Chapter 09: Decorator

> 类型: 结构 | 难度: ★★☆ | 原书: `src/ch12_Decorator/` | 前置: Ch04 (Adapter)

---

## 模式速览

**问题**: 你想给一个对象动态添加新行为，但又不想修改它的类，也不想为每种组合都创建子类（那样子类会爆炸式增长）。比如一个文本框，你想让它能加边框、加滚动条、加颜色——三种特性任意组合就有 8 种子类，完全不可维护。Decorator 模式的解决方案是：把"附加行为"封装成一个包装对象，它和被包装对象实现同一个接口，调用方无法区分原始对象和包装对象。

```
      «abstract»
      Component
      ┌───────────────┐
      │ display()     │
      └───────┬───────┘
              │
      ┌───────┴──────────────────────────┐
      │                                  │
ConcreteComponent               «abstract» Decorator
┌─────────────────┐             ┌──────────────────────┐
│ StringDisplay   │             │ - component: Component│
│ display()       │             │ display()             │
└─────────────────┘             └──────────┬────────────┘
                                           │
                          ┌────────────────┴────────────────┐
                          │                                  │
                   SideBorder                         FullBorder
                   ┌─────────────────┐         ┌─────────────────┐
                   │ - borderChar    │         │ display()       │
                   │ display()       │         └─────────────────┘
                   └─────────────────┘
```

**四个角色**:
- `Component` — 抽象组件，定义被装饰对象和装饰器共同遵守的接口
- `ConcreteComponent` — 具体组件，真正的核心实现（被装饰的对象）
- `Decorator` — 抽象装饰器，持有一个 `Component` 引用，自身也实现 `Component`
- `ConcreteDecorator` — 具体装饰器，在委托给内部组件的前后添加新行为

**核心洞察**: Decorator 和 Component 共享同一接口，因此装饰器可以无限叠加——装饰器可以再套装饰器，调用方始终面对同一套接口，完全不感知层数。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 资源自动关闭 | `try-with-resources` + `AutoCloseable` | `with` 语句 + `contextmanager` |
| 装饰器语法糖 | 无（需要手动包装） | `@decorator` 原生语法 |
| 元数据保留 | 不涉及（类方法天然有名称） | `functools.wraps` |
| 类型安全装饰器 | 泛型接口 | `ParamSpec` + `TypeVar`（Python 3.10+） |
| 惰性求值缓存 | 无直接对应 | `functools.lru_cache` / `functools.cache` |

### `try-with-resources` — Java 资源自动关闭

`try-with-resources` 是 Java 7 引入的语法，专门解决 I/O Decorator 链的资源泄漏问题。只要对象实现 `AutoCloseable` 接口，语句块结束时 JVM 自动按**逆序**调用 `close()`。

```java
// 旧写法：手动关闭，容易遗忘或遗漏异常路径
InputStream raw = new FileInputStream("data.bin");
InputStream buffered = new BufferedInputStream(raw);
DataInputStream data = new DataInputStream(buffered);
try {
    // 使用 data ...
} finally {
    data.close();   // 若这里抛异常，raw 可能永远不关
}

// try-with-resources：声明在括号内的资源自动关闭，逆序执行
try (var raw    = new FileInputStream("data.bin");
     var buf    = new BufferedInputStream(raw);
     var data   = new DataInputStream(buf)) {
    // 使用 data ...
}   // 离开块时：data.close() → buf.close() → raw.close()，全部自动执行
```

多资源形式：用分号隔开，关闭顺序与声明顺序相反（后声明的先关闭），符合栈的 LIFO 语义，和 Decorator 链的嵌套结构天然吻合。

### Python `functools.wraps` — 保留函数元数据

Python 的 `@decorator` 语法会用包装函数替换原函数，如果不加处理，`__name__`、`__doc__` 等元数据会丢失。`functools.wraps` 解决这个问题。

```python
import functools

def log(func):
    # 没有 @functools.wraps 时，wrapper.__name__ == "wrapper"
    @functools.wraps(func)   # 把 func 的元数据复制到 wrapper 上
    def wrapper(*args, **kwargs):
        print(f"调用 {func.__name__}")
        result = func(*args, **kwargs)
        print(f"{func.__name__} 返回 {result}")
        return result
    return wrapper

@log
def add(a: int, b: int) -> int:
    """两数相加"""
    return a + b

print(add.__name__)   # 输出: add（而非 wrapper）
print(add.__doc__)    # 输出: 两数相加
```

### Python `ParamSpec` + `TypeVar` — 类型安全的装饰器（Python 3.10+）

```python
from typing import Callable, ParamSpec, TypeVar

P = ParamSpec('P')   # 捕获原函数的参数签名（位置参数 + 关键字参数）
R = TypeVar('R')     # 捕获原函数的返回类型

def log(func: Callable[P, R]) -> Callable[P, R]:
    """类型安全的日志装饰器：输入输出类型与原函数完全一致"""
    @functools.wraps(func)
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        print(f"调用 {func.__name__}")
        return func(*args, **kwargs)
    return wrapper

# IDE 和 mypy 现在能正确推断 add(1, 2) 的参数类型和返回类型
@log
def add(a: int, b: int) -> int:
    return a + b
```

---

## Java 实战: `java.io` 流体系

### 源码解析

JDK 的 `java.io` 包是教科书级别的 Decorator 模式应用，也是 GoF 书中列举的经典案例。整个层次结构完美对应模式的四个角色：

| 模式角色 | `InputStream` 体系 | `OutputStream` 体系 |
|---------|-------------------|---------------------|
| Component | `InputStream` | `OutputStream` |
| ConcreteComponent | `FileInputStream`, `ByteArrayInputStream` | `FileOutputStream`, `ByteArrayOutputStream` |
| Decorator (抽象) | `FilterInputStream` | `FilterOutputStream` |
| ConcreteDecorator | `BufferedInputStream`, `DataInputStream` | `BufferedOutputStream`, `DataOutputStream` |

`FilterInputStream` 是关键——它持有一个 `InputStream` 引用，并把所有方法委托给它：

```java
// FilterInputStream — JDK 源码（简化），这是抽象装饰器
public class FilterInputStream extends InputStream {

    // 持有被包装的组件，构造时注入
    protected volatile InputStream in;

    protected FilterInputStream(InputStream in) {
        this.in = in;
    }

    // 默认实现：原样委托给内部流，子类选择性地覆盖并增强
    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        in.close();   // 关闭时自动传播到内部流
    }
}
```

实际使用中，三层 Decorator 叠加只需一行构造代码：

```java
// 从磁盘文件读取二进制数据，带缓冲，能解析 Java 基本类型
try (var data = new DataInputStream(
                    new BufferedInputStream(
                        new FileInputStream("scores.dat")))) {

    // DataInputStream 的 readInt() 内部调用 BufferedInputStream 的 read()
    // BufferedInputStream 再按需调用 FileInputStream 的 read()，减少系统调用
    int score = data.readInt();
    String name = data.readUTF();
    System.out.println(name + ": " + score);
}
// try-with-resources 保证三层流都被关闭
```

### `Collections` 的保护装饰器

`java.util.Collections` 提供了两个经典的保护型和同步型 Decorator，它们包装 `List`/`Map`/`Set` 而不改变接口：

```java
// 原始可变列表
var mutable = new ArrayList<String>();
mutable.add("Alice");
mutable.add("Bob");

// 保护装饰器：让外部只读，防止意外修改
List<String> readOnly = Collections.unmodifiableList(mutable);
// readOnly.add("Charlie");  // 运行时抛 UnsupportedOperationException

// 同步装饰器：让非线程安全集合变成线程安全（已有 ConcurrentHashMap 时较少用）
List<String> threadSafe = Collections.synchronizedList(mutable);
```

### 现代写法：工厂方法隐藏装饰层

Java 9+ 引入的工厂方法和 NIO 的 `Files` 工具类把常见的 Decorator 叠加封装成单行调用：

```java
// Java 1.x 时代：手动叠加三层装饰器
BufferedReader oldStyle = new BufferedReader(
    new InputStreamReader(
        new FileInputStream("readme.txt"), StandardCharsets.UTF_8));

// Java 11+ 现代写法：Files.newBufferedReader 内部帮你叠好装饰层
try (var reader = Files.newBufferedReader(Path.of("readme.txt"))) {
    reader.lines().forEach(System.out::println);
}

// Java 9+ InputStream.transferTo()：不再需要手动缓冲拷贝
try (var in  = new FileInputStream("src.txt");
     var out = new FileOutputStream("dst.txt")) {
    in.transferTo(out);   // 内部自动处理缓冲，省去 BufferedInputStream 包装
}
```

---

## Python 实战: `@` 装饰器语法与 `functools`

### 源码解析

Python 的 `@decorator` 语法是语言层面对 Decorator 模式的直接支持。`@decorator` 在语义上等价于 `func = decorator(func)`——用包装后的函数替换原函数引用。

```python
import functools
import time

# ---------- 计时装饰器 ----------
def timer(func):
    """测量函数执行时间，不改变原函数签名"""
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()          # 记录开始时间
        result = func(*args, **kwargs)       # 调用原函数
        elapsed = time.perf_counter() - start
        print(f"{func.__name__} 耗时 {elapsed:.4f}s")
        return result
    return wrapper

# ---------- 日志装饰器 ----------
def log(func):
    """记录函数调用参数和返回值"""
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        args_repr = [repr(a) for a in args]
        kwargs_repr = [f"{k}={v!r}" for k, v in kwargs.items()]
        signature = ", ".join(args_repr + kwargs_repr)
        print(f"调用 {func.__name__}({signature})")
        result = func(*args, **kwargs)
        print(f"{func.__name__} 返回 {result!r}")
        return result
    return wrapper

# 叠加装饰器：先执行 log（外层），再执行 timer（内层），最后执行原函数
# 等价于: compute = log(timer(compute))
@log
@timer
def compute(n: int) -> int:
    """计算前 n 个自然数之和"""
    return sum(range(n))

compute(1000)
# 输出:
# 调用 compute(1000)
# compute 耗时 0.0001s
# compute 返回 499500
```

### `functools.lru_cache` — 内置的备忘录装饰器

```python
import functools

# lru_cache 是最常用的内置装饰器之一，用有界 LRU 缓存包装函数
@functools.lru_cache(maxsize=128)
def fibonacci(n: int) -> int:
    """无缓存时指数级递归，加上缓存后降为线性"""
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

# Python 3.9+ 简写：functools.cache 等价于 lru_cache(maxsize=None)（无界缓存）
@functools.cache
def factorial(n: int) -> int:
    return 1 if n == 0 else n * factorial(n - 1)
```

### `contextlib.contextmanager` — 把生成器变成上下文管理器的装饰器

```python
from contextlib import contextmanager

@contextmanager
def managed_resource(name: str):
    """
    用 @contextmanager 把生成器函数变成 with 语句可用的上下文管理器。
    yield 之前的代码是 __enter__，yield 之后的是 __exit__。
    """
    print(f"获取资源: {name}")
    resource = {"name": name, "open": True}   # 模拟资源
    try:
        yield resource                         # 把资源交给 with 块使用
    finally:
        resource["open"] = False
        print(f"释放资源: {name}")            # 无论是否异常，都会执行

# 使用时和内置 open() 没有区别
with managed_resource("数据库连接") as res:
    print(f"使用中: {res['name']}")
```

### 带参数的装饰器工厂

当装饰器本身需要配置参数时，需要再包一层函数——这是 Decorator 模式的"参数化"变体：

```python
from typing import Callable, ParamSpec, TypeVar

P = ParamSpec('P')
R = TypeVar('R')

def retry(times: int = 3, exceptions: tuple = (Exception,)) -> Callable[[Callable[P, R]], Callable[P, R]]:
    """
    带参数的装饰器工厂：@retry(times=5) 而不是 @retry
    返回一个真正的装饰器，该装饰器在失败时最多重试 times 次
    """
    def decorator(func: Callable[P, R]) -> Callable[P, R]:
        @functools.wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            last_error: Exception | None = None
            for attempt in range(1, times + 1):
                try:
                    return func(*args, **kwargs)
                except exceptions as e:
                    last_error = e
                    print(f"第 {attempt}/{times} 次尝试失败: {e}")
            raise last_error   # 所有重试耗尽，重新抛出最后一个异常
        return wrapper
    return decorator

# 使用：@retry(times=3, exceptions=(IOError, TimeoutError))
@retry(times=3, exceptions=(IOError,))
def fetch_data(url: str) -> str:
    """模拟可能失败的网络请求"""
    import random
    if random.random() < 0.7:
        raise IOError("网络超时")
    return f"来自 {url} 的数据"
```

### 类装饰器：装饰整个类

Python 的 `@decorator` 不仅适用于函数，也适用于类：

```python
def singleton(cls):
    """
    类装饰器：把任意类变成单例——
    第一次调用时创建实例并缓存，后续调用返回同一实例
    """
    instances: dict = {}

    @functools.wraps(cls)
    def get_instance(*args, **kwargs):
        if cls not in instances:
            instances[cls] = cls(*args, **kwargs)
        return instances[cls]

    return get_instance

@singleton
class DatabaseConnection:
    def __init__(self, url: str):
        self.url = url
        print(f"建立连接: {url}")

db1 = DatabaseConnection("postgres://localhost/mydb")  # 打印"建立连接"
db2 = DatabaseConnection("postgres://localhost/mydb")  # 不打印，返回同一实例
assert db1 is db2   # True
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 装饰机制 | 手动类包装，构造函数注入 | `@decorator` 原生语法糖 |
| 接口约束 | 必须实现同一接口/继承同一父类 | 鸭子类型，只需签名兼容 |
| 叠加方式 | 构造函数嵌套 `new A(new B(new C(...)))` | `@A @B @C` 注解堆叠 |
| 类型检查 | 编译期静态类型检查 | `ParamSpec`+`TypeVar` 提供可选类型注解 |
| 典型场景 | I/O 流装饰、集合保护 | 日志、重试、缓存、权限校验 |
| 资源管理 | `AutoCloseable` + `try-with-resources` | `contextmanager` + `with` 语句 |
| 叠加顺序语义 | 最外层装饰器最先执行 | 最接近函数的 `@` 最先应用（执行顺序由外向内） |

**叠加顺序示例对比**：

```java
// Java：阅读顺序从内到外
new DataInputStream(          // 最后执行（最外层）
    new BufferedInputStream(  // 中间执行
        new FileInputStream("data.bin")));  // 最先执行（最内层）
```

```python
# Python：装饰器从下往上应用（离函数最近的最先包装）
@log      # 最后应用，最外层，调用时最先执行
@timer    # 中间
@cache    # 最先应用，最内层，调用时最后执行
def compute(n: int) -> int: ...
# 等价于: compute = log(timer(cache(compute)))
```

---

## 动手练习

**09.1 Java** — 为 `InputStream` 创建两个自定义装饰器：
- `LoggingInputStream`：每次 `read()` 时打印读取的字节数
- `CountingInputStream`：统计总读取字节数，通过 `getCount()` 查询
- 叠加使用：`new CountingInputStream(new LoggingInputStream(new FileInputStream(...)))`

**09.2 Python** — 实现以下三个装饰器，全部使用 `functools.wraps` 保留元数据：
- `@log`：打印调用参数和返回值
- `@retry(times=3)`：失败时自动重试（带参数的装饰器工厂）
- 将两者叠加应用于一个模拟的不稳定函数，观察执行顺序

**09.3 思考题** — Decorator vs 继承：
- 如果一个文本框需要同时支持"滚动条"和"边框"两种特性，用继承需要几个子类？用 Decorator 需要几个类？
- 什么情况下继承更合适，什么情况下 Decorator 更合适？提示：考虑"组合爆炸"和"运行时动态添加行为"两个维度。

---

## 回顾与连接

**与相关模式的区别**:

- **Decorator vs Composite (Ch12)**: Decorator 给**单个对象**添加行为（链状结构）；Composite 把多个对象**组合成树**（树状结构）。两者都递归持有同类型引用，但意图截然不同。

- **Decorator vs Adapter (Ch04)**: Adapter **改变**接口（把 A 接口适配成 B 接口）；Decorator **保持**接口不变，只添加行为。如果你发现装饰前后接口不同，你写的很可能是 Adapter。

- **Decorator vs Proxy (Ch16)**: 两者结构几乎相同（都持有被包装对象的引用）。区别在于**意图**：Proxy 控制**访问**（认证、懒加载、缓存代理）；Decorator 添加**功能**（日志、计时、重试）。经验法则：Proxy 通常在工厂或框架层创建，调用方不知道它在访问代理；Decorator 通常由调用方显式叠加。

- **Decorator vs Chain of Responsibility (Ch15)**: 责任链也是链式结构，每个节点可以处理或传递请求。区别在于责任链中每个节点可以**决定是否继续传递**，适合请求路由；Decorator 总是**透明地传递**并在前后添加行为，适合功能增强。

**设计要点**:

1. **Component 接口尽量精简**：Decorator 必须实现 Component 的全部方法，方法越多实现成本越高。
2. **避免重量级基类**：如果 Component 是抽象类而非接口，Decorator 就失去了继承其他类的机会。
3. **注意叠加顺序**：在 Java 中，最外层的装饰器先执行；在 Python 中，靠近函数的 `@` 先被应用（执行时由外向内）。搞混顺序是常见 bug。
4. **`functools.wraps` 不是可选的**：Python 中省略它会导致调试困难——所有装饰后的函数都叫 `wrapper`，堆栈跟踪无法辨别。
