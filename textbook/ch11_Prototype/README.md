# Chapter 11: Prototype

> 类型: 创建 | 难度: ★★☆ | 原书: `src/ch06_Prototype/` | 前置: Ch05 (Singleton)

---

## 模式速览

**问题**: 你需要创建一个对象，但这个对象的初始化代价很高（需要读取配置文件、查询数据库、解析复杂结构），或者你需要创建多个"几乎相同"的对象，只有少数字段不同。每次从零构造既浪费又繁琐。Prototype 模式的解决方案是：把一个已经配置好的对象当作"模板"，通过克隆（clone）它来得到新对象，再按需微调——就像复印机，复印一张已有的文件比重新打字快得多。

```
«interface»
Prototype
┌──────────────┐
│ use(s)       │        Client
│ createClone()│◀───────────────────────────────┐
└──────┬───────┘                                │
       │实现                              使用 clone
       ├─────────────────┐                      │
       ▼                 ▼                      │
 MessageBox        UnderlinePen          Manager
 ┌────────────┐    ┌────────────┐    ┌─────────────────┐
 │-decoChar   │    │-underline  │    │-protoMap: Map   │
 │use()       │    │use()       │    │register(name, p)│
 │createClone()│   │createClone()│   │create(name)     │
 └────────────┘    └────────────┘    └─────────────────┘
```

**四个角色**:
- `Product` (Prototype) — 接口，声明 `use()` 和 `createClone()`，同时 `extends Cloneable`
- `Manager` (Client) — 用字符串名称注册和查找原型，调用 `createClone()` 产生副本
- `MessageBox` (ConcretePrototype) — 用装饰字符包裹字符串，克隆时复制 `decoChar`
- `UnderlinePen` (ConcretePrototype) — 在字符串下方画下划线，克隆方式相同

**核心洞察**: Manager 通过接口 `Product` 操作，完全不知道具体类是 `MessageBox` 还是 `UnderlinePen`。新增具体原型时不需要修改 Manager——这正是面向接口编程的价值。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 浅拷贝 | `Object.clone()` (native) | `copy.copy()` |
| 深拷贝 | 需手动实现 / 序列化 | `copy.deepcopy()` |
| 不可变对象的"修改副本" | record wither 方法（手动） | `dataclasses.replace()`（内置） |
| 自定义拷贝行为 | 覆盖 `clone()` | `__copy__` / `__deepcopy__` 协议 |
| 标记接口 | `Cloneable`（无方法） | 无对应概念 |

### Java `Cloneable` — 设计失误的标记接口

`Cloneable` 是 Java 中少有的"设计失误"之一，Joshua Bloch 在《Effective Java》中明确指出：

```java
// Cloneable 接口定义（java.lang 包中）——注意它没有任何方法！
public interface Cloneable {
    // 完全空的——这是一个标记接口（marker interface）
    // 它的唯一作用：告诉 Object.clone() "我允许被克隆"
    // 若未实现 Cloneable 而调用 clone()，会抛 CloneNotSupportedException
}

// Object.clone() 是 native 方法，执行逐字段的浅拷贝（bit-by-bit copy）
// 问题 1：clone() 是 protected 的，子类必须显式覆盖并改为 public
// 问题 2：返回类型是 Object，需要强制转型
// 问题 3：throws CloneNotSupportedException，调用方必须处理受检异常
// 问题 4：浅拷贝——引用字段只复制引用，不复制对象本身
protected native Object clone() throws CloneNotSupportedException;
```

### Java record wither — 不可变对象的"修改副本"

Java record 是不可变的（字段 final），无法直接修改，但可以定义 wither 方法返回新实例：

```java
// record 声明：自动生成构造器、getter、equals、hashCode、toString
record Config(String host, int port, boolean ssl) {

    // wither 方法：保持其他字段不变，仅替换 host
    Config withHost(String host) {
        return new Config(host, this.port, this.ssl);
    }

    // wither 方法：保持其他字段不变，仅替换 ssl
    Config withSsl(boolean ssl) {
        return new Config(this.host, this.port, ssl);
    }
}

// 使用：链式调用，从基础配置派生出生产环境配置
Config base = new Config("localhost", 5432, false);
Config prod = base.withHost("prod.db.com").withSsl(true);
// base 不变，prod 是全新对象——这就是 Prototype 思想在不可变对象上的体现
```

这正是 Kotlin `data class` 的 `copy()` 方法和 Python `dataclasses.replace()` 在 Java 中的手动实现。

### Python `dataclasses.replace()` — 内置的 wither

```python
from dataclasses import dataclass, replace

# frozen=True 使 dataclass 不可变，等同于 Java record
@dataclass(frozen=True)
class Config:
    host: str
    port: int = 5432
    ssl: bool = False

base = Config("localhost")
# replace() 是内置函数，无需手动写 wither
prod = replace(base, host="prod.db.com", ssl=True)
# base.host 仍是 "localhost"——原型未被修改
```

---

## Java 实战: `Object.clone()` + `Cloneable` + record wither

### 源码解析

原书的 `Product` 接口（`src/ch06_Prototype/framework/Product.java`）用 `extends Cloneable` 把克隆能力内嵌到接口合约中：

```java
// framework/Product.java — 原型接口
package ch06_Prototype.framework;

public interface Product extends Cloneable {
    // extends Cloneable：实现此接口的类自动也实现 Cloneable
    // 这样 ConcretePrototype 在调用 Object.clone() 时不会抛异常

    void use(String s);         // 业务方法：展示原型的能力
    Product createClone();      // 克隆方法：返回自身的副本
}
```

`Manager` 完全面向 `Product` 接口，不依赖任何具体类：

```java
// framework/Manager.java — 原型注册表（registry）
package ch06_Prototype.framework;

import java.util.HashMap;

public class Manager {
    // key: 字符串名称；value: 已注册的原型实例
    private final HashMap<String, Product> protoMap = new HashMap<>();

    // 注册：把原型存入 map，等待将来被克隆
    public void register(String name, Product p) {
        protoMap.put(name, p);
    }

    // 创建：找到对应原型，调用 createClone() 返回副本
    // Manager 不需要知道 Product 的具体类型——多态在此发挥作用
    public Product create(String protoName) {
        return protoMap.get(protoName).createClone();
    }
}
```

`MessageBox` 的 `createClone()` 展示了 `Object.clone()` 的典型用法：

```java
// MessageBox.java — 具体原型
package ch06_Prototype;

import ch06_Prototype.framework.Product;

public class MessageBox implements Product {
    private final char decoChar;  // 装饰字符，如 '*' 或 '~'

    public MessageBox(char decoChar) {
        this.decoChar = decoChar;
    }

    @Override
    public void use(String s) {
        // 用 decoChar 在字符串上下各画一条线
        int len = s.length();
        String line = String.valueOf(decoChar).repeat(len + 2);
        System.out.println(line);
        System.out.println(s);
        System.out.println(line);
    }

    @Override
    public Product createClone() {
        try {
            // Object.clone() 执行浅拷贝：decoChar 是 char（基本类型），
            // 浅拷贝就是完整复制——对于只含基本类型的类，浅拷贝足够安全
            return (Product) clone();
        } catch (CloneNotSupportedException e) {
            // 因为实现了 Cloneable，理论上不会到这里
            // 但 clone() 声明了受检异常，必须处理
            throw new RuntimeException(e);
        }
    }
}
```

`Main` 展示了完整的 Prototype 工作流：

```java
// Main.java — 客户端
package ch06_Prototype;

import ch06_Prototype.framework.Manager;

static void main() {
    // 1. 创建具体原型实例（这是创建代价高的地方）
    var u1 = new UnderlinePen('_');
    var m1 = new MessageBox('*');
    var m2 = new MessageBox('~');

    // 2. 注册到 Manager
    var mgr = new Manager();
    mgr.register("u1", u1);
    mgr.register("m1", m1);
    mgr.register("m2", m2);

    // 3. 通过名称克隆，无需 new，无需知道具体类
    var new_u1 = mgr.create("u1");
    var new_m1 = mgr.create("m1");
    var new_m2 = mgr.create("m2");

    new_u1.use("New U1");   // 输出下划线风格
    new_m1.use("New M1");   // 输出 * 边框风格

    // 克隆出的对象是新对象（== 返回 false），但类型相同
    System.out.println(new_m1 == m1);                         // false
    System.out.println(new_m1.getClass() == m1.getClass());  // true
}
```

### 浅拷贝 vs 深拷贝

当原型包含引用类型字段时，浅拷贝只复制引用，深拷贝才复制整个对象图：

```java
import java.util.ArrayList;
import java.util.List;

// 浅拷贝的危险：共享可变引用
class ShallowDemo implements Cloneable {
    List<String> tags = new ArrayList<>();  // 可变引用类型！

    @Override
    public ShallowDemo clone() throws CloneNotSupportedException {
        // Object.clone() 只复制引用——两个实例共享同一个 tags 列表
        return (ShallowDemo) super.clone();
    }
}

// 深拷贝：必须手动复制引用字段
class DeepDemo implements Cloneable {
    List<String> tags = new ArrayList<>();

    @Override
    public DeepDemo clone() throws CloneNotSupportedException {
        DeepDemo copy = (DeepDemo) super.clone();
        // 用复制构造器创建新列表，切断共享
        copy.tags = new ArrayList<>(this.tags);
        return copy;
    }
}

// 现代替代方案 1：复制构造器（copy constructor）——Josh Bloch 推荐
class Config {
    final String host;
    final List<String> tags;

    // 复制构造器：比 clone() 更清晰、更安全
    Config(Config other) {
        this.host = other.host;
        this.tags = List.copyOf(other.tags);  // Java 10+：不可变副本
    }
}

// 现代替代方案 2：record + wither（不可变，天然无深浅拷贝问题）
record ImmutableConfig(String host, int port, List<String> tags) {
    // 构造器中防御性复制，确保外部不能篡改内部列表
    ImmutableConfig {
        tags = List.copyOf(tags);  // 不可变包装，不是深拷贝，但已足够安全
    }

    ImmutableConfig withHost(String host) {
        return new ImmutableConfig(host, port, tags);
    }

    ImmutableConfig withTags(List<String> tags) {
        return new ImmutableConfig(host, port, tags);
    }
}
```

**关键规则**:
- 字段全是基本类型或不可变对象（`String`、`Integer`）→ 浅拷贝安全
- 字段含可变引用类型（`List`、`Map`、自定义对象）→ 必须深拷贝或改用不可变设计

---

## Python 实战: `copy` 模块 + `dataclasses.replace()`

### 源码解析

Python 的 `copy` 模块提供了两个函数，行为与 Java 的浅拷贝/深拷贝完全对应：

```python
import copy

class Node:
    def __init__(self, value, children=None):
        self.value = value
        self.children = children or []  # 可变列表——浅拷贝的危险区

original = Node(1, [Node(2), Node(3)])

# 浅拷贝：新 Node 对象，但 children 列表是共享的
shallow = copy.copy(original)
shallow.children.append(Node(99))
print(len(original.children))  # 3——原对象也被修改了！

# 深拷贝：完整复制整个对象图，包括 children 中的每个 Node
deep = copy.deepcopy(original)
deep.children.append(Node(99))
print(len(original.children))  # 2——原对象未受影响
```

### 自定义拷贝行为：`__copy__` / `__deepcopy__` 协议

```python
import copy

class DatabaseConnection:
    def __init__(self, dsn: str, pool_size: int = 5):
        self.dsn = dsn
        self.pool_size = pool_size
        # _socket 是底层资源，不应被直接拷贝
        self._socket = self._connect()

    def _connect(self):
        return f"<socket to {self.dsn}>"

    def __copy__(self):
        # 浅拷贝：创建新对象，但重新建立连接而非复制 socket
        cls = self.__class__
        result = cls.__new__(cls)  # 绕过 __init__，分配空对象
        result.__dict__.update(self.__dict__)  # 复制所有属性
        result._socket = result._connect()     # 重新建立真正的连接
        return result

    def __deepcopy__(self, memo):
        # memo 是 {id: obj} 字典，防止循环引用时无限递归
        cls = self.__class__
        result = cls.__new__(cls)
        memo[id(self)] = result  # 先注册，再填充（防止循环）
        for k, v in self.__dict__.items():
            # deepcopy 每个字段，除了 _socket（重新建立）
            if k == '_socket':
                setattr(result, k, result._connect())
            else:
                setattr(result, k, copy.deepcopy(v, memo))
        return result
```

### `dataclasses.replace()` — Pythonic 的 Prototype

对于配置类、值对象等场景，`frozen=True` 的 dataclass + `replace()` 是最地道的 Python 写法：

```python
from dataclasses import dataclass, field, replace
import copy

@dataclass(frozen=True)
class ServerConfig:
    host: str
    port: int = 5432
    ssl: bool = False
    # tuple 而非 list：不可变，frozen dataclass 可以安全持有
    allowed_hosts: tuple[str, ...] = ()

# 创建基础配置（原型）
base = ServerConfig("localhost")

# replace() 产生修改副本——只需指定要改的字段
dev  = replace(base, host="dev.internal")
prod = replace(base, host="prod.db.com", ssl=True,
               allowed_hosts=("10.0.0.0/8",))

# 链式派生：从 prod 再派生出只读副本
prod_ro = replace(prod, port=5433)

print(base)    # ServerConfig(host='localhost', port=5432, ssl=False, ...)
print(prod)    # ServerConfig(host='prod.db.com', port=5432, ssl=True, ...)
print(prod_ro) # ServerConfig(host='prod.db.com', port=5433, ssl=True, ...)
# base 始终不变——它就是原型模板
```

### 含可变字段的 `__deepcopy__` 进阶

当 dataclass 含有可变默认值时（比如 `list`），需要结合 `__deepcopy__` 确保安全：

```python
from dataclasses import dataclass, field
import copy

@dataclass
class Pipeline:
    name: str
    # field(default_factory=list) 是 dataclass 处理可变默认值的标准写法
    stages: list[str] = field(default_factory=list)
    metadata: dict[str, str] = field(default_factory=dict)

    def __deepcopy__(self, memo):
        # 完整深拷贝：确保 stages 和 metadata 都是独立副本
        return Pipeline(
            name=self.name,                         # str 不可变，直接复用
            stages=copy.deepcopy(self.stages, memo),
            metadata=copy.deepcopy(self.metadata, memo),
        )

original = Pipeline("etl", ["extract", "transform", "load"])
cloned   = copy.deepcopy(original)
cloned.stages.append("validate")

print(original.stages)  # ['extract', 'transform', 'load']——未受影响
print(cloned.stages)    # ['extract', 'transform', 'load', 'validate']
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 核心机制 | `Object.clone()` + `Cloneable` | `copy.copy()` / `copy.deepcopy()` |
| 设计质量 | `clone()` 被认为是设计失误 | `copy` 模块设计良好，普遍可用 |
| 现代推荐 | 复制构造器 / record wither | `dataclasses.replace()` |
| 不可变支持 | record（Java 16+）手动 wither | `frozen=True` + `replace()`（内置） |
| 自定义拷贝 | 覆盖 `clone()` 方法 | `__copy__` / `__deepcopy__` 协议 |
| 循环引用处理 | 需序列化或手动处理 | `deepcopy` 的 `memo` 自动处理 |
| 语言原生支持 | 无 copy 语法糖 | `replace()` 接近语言级别支持 |

**结论**: Java 的 Prototype 更多依靠设计模式规范（`Manager` + `createClone()` 接口），而 Python 的 `copy` 模块和 `replace()` 把 Prototype 的核心思想直接内置到标准库中，无需额外的设计模式基础设施。

---

## 动手练习

### 11.1 Java: Config record 的 wither 方法族

实现一个 `DatabaseConfig` record，字段包括 `host`、`port`、`database`、`maxConnections`，为每个字段提供对应的 wither 方法。从一个 `local` 基础配置出发，派生出 `staging` 和 `production` 配置，要求每个环境只改动必要的字段。

```
local:      host=localhost, port=5432, database=mydb, maxConnections=5
staging:    host=staging.db.internal, maxConnections=20
production: host=prod.db.com, maxConnections=100（ssl 额外字段）
```

### 11.2 Python: 带自定义 `__deepcopy__` 的 frozen dataclass

实现一个 `CacheConfig` frozen dataclass，字段包括 `ttl`（秒）、`max_size`、`eviction_policy`（字符串）和 `tags`（`tuple[str, ...]`）。要求：
1. 用 `replace()` 从基础配置派生出"短 TTL 热点缓存"和"大容量冷数据缓存"
2. 在非 frozen 版本中添加 `hit_count: int = 0` 字段，实现 `__deepcopy__`，克隆时将 `hit_count` 重置为 0（克隆出的是"全新"缓存实例）

### 11.3 浅拷贝 vs 深拷贝：画图理解

给定以下对象结构，用 ASCII 图分别画出浅拷贝和深拷贝后的内存布局：

```
原始对象:
Order
├── id: "ORD-001"          (String，不可变)
├── items: ArrayList       (可变列表)
│   ├── Item("A", 10)
│   └── Item("B", 20)
└── customer: Customer     (可变对象)
    └── name: "Alice"
```

浅拷贝后，修改 `copy.items` 会影响 `original.items` 吗？修改 `copy.id` 呢？说明原因。

---

## 回顾与连接

**与其他模式的关系**:

| 模式 | 关系 |
|------|------|
| Builder (Ch10) | Builder 从零构建对象，逐步组装；Prototype 克隆已有对象，微调差异 |
| Singleton (Ch05) | Singleton 确保全局只有一个实例；Prototype 从一个模板产生多个副本 |
| Composite (Ch12) | Composite 树结构中常用 Prototype 克隆整棵子树，实现树节点的复制 |
| Flyweight (Ch19) | Flyweight 共享实例以节省内存；Prototype 复制实例——方向相反 |
| Abstract Factory (Ch18) | Abstract Factory 可以用 Prototype 注册表替代：用原型名称动态选择产品族 |

**什么时候选 Prototype**:
- 对象初始化复杂（数据库查询、文件解析、网络请求），克隆比重建便宜
- 需要大量"几乎相同"的对象，只有少数字段不同
- 想在运行时动态添加/移除原型（Manager 注册表模式），无需修改客户端代码
- 语言不支持直接 `new`（如通过反射或工厂动态创建）时，克隆是替代方案

**关键洞察**: Prototype 的本质是**把"如何创建对象"的知识封装在对象自身中**（`createClone()` 是对象的方法），而不是分散在工厂或客户端代码里。这让对象的创建逻辑与对象的使用逻辑保持在同一个地方，符合"谁知道自己的结构，谁就负责复制自己"的单一职责原则。
