# Chapter 06: Factory Method

> 类型: 创建 | 难度: ★★☆ | 原书: `src/ch04_FactoryMethod/` | 前置: Ch03 (Template Method)

---

## 模式速览

Factory Method 是 Template Method 在**对象创建**场景中的直接应用。父类（Creator）定义创建产品的骨架流程，子类（ConcreteCreator）决定到底实例化哪个具体产品。客户端只依赖抽象接口，永远不直接 `new` 具体类。

```
     Creator
  ──────────────────────────────
  + create(owner): Product      ← 模板方法（final），固定流程
    ├─ createProduct(owner)     ← 工厂方法（abstract），子类覆写
    └─ registerProduct(p)       ← 钩子（abstract），子类覆写
           ▲
           │
   ConcreteCreator              ConcreteProduct
   (IDCardFactory)              (IDCard)
   覆写 createProduct()   ──→   implements Product
   覆写 registerProduct()
```

**核心思想**：把"创建哪种对象"这个变化点推迟到子类，父类只负责调用流程。这与 Template Method 如出一辙——只不过"可变的步骤"变成了实例化一个产品。

> **一句话记忆**：Factory Method = Template Method，只是那个抽象步骤叫做 `createProduct()`。

---

## 本章新语言特性

### Java：`sealed interface` 与 `permits`（Java 17+）

传统 Factory Method 用抽象类约束产品层次。Java 17 引入 `sealed`，让编译器**显式枚举**所有合法子类，天然配合 `switch` 模式匹配做穷举检查。

| 特性 | 说明 |
|------|------|
| `sealed interface Foo permits A, B` | 只有 `A`、`B` 可以实现 `Foo`，其余类编译报错 |
| `non-sealed class` | 打开封印，允许该子类被进一步继承 |
| `record` 实现 `sealed interface` | 最简洁的不可变产品实现 |
| `switch` 模式匹配 + `sealed` | 编译器检查分支是否穷举，省去 `default` |

```java
// sealed 约束产品层次：只有 Circle 和 Rectangle 能实现 Shape
sealed interface Shape permits Circle, Rectangle {
    double area();
}

// record 自动生成构造器、equals、hashCode、toString
record Circle(double radius) implements Shape {
    public double area() { return Math.PI * radius * radius; }
}

record Rectangle(double w, double h) implements Shape {
    public double area() { return w * h; }
}

// switch 模式匹配：编译器验证是否覆盖了全部子类
static String describe(Shape s) {
    return switch (s) {
        case Circle c    -> "圆形，半径 " + c.radius();
        case Rectangle r -> "矩形，" + r.w() + " × " + r.h();
        // 无需 default：sealed 保证穷举
    };
}
```

`sealed` 的价值在于**把层次结构的知识固化在编译器里**。增加新子类时，所有 `switch` 语句都会报错提醒——这是运行时异常无法做到的安全感。

### Python：`typing.Protocol` 与 `@classmethod`

Python 没有 `abstract class` 的强制约束，但有两种工厂惯用法：

| 特性 | 说明 |
|------|------|
| `typing.Protocol` | 结构化子类型（鸭子类型的静态版本），无需继承即可满足接口 |
| `@runtime_checkable` | 让 `isinstance()` 支持 Protocol 检查 |
| `@classmethod` | 命名构造器（Named Constructor），同一个类可以有多个工厂入口 |
| `__new__` | 构造器本身可以是工厂——`pathlib.Path()` 就是这样实现的 |

```python
from typing import Protocol, runtime_checkable

# Protocol 定义"产品接口"——无需继承，只需实现对应方法
@runtime_checkable
class Shape(Protocol):
    def area(self) -> float: ...

# 具体产品：无需继承 Shape，只要有 area() 方法即可满足 Protocol
class Circle:
    def __init__(self, radius: float) -> None:
        self.radius = radius

    def area(self) -> float:
        return 3.14159 * self.radius ** 2

    # @classmethod 作为命名构造器——工厂方法的 Python 惯用形式
    @classmethod
    def unit(cls) -> "Circle":
        """创建单位圆（半径 = 1）"""
        return cls(1.0)

    @classmethod
    def from_diameter(cls, diameter: float) -> "Circle":
        """从直径创建圆"""
        return cls(diameter / 2)
```

---

## Java 实战：JDK 中的 Factory Method

### `Collection.iterator()`——最经典的工厂方法

`java.util.Collection` 接口中有：

```java
Iterator<E> iterator();  // 这就是工厂方法
```

每个具体集合类覆写它，返回自己专属的迭代器实现：

- `ArrayList.iterator()` → 返回私有内部类 `ArrayList.Itr`
- `HashSet.iterator()` → 委托给 `HashMap.KeyIterator`
- `LinkedList.iterator()` → 返回 `ListItr`

客户端只持有 `Iterator<E>` 接口引用，完全不知道背后是哪种迭代器——这正是 Factory Method 的精髓：**产品类型的选择权在子类**。

### `EnumSet.of()`——运行时选择具体产品

```java
// 客户端只看到 EnumSet，内部根据枚举常量数量选择实现
EnumSet<Day> weekdays = EnumSet.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
```

`EnumSet.of()` 的源码逻辑：

```java
// JDK 内部（简化示意）
public static <E extends Enum<E>> EnumSet<E> of(E first, E... rest) {
    EnumSet<E> result = noneOf(first.getDeclaringClass());
    result.add(first);
    // ...
    return result;
}

static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
    // 工厂方法：根据枚举大小选择实现类
    if (universe.length <= 64)
        return new RegularEnumSet<>(elementType, universe);  // 位操作优化
    else
        return new JumboEnumSet<>(elementType, universe);    // 长位图
}
```

客户端写 `EnumSet.of(...)` 时，完全不需要知道 `RegularEnumSet` 还是 `JumboEnumSet` 的存在——这就是工厂方法把"选择权"封装起来的价值。

### `Calendar.getInstance()`——地区敏感的工厂

```java
Calendar cal = Calendar.getInstance();          // 根据系统 Locale 返回不同子类
Calendar cal = Calendar.getInstance(Locale.JP); // 返回 JapaneseImperialCalendar
```

这是 Factory Method 的静态变体（有时称为"静态工厂方法"）。

### 用现代 Java 重写教科书示例

原书（`src/ch04_FactoryMethod/`）用抽象类实现。下面用 `sealed interface` + `record` 重写，语义更精确：

```java
// ── 产品层 ──────────────────────────────────────────────
sealed interface Shape permits Circle, Rectangle {
    double area();
    String describe();  // 产品统一接口
}

record Circle(double radius) implements Shape {
    // record 的 compact constructor 做参数校验
    Circle {
        if (radius <= 0) throw new IllegalArgumentException("半径必须为正数");
    }
    public double area()     { return Math.PI * radius * radius; }
    public String describe() { return "圆形（半径 %.2f）".formatted(radius); }
}

record Rectangle(double w, double h) implements Shape {
    Rectangle {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("边长必须为正数");
    }
    public double area()     { return w * h; }
    public String describe() { return "矩形（%.2f × %.2f）".formatted(w, h); }
}

// ── 创建者层 ─────────────────────────────────────────────
abstract class ShapeFactory {
    // 模板方法：固定"创建 → 注册 → 返回"流程
    public final Shape create(String spec) {
        Shape shape = createShape(spec);    // 工厂方法（抽象）
        register(shape);                    // 钩子（抽象）
        return shape;
    }

    protected abstract Shape createShape(String spec);
    protected abstract void register(Shape shape);
}

// ── 具体创建者 ────────────────────────────────────────────
class CircleFactory extends ShapeFactory {
    private final List<Shape> created = new ArrayList<>();

    @Override
    protected Shape createShape(String spec) {
        // spec 格式："r=3.5"
        double r = Double.parseDouble(spec.split("=")[1]);
        return new Circle(r);
    }

    @Override
    protected void register(Shape shape) {
        created.add(shape);
        System.out.println("已注册: " + shape.describe());
    }
}

// ── 使用示例 ──────────────────────────────────────────────
ShapeFactory factory = new CircleFactory();
Shape c = factory.create("r=5.0");
System.out.println(c.area());   // 78.539...

// sealed + switch：编译器保证穷举
double result = switch (c) {
    case Circle ci    -> ci.radius() * 2;           // 直径
    case Rectangle re -> re.w() * 2 + re.h() * 2;  // 周长
};
```

---

## Python 实战：工厂方法的 Pythonic 表达

### `pathlib.Path()`——`__new__` 就是工厂

```python
from pathlib import Path

# 同一个构造器调用，在不同平台返回不同子类
p = Path("/usr/local/bin")
# macOS/Linux: 返回 PosixPath 实例
# Windows:     返回 WindowsPath 实例
```

`Path.__new__` 的实现逻辑（简化）：

```python
class Path:
    def __new__(cls, *args, **kwargs):
        # 工厂逻辑藏在 __new__ 里：根据操作系统选择子类
        if cls is Path:
            cls = WindowsPath if os.name == 'nt' else PosixPath
        return object.__new__(cls)
```

构造器本身成为工厂——客户端永远只写 `Path(...)`，平台细节完全透明。

### `@classmethod` 命名构造器——同一类的多个工厂入口

标准库里大量使用这种模式：

```python
from datetime import datetime, date

# classmethod 作为工厂方法——不同的创建语义，不同的入口
today   = date.today()                      # 当前日期
from_ts = datetime.fromtimestamp(1700000000) # 从时间戳创建
parsed  = datetime.fromisoformat("2024-01-15T10:30:00")  # 从字符串创建
utcnow  = datetime.utcnow()                 # UTC 时间

# dict.fromkeys 也是 classmethod 工厂
counts = dict.fromkeys(["apple", "banana", "cherry"], 0)
# → {'apple': 0, 'banana': 0, 'cherry': 0}
```

### Protocol + `@classmethod` 重写教科书示例

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Protocol, runtime_checkable

# ── 产品协议 ─────────────────────────────────────────────
@runtime_checkable
class Product(Protocol):
    """产品接口：凡是有 use() 方法的对象都满足此协议"""
    def use(self) -> None: ...

# ── 具体产品 ─────────────────────────────────────────────
class IDCard:
    def __init__(self, owner: str) -> None:
        print(f"正在制作 {owner} 的 ID 卡")
        self._owner = owner

    @property
    def owner(self) -> str:
        return self._owner

    def use(self) -> None:
        print(f"使用 {self._owner} 的 ID 卡")

    # 命名构造器：工厂方法的 Python 惯用形式
    @classmethod
    def guest(cls) -> IDCard:
        """创建访客临时卡"""
        return cls("访客")

    @classmethod
    def from_employee_id(cls, emp_id: int) -> IDCard:
        """从工号创建员工卡"""
        return cls(f"员工#{emp_id:06d}")

# ── 抽象创建者 ────────────────────────────────────────────
class Factory(ABC):
    def create(self, owner: str) -> Product:
        """模板方法：固定创建流程"""
        product = self._create_product(owner)   # 工厂方法
        self._register_product(product)          # 钩子
        return product

    @abstractmethod
    def _create_product(self, owner: str) -> Product:
        """子类决定创建哪种产品"""
        ...

    @abstractmethod
    def _register_product(self, product: Product) -> None:
        """子类决定如何注册"""
        ...

# ── 具体创建者 ────────────────────────────────────────────
class IDCardFactory(Factory):
    def __init__(self) -> None:
        self._owners: list[str] = []

    def _create_product(self, owner: str) -> IDCard:
        return IDCard(owner)

    def _register_product(self, product: Product) -> None:
        assert isinstance(product, IDCard)   # Protocol 运行时检查
        self._owners.append(product.owner)
        print(f"已注册: {product.owner}")

    @property
    def owners(self) -> list[str]:
        return self._owners.copy()

# ── 使用示例 ──────────────────────────────────────────────
factory = IDCardFactory()
card1 = factory.create("张三")
card2 = factory.create("李四")
card2.use()

# 命名构造器（classmethod 工厂）
guest_card = IDCard.guest()
guest_card.use()  # 使用 访客 的 ID 卡
```

---

## 两种哲学的对比

| 维度 | Java | Python |
|------|------|--------|
| 产品接口 | `sealed interface` / `abstract class` | `Protocol`（结构子类型）/ `ABC` |
| 层次约束 | `permits` 列出所有合法子类，编译期检查 | 无约束，鸭子类型，运行期检查 |
| 工厂方法位置 | 必须在类中，通常 `protected` | 可以是任意函数；`@classmethod` 是惯用法 |
| 穷举保证 | `switch` + `sealed` → 编译器报警 | `match` 无穷举保证，需手写 `case _` |
| 多个工厂入口 | 静态方法 / 多个 ConcreteCreator | `@classmethod` 命名构造器，极其自然 |
| 类型安全 | 编译期，`instanceof` 检查 | `isinstance(x, Protocol)` 运行期 |

**核心分歧**：Java 的 `sealed` 把"产品家族的边界"写入类型系统，任何违规都是编译错误。Python 的 Protocol 不关心继承关系，只看方法签名——灵活但需要测试覆盖来弥补静态检查的缺失。

两者没有高下之分，是类型系统哲学的根本差异：**名义类型（nominal typing）vs 结构类型（structural typing）**。

---

## 原书代码回顾（`src/ch04_FactoryMethod/`）

原书用经典的抽象类风格实现，层次清晰：

```
framework/
  Product.java        ← abstract class，声明 use()
  Factory.java        ← abstract class，template method = create()
                         abstract methods: createProduct(), registerProduct()
idcard/
  IDCard.java         ← extends Product，具体产品
  IDCardFactory.java  ← extends Factory，具体工厂
Main.java             ← 只依赖 framework 层，不 import idcard
```

`Factory.create()` 是 `final` 的模板方法：

```java
// framework/Factory.java
public final Product create(String owner) {
    Product p = createProduct(owner);   // 工厂方法，子类实现
    registerProduct(p);                 // 钩子，子类实现
    return p;
}
```

`Main.java` 里，`factory` 变量声明为 `IDCardFactory`（具体类型），但 `create()` 返回值是 `Product`。这体现了 Factory Method 的典型用法：**创建者知道具体类型，调用者只看抽象接口**。

---

## 动手练习

### 06.1 Java：sealed Shape 层次 + 工厂

实现一个 `ShapeFactory`，支持通过字符串 `"circle:3.0"` 或 `"rect:4.0,5.0"` 创建对应的 `Shape`。要求：

- 产品层用 `sealed interface Shape permits Circle, Rectangle`
- `Circle`、`Rectangle` 用 `record` 实现
- 创建者用抽象类，`create(String spec)` 为模板方法
- 在 `describe()` 方法中用 `switch` 模式匹配，利用 `sealed` 实现穷举检查

### 06.2 Python：Protocol + @classmethod 工厂

实现一个图形工厂系统：

- 用 `Protocol` 定义 `Shape` 接口（`area()`、`perimeter()`）
- `Circle` 和 `Triangle` 各自实现 `Shape`，并提供至少两个 `@classmethod` 命名构造器（如 `Circle.unit()`、`Triangle.equilateral(side)`）
- 抽象工厂基类用 `ABC`，`create()` 为模板方法
- 用 `isinstance(product, Shape)` 验证 Protocol 的运行期检查

### 06.3 思考题：何时用 Factory Method vs 直接构造？

考虑以下三种场景，分别判断是否需要 Factory Method，并说明理由：

1. 一个 `Config` 类，总是从 JSON 文件读取，无其他来源
2. 一个 `Connection` 类，根据 URL scheme（`mysql://`、`postgres://`、`sqlite://`）返回不同实现
3. 一个 `Point` 类，需要支持直角坐标 `(x, y)` 和极坐标 `(r, θ)` 两种创建方式

---

## 回顾与连接

```
Ch03 Template Method
       │
       │  "把可变步骤从模板方法中分离"
       ▼
Ch06 Factory Method          ← 本章：可变步骤 = 创建产品
       │
       │  "一族相关的工厂方法"
       ▼
Ch18 Abstract Factory        ← 多个产品维度同时变化
       │
       │  "不用 new，复制现有对象"
       ▼
Ch11 Prototype               ← 克隆代替实例化
```

| 模式 | 核心问题 | 解法 |
|------|----------|------|
| Factory Method | 子类决定创建哪种产品 | 抽象方法 + 继承 |
| Abstract Factory | 创建一族相关产品 | 多个工厂方法组合 |
| Prototype | 创建成本高，复制更快 | `clone()` / `copy.deepcopy()` |
| Builder（Ch10） | 产品构建步骤复杂 | 分步构建 + Director |

**Factory Method 的核心价值**：让框架代码（父类）调用应用代码（子类）来创建对象——这是"控制反转"在对象创建领域最简洁的体现。当你发现自己在父类里写 `if (type.equals("A")) return new A()` 时，通常意味着该把这个 `if` 提升为工厂方法了。
