# 23 模式 × 2 语言 速查矩阵

## 按 Textbook 章节顺序

| Ch | 模式 | 类型 | Java 核心机制 | Python 核心机制 | Java 典型实现 | Python 典型实现 |
|----|------|------|--------------|----------------|--------------|----------------|
| 01 | Iterator | 行为 | `Iterator<E>` 接口 | `__iter__`/`__next__` | ArrayList.Itr | generator `yield` |
| 02 | Strategy | 行为 | `Comparator<T>` + lambda | first-class function | `list.sort(cmp)` | `sorted(key=)` |
| 03 | Template Method | 行为 | abstract class + @Override | abc.ABC + @abstractmethod | `AbstractList` | `collections.abc` mixin |
| 04 | Adapter | 结构 | implements Target, wraps Adaptee | `__getattr__` 委托 | `InputStreamReader` | `io.TextIOWrapper` |
| 05 | Singleton | 创建 | enum 单例 | module-level instance | `Runtime.getRuntime()` | `logging.root` |
| 06 | Factory Method | 创建 | sealed + 子类 override | `@classmethod` / `__new__` | `Collection.iterator()` | `pathlib.Path()` |
| 07 | Facade | 结构 | package-private + 公开入口 | `__all__` + `__init__.py` | `URL.openStream()` | `requests.get()` |
| 08 | Observer | 行为 | `PropertyChangeListener` | callback list / signals | `Flow` API | Django signals |
| 09 | Decorator | 结构 | 同接口包装 + 委托 | `@decorator` 语法 | `BufferedInputStream` | `@functools.wraps` |
| 10 | Builder | 创建 | 内嵌 static Builder | keyword args / `@dataclass` | `HttpRequest.newBuilder()` | `argparse` |
| 11 | Prototype | 创建 | record wither / copy constructor | `dataclasses.replace()` | `List.copyOf()` | `copy.deepcopy()` |
| 12 | Composite | 结构 | sealed interface 树节点 | dataclass + match | `Component/Container` | `pathlib.Path` |
| 13 | Command | 行为 | `@FunctionalInterface` + lambda | `__call__` / `partial` | `Runnable`/`Callable` | `concurrent.futures` |
| 14 | State | 行为 | enum + abstract method / switch | `enum.Enum` + match/case | `Thread.State` | `transitions` 库 |
| 15 | CoR | 行为 | 链表 / Optional 链 | middleware / MRO super() | `Logger` parent chain | Django middleware |
| 16 | Proxy | 结构 | `java.lang.reflect.Proxy` | `__getattr__` / descriptor | 动态代理 | `unittest.mock` |
| 17 | Bridge | 结构 | 接口 + `ServiceLoader` | Protocol + `importlib` | JDBC Driver/Connection | DB-API 2.0 |
| 18 | Abstract Factory | 创建 | sealed + record 家族 | ABC + `__init_subclass__` | `DocumentBuilderFactory` | Django DB backends |
| 19 | Flyweight | 结构 | wrapper cache + intern | `__slots__` + `sys.intern` | `Integer.valueOf()` | 小整数缓存 |
| 20 | Memento | 行为 | record 不可变快照 | `dataclasses.asdict()` | `Serializable` | `pickle` |
| 21 | Mediator | 行为 | `ExecutorService` / virtual threads | `asyncio.EventLoop` | `Timer`/`TimerTask` | `asyncio` |
| 22 | Visitor | 行为 | sealed + switch 替代双分派 | match/case 结构模式 | `FileVisitor` | `ast.NodeVisitor` |
| 23 | Interpreter | 行为 | sealed+record+switch 构建 AST | dataclass+match 递归求值 | `regex.Pattern` | `ast.literal_eval` |

---

## 按 GoF 分类

### 创建型 (5)

| 模式 | 解决的问题 | Java 一句话 | Python 一句话 |
|------|-----------|------------|--------------|
| Singleton | 确保唯一实例 | `enum` 单例 | module 就是单例 |
| Factory Method | 延迟到子类决定创建什么 | `sealed` + override | `@classmethod` |
| Abstract Factory | 创建相关产品族 | sealed + record 家族 | duck typing 换实现 |
| Builder | 分步构建复杂对象 | fluent API (弥补无 kwargs) | `@dataclass` + kwargs |
| Prototype | 克隆现有对象 | record wither | `replace()` / `deepcopy` |

### 结构型 (7)

| 模式 | 解决的问题 | Java 一句话 | Python 一句话 |
|------|-----------|------------|--------------|
| Adapter | 接口转换 | implements + 委托 | `__getattr__` |
| Bridge | 抽象与实现独立变化 | SPI + ServiceLoader | Protocol + importlib |
| Composite | 统一对待整体与部分 | sealed interface 树 | dataclass + match |
| Decorator | 动态添加职责 | 同接口包装链 | `@decorator` 语法 |
| Facade | 简化子系统接口 | package-private 封装 | `__init__.py` 导出 |
| Flyweight | 共享实例节省内存 | wrapper cache | `__slots__` + intern |
| Proxy | 控制访问 | 动态代理 | `__getattr__` |

### 行为型 (11)

| 模式 | 解决的问题 | Java 一句话 | Python 一句话 |
|------|-----------|------------|--------------|
| Iterator | 顺序访问聚合元素 | `Iterator<E>` | generator yield |
| Strategy | 运行时切换算法 | lambda + Comparator | first-class function |
| Template Method | 固定骨架，子类填步骤 | abstract class | abc.ABC / context manager |
| Observer | 状态变化通知 | Flow API / Listener | signals / callback |
| Command | 请求对象化 | Runnable/Callable | `__call__` / partial |
| State | 状态驱动行为 | enum + switch | Enum + match |
| CoR | 链式处理请求 | Logger chain | middleware / MRO |
| Mediator | 集中协调交互 | ExecutorService | asyncio EventLoop |
| Memento | 保存/恢复状态 | record 快照 | dict/pickle 快照 |
| Visitor | 为层次结构添加操作 | sealed + switch | match/case |
| Interpreter | 解释 DSL/语法 | sealed+record AST | dataclass+match AST |
