# Chapter 05: Singleton

> 类型: 创建 | 难度: ★☆☆ | 原书: `src/ch05_Singleton/` | 前置: Ch04

---

## 模式速览

**Singleton** 保证一个类只有一个实例，并提供一个全局访问点。

**何时使用：**
- 共享资源（数据库连接池、配置对象、日志器）
- 多个实例会引发语义错误（如两个 Logger 写同一个文件产生竞争）

**何时不使用（更常见）：**
- 大多数情况下！Singleton 是被过度使用的模式，往往是 anti-pattern
- 单元测试中全局状态会造成测试间相互干扰
- 隐式全局依赖使代码难以推理和重构

**结构图：**

```
┌─────────────────────────────────────┐
│             Singleton               │
├─────────────────────────────────────┤
│ - instance: Singleton  (static)     │
│ - Singleton()          (private)    │
├─────────────────────────────────────┤
│ + getInstance(): Singleton (static) │
└─────────────────────────────────────┘
         │
         │ returns the same object every time
         ▼
    [ single instance ]
```

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 本章重点 | `enum` 类型基础 | `__new__` 方法 |
| 作用 | enum 是 Java 最佳单例实现 | 控制实例创建，在 `__init__` 之前调用 |

### Java: `enum` 基础

Java 的 `enum` 不只是常量集合——每个枚举值本质上是该 enum 类的唯一实例，由 JVM 保证只创建一次。

```java
// 最简单的 enum
public enum Direction {
    NORTH, SOUTH, EAST, WEST
}

// 带字段和方法的 enum
public enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    EARTH(5.976e+24, 6.37814e6);

    private final double mass;   // 质量（千克）
    private final double radius; // 半径（米）

    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
    }

    // 表面重力
    double surfaceGravity() {
        final double G = 6.67300E-11;
        return G * mass / (radius * radius);
    }
}
```

枚举的构造函数隐式私有，外部无法调用 `new Planet(...)`，天然满足 Singleton 的核心约束。

### Python: `__new__` 方法

`__new__` 是对象的"出生"钩子，在 `__init__`（"成长"钩子）之前调用，负责分配并返回实例。

```python
class MyClass:
    def __new__(cls, *args, **kwargs):
        print(f"__new__ 被调用，准备创建 {cls.__name__} 的实例")
        instance = super().__new__(cls)  # 委托给 object.__new__
        return instance

    def __init__(self, value):
        print(f"__init__ 被调用，初始化 value={value}")
        self.value = value

obj = MyClass(42)
# 输出:
# __new__ 被调用，准备创建 MyClass 的实例
# __init__ 被调用，初始化 value=42
```

控制 `__new__` 的返回值就能控制"是否真的创建新对象"，Singleton 由此实现。

---

## Java 实战: `java.lang.Runtime` + enum Singleton

### 源码解析

`java.lang.Runtime` 是 JDK 中最经典的 Singleton——每个 Java 程序只需要一个 Runtime 对象来与宿主 JVM 交互。

```java
// java.lang.Runtime 源码（简化）
public class Runtime {
    // 饿汉式：类加载时立即初始化，线程安全
    private static final Runtime currentRuntime = new Runtime();

    // 私有构造函数：外部无法 new Runtime()
    private Runtime() {}

    // 全局访问点
    public static Runtime getRuntime() {
        return currentRuntime;
    }

    // 实际功能
    public long totalMemory() { ... }
    public long freeMemory()  { ... }
    public int  availableProcessors() { ... }
}
```

线程安全的原因：`static final` 字段在类加载阶段由 JVM 初始化，JVM 的类加载机制本身就保证了线程安全，无需额外同步。

### Java 单例的五种实现（从差到好）

**方式一：懒汉式（多线程下破损）**

```java
public class Singleton {
    private static Singleton instance;

    private Singleton() {}

    // 问题：多线程下两个线程可能同时通过 null 检查，创建两个实例
    public static Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();  // 竞态条件！
        }
        return instance;
    }
}
```

**方式二：synchronized（正确但慢）**

```java
public class Singleton {
    private static Singleton instance;

    private Singleton() {}

    // 每次调用 getInstance() 都加锁，高并发下性能差
    public static synchronized Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}
```

**方式三：双重检查锁 + volatile（正确，但复杂）**

```java
public class Singleton {
    // volatile 防止指令重排序：确保 instance 写入对所有线程可见
    private static volatile Singleton instance;

    private Singleton() {}

    public static Singleton getInstance() {
        if (instance == null) {                    // 第一次检查（无锁）
            synchronized (Singleton.class) {
                if (instance == null) {            // 第二次检查（有锁）
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

**方式四：静态内部类（推荐，延迟加载 + 线程安全）**

```java
public class Singleton {
    private Singleton() {}

    // Holder 类只在第一次调用 getInstance() 时才被加载
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

**方式五：enum 单例（最佳，Josh Bloch 在 Effective Java 中推荐）**

```java
public enum DatabaseConnection {
    INSTANCE;

    // 字段在枚举实例初始化时创建
    private final Connection conn = createConnection();

    public Connection getConnection() {
        return conn;
    }

    private Connection createConnection() {
        // 实际创建数据库连接
        return DriverManager.getConnection("jdbc:postgresql://localhost/mydb");
    }
}

// 使用方式
Connection conn = DatabaseConnection.INSTANCE.getConnection();
```

**为什么 enum 是最佳选择：**

1. **反射攻击免疫**：试图通过反射调用 enum 构造函数会抛出 `IllegalArgumentException`，普通类的私有构造函数可以被 `Constructor.setAccessible(true)` 绕过。

2. **序列化安全**：普通 Singleton 在序列化/反序列化后会得到新实例（除非实现 `readResolve()`）；JVM 对 enum 的序列化有特殊处理，反序列化保证返回同一个枚举常量。

3. **代码简洁**：几行代码就实现了线程安全、延迟（类加载）初始化的 Singleton。

```java
// 演示反射攻击对普通类有效，对 enum 无效
// 普通类：可被攻破
Constructor<NormalSingleton> c = NormalSingleton.class.getDeclaredConstructor();
c.setAccessible(true);
NormalSingleton hacked = c.newInstance();  // 成功创建第二个实例！

// enum：免疫
// 尝试调用 DatabaseConnection 的构造函数
// 会抛出: java.lang.IllegalArgumentException: Cannot reflectively create enum objects
```

### 现代重写

在现代 Java 应用（Spring Boot 等）中，手写 Singleton 几乎已被依赖注入框架取代：

```java
// Spring 中，@Component 默认就是 Singleton scope
@Component
public class ConfigManager {
    // Spring 容器保证整个应用只有一个 ConfigManager 实例
    // 无需手写 getInstance()，无需处理线程安全
    private final String dbUrl = loadFromEnv("DB_URL");

    public String getDbUrl() { return dbUrl; }
}

// 使用：通过构造函数注入，而非全局访问点
@Service
public class UserService {
    private final ConfigManager config;

    public UserService(ConfigManager config) {  // Spring 自动注入
        this.config = config;
    }
}
```

框架管理的单例有一个巨大优势：测试时可以轻松替换为 mock 对象，而全局 `getInstance()` 无法被替换。

---

## Python 实战: module singleton + `__new__`

### 源码解析

**Python 最简单的单例：模块级实例**

Python 的模块在首次 `import` 时执行，之后被缓存在 `sys.modules` 中。模块本身就是天然的单例。

```python
# config.py
class _Config:
    """配置管理器，前缀下划线表示"内部实现，不直接使用"。"""

    def __init__(self):
        self.debug = False
        self.db_url = "sqlite:///default.db"
        self.max_connections = 10

    def load_from_env(self):
        import os
        self.debug = os.getenv("DEBUG", "false").lower() == "true"
        self.db_url = os.getenv("DATABASE_URL", self.db_url)

# 模块级单例——整个应用共享同一个实例
config = _Config()
```

```python
# 使用方式
from config import config  # 无论 import 多少次，都是同一个对象

config.load_from_env()
print(config.db_url)
```

**标准库中的模块级单例：`logging.root`**

```python
import logging

# logging 模块在初始化时创建了一个根 Logger
# logging.root 就是模块级单例
root_logger = logging.getLogger()   # 返回 logging.root
same_logger = logging.getLogger()   # 同一个对象

print(root_logger is same_logger)   # True

# logging 源码（简化）：
# root = RootLogger(WARNING)         ← 模块级单例
# Logger.root = root
# Logger.manager = Manager(Logger.root)
```

**`__new__` 实现的传统单例**

```python
class Singleton:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            # 第一次创建：委托给 object.__new__ 分配内存
            cls._instance = super().__new__(cls)
        # 后续调用：直接返回已有实例，不分配新内存
        return cls._instance

    def __init__(self, value=None):
        # 注意：__init__ 每次调用都会执行！
        # 需要用 hasattr 防止重复初始化
        if not hasattr(self, '_initialized'):
            self.value = value
            self._initialized = True


s1 = Singleton(42)
s2 = Singleton(99)
print(s1 is s2)      # True，同一对象
print(s1.value)      # 42，而非 99（初始化只执行了一次）
```

**Python 语言内置的单例**

```python
# None、True、False 在 CPython 中是语言级单例（interned objects）
a = None
b = None
print(a is b)   # True，is 比较身份（内存地址）

# 小整数也被 CPython intern（-5 到 256）
x = 256
y = 256
print(x is y)   # True（CPython 实现细节，不可依赖）

z = 257
w = 257
print(z is w)   # False（大整数不 intern）
```

### Pythonic 重写

Python 社区的共识：**模块就是单例，不需要 Singleton 类**。如果真的需要"懒加载 + 缓存"语义，用 `functools.cache`：

```python
from functools import cache

@cache
def get_config():
    """首次调用时创建 Config，之后返回缓存的同一个实例。"""
    cfg = Config()
    cfg.load_from_env()
    return cfg

# 等价于手写 _instance 缓存，但更 Pythonic
config1 = get_config()
config2 = get_config()
print(config1 is config2)  # True
```

线程安全说明：Python 有 GIL（全局解释器锁），简单赋值操作是原子的，但 GIL 不能作为并发安全的保证。若需要真正线程安全的懒加载，仍需 `threading.Lock`。

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 最佳实现 | `enum` 单例 | module-level instance |
| 线程安全 | 必须显式处理（`volatile`、`enum`） | GIL 提供基本保护，但不可依赖 |
| 反射攻击 | `enum` 免疫；普通类需防御 | Python 无法真正阻止多实例 |
| 序列化安全 | `enum` 免疫；普通类需 `readResolve()` | `pickle` 需 `__reduce__` 处理 |
| 现代替代 | DI 框架（`@Singleton` scope） | module singleton / `@cache` |
| 社区态度 | "谨慎使用，优先 DI" | "几乎不需要 Singleton 类" |

Singleton 是最具争议的设计模式之一。Java 因其以类为中心的设计，确实存在需要手动管理单例的场景；而 Python 的模块系统提供了自然的单例机制，让 Singleton 类显得多余。两个社区的共识是：**优先使用依赖注入而非全局单例**，全局状态是测试、并发和代码演化的大敌。

---

## 动手练习

**05.1 Java：enum 配置管理器**

用 `enum` 实现一个 `ConfigManager`，要求：
- 提供 `getString(String key)`、`getInt(String key, int defaultValue)` 方法
- 实现 `loadFromFile(String path)` 从 `.properties` 文件加载配置
- 思考：如何在不破坏单例的情况下支持测试中替换配置？

**05.2 Python：两种单例对比**

1. 创建 `config.py`，用模块级实例实现配置单例
2. 另起一个文件，用 `__new__` 实现同样的功能
3. 分别测试：从多个模块 import，验证是否是同一个对象（`is` 比较）
4. 思考：哪种方式在项目重构时更容易维护？

**05.3 跨语言：为什么 Singleton 是测试杀手？**

回答以下问题并写出示例代码：
- 为什么全局 Singleton 会让单元测试变得"顺序依赖"？（提示：测试 A 修改了 Singleton 状态，测试 B 看到了脏状态）
- 依赖注入如何解决这个问题？（提示：每个测试自己创建并传入实例）
- 在 Java 中，如何用 Mockito mock 掉一个通过构造函数注入的依赖，而无需改动生产代码？

---

## 回顾与连接

- **Singleton vs Flyweight（Ch19）**：Singleton 管控"只有一个实例"；Flyweight 管控"共享的实例池"——前者是绝对唯一，后者是按需复用
- **Singleton vs Factory Method（Ch06）**：Factory Method 解决"如何创建"的问题；Singleton 解决"创建多少"的问题；两者经常搭配使用
- **Singleton vs Abstract Factory（Ch18）**：Abstract Factory 描述"创建一族对象"的接口，其具体实现往往被实现为 Singleton（一个应用只需要一个 Factory）
- **Singleton 与 DI 容器**：Spring 的 `@Scope("singleton")`、Guice 的 `@Singleton` 本质上都是容器管理的 Singleton，将生命周期管理权从类本身转移给框架，大幅提升可测试性
