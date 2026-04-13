# Chapter 19: Flyweight

> 类型: 结构 | 难度: ★★★ | 原书: `src/ch20_Flyweight/` | 前置: Ch05 (Singleton)

---

## 模式速览

**问题**: 文字编辑器要在屏幕上渲染数万个字符，每个字符如果都是独立对象，内存会爆掉。游戏地图里有几十万棵相同的树，每棵树单独保存完整纹理数据根本不现实。这类场景的共同特征是：大量细粒度对象存在，但它们的*大部分状态是相同的*。

Flyweight 的解法是：**把状态分为两类，只共享不变的那部分**。

- **Intrinsic state（内在状态）**: 对象本身固有的、可以被共享的状态。与上下文无关，多个调用方共享同一份数据。例如字符的字形数据、树的纹理图片。
- **Extrinsic state（外在状态）**: 随上下文变化的状态，由调用方在使用时传入，不存储在 Flyweight 对象内。例如字符在屏幕上的坐标、树在地图上的位置。

```
                   Client
                     │
                     ▼
              FlyweightFactory
              ┌──────────────┐
              │   pool: Map  │   若已有 → 直接返回
              │  ┌─────────┐ │   若没有 → 创建并缓存
              │  │'A'→obj  │ │
              │  │'B'→obj  │ │
              │  │'3'→obj  │ │
              │  └─────────┘ │
              └──────┬───────┘
                     │ 返回共享实例
          ┌──────────┼──────────┐
          ▼          ▼          ▼
       BigChar    BigChar    BigChar
        ('A')      ('B')      ('A')  ← 同一个对象！
      [Intrinsic]
      fontdata = "###\n#  \n..."    ← 字形数据，只存一份

   调用时传入 Extrinsic state：
   bigChar.print(x=10, y=5)    ← 坐标由调用方传入
```

**四个角色**:

| 角色 | 本章对应 | 职责 |
|------|---------|------|
| Flyweight | `BigChar` | 持有 intrinsic state，接受 extrinsic state 作为参数 |
| FlyweightFactory | `BigCharFactory` | 管理实例池，确保同一 key 只有一个实例 |
| ConcreteFlyweight | `BigChar`（同上） | 本例中 Flyweight 无抽象层，直接是具体类 |
| Client | `BigString` / `Main` | 通过 factory 获取 flyweight，自行持有 extrinsic state |

**核心洞察**: Flyweight 的本质是**对象级别的缓存**——不是缓存计算结果，而是缓存对象本身。FlyweightFactory 就是一个带生命周期管理的对象池。Singleton 保证一个类只有一个实例；Flyweight 保证相同 key 只有一个实例（实例池规模通常大于 1）。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 值对象标注 | `@ValueBased` 注解概念（Java 16+） | `__slots__` 禁用 `__dict__`，减少每实例内存 |
| 内存测量 | `Runtime.getRuntime().totalMemory()` | `sys.getsizeof()`，`tracemalloc` |
| 字符串驻留 | `String.intern()` | `sys.intern()` |
| 整数缓存 | `Integer.valueOf()` 缓存 -128~127 | CPython 小整数缓存 -5~256 |
| 枚举单例 | `enum` 成员天然 Singleton | `enum.Enum` 成员同样是单例 |

### Java `@ValueBased` — 标记"不应有身份"的类

Java 16 引入 `@ValueBased` 注解（`java.lang.invoke` 包下），用于标记那些语义上应视为"值"而非"对象"的类——即不应依赖引用相等性（`==`）、不应对其加锁、不应假设其唯一性的类。`Integer`、`Double`、`Optional` 等 JDK 类均已加上此注解。

```java
// 不正确的用法（@ValueBased 类不应依赖引用身份）
Integer a = Integer.valueOf(200);
Integer b = Integer.valueOf(200);
System.out.println(a == b);          // false（超出缓存范围，是两个对象）
System.out.println(a.equals(b));     // true（值相等）

// @ValueBased 对象的正确比较方式永远是 equals()，而非 ==
// 未来的 Project Valhalla 会把这类对象变成真正的值类型（在栈上分配）
```

### Python `__slots__` — 固定属性布局以节省内存

默认情况下，Python 每个实例都有一个 `__dict__`（字典），存储所有实例属性。字典本身开销很大。`__slots__` 告诉解释器：这个类的实例只有这几个固定属性，不需要 `__dict__`，从而大幅减少内存占用。

```python
import sys

class NormalPoint:
    """普通类：每个实例有 __dict__，内存开销大"""
    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y

class SlottedPoint:
    """使用 __slots__：无 __dict__，内存紧凑"""
    __slots__ = ("x", "y")

    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y

normal = NormalPoint(1.0, 2.0)
slotted = SlottedPoint(1.0, 2.0)

print(sys.getsizeof(normal))          # 约 48 字节（仅对象头，不含 __dict__）
print(sys.getsizeof(normal.__dict__)) # 约 232 字节（dict 本身）
print(sys.getsizeof(slotted))         # 约 56 字节（含两个 slot，无 __dict__）

# __slots__ 也禁止动态添加属性
# slotted.z = 3.0                     # 抛 AttributeError
```

百万个实例时，`__slots__` 可节省约 30-50% 内存，是 Python 中 Flyweight intrinsic state 的理想载体。

---

## Java 实战: `Integer.valueOf()` 缓存 + `String.intern()`

### 源码解析

#### `Integer.valueOf(int)` — JDK 内置的整数 Flyweight 池

`Integer.valueOf()` 是 JDK 最经典的 Flyweight 实现。源码在 `IntegerCache` 内部类中维护了一个 `Integer[]` 数组，缓存 -128 到 127 之间的所有装箱整数。这个范围内的整数在程序中使用极其频繁，共享实例可以省去大量内存分配和 GC 压力。

```java
// Integer.valueOf 的简化源码逻辑（JDK 内部 IntegerCache）
public static Integer valueOf(int i) {
    // 缓存范围 [-128, 127]，命中则直接返回共享实例
    if (i >= IntegerCache.low && i <= IntegerCache.high) {
        return IntegerCache.cache[i + (-IntegerCache.low)];
    }
    // 超出范围则每次 new 一个新对象
    return new Integer(i);
}

// 演示：引用相等性
Integer a = Integer.valueOf(100);
Integer b = Integer.valueOf(100);
System.out.println(a == b);          // true（同一个缓存实例）
System.out.println(a.equals(b));     // true

Integer x = Integer.valueOf(200);
Integer y = Integer.valueOf(200);
System.out.println(x == y);          // false（超出缓存，两个不同对象）
System.out.println(x.equals(y));     // true（值相等）

// 自动装箱（autoboxing）也走缓存
Integer p = 50;    // 编译器自动转为 Integer.valueOf(50)
Integer q = 50;
System.out.println(p == q);          // true（命中缓存）
```

**实际意义**：循环里的 `for (int i = 0; i < 10000; i++)` 中的循环变量装箱，以及 `Map<String, Integer>` 存储小整数，都受益于这个缓存。上层业务代码**永远不应**用 `==` 比较 `Integer` 对象，而应用 `equals()`——这是 `@ValueBased` 语义的实践要求。

#### `String.intern()` — 字符串常量池

字符串常量池（string pool）是另一个 Flyweight 池。字面量字符串在编译期自动进入常量池；运行时动态创建的字符串通过 `intern()` 可以手动加入。

```java
// 字面量字符串：编译期自动驻留
String s1 = "hello";
String s2 = "hello";
System.out.println(s1 == s2);        // true（同一个常量池对象）

// new 出来的字符串：在堆上，不在常量池
String s3 = new String("hello");
System.out.println(s1 == s3);        // false（不同对象）

// intern() 手动加入常量池
String s4 = s3.intern();
System.out.println(s1 == s4);        // true（s4 指向常量池中的那个）

// 实际应用：大量重复字符串时节省内存（如解析 CSV 的列名、枚举值字符串）
String city1 = readFromCsv().intern();   // 把重复的城市名归一化到常量池
String city2 = readFromCsv().intern();
System.out.println(city1 == city2);      // true（如果城市名相同）
```

#### `Boolean.TRUE` / `Boolean.FALSE` — 只有两个实例的 Flyweight

`Boolean` 类只有两个合法值，因此只维护两个共享实例：

```java
// Boolean 是最极端的 Flyweight：整个 JVM 只有两个实例
Boolean t1 = Boolean.valueOf(true);
Boolean t2 = Boolean.valueOf(true);
System.out.println(t1 == t2);        // true（永远是同一个 Boolean.TRUE）
System.out.println(t1 == Boolean.TRUE); // true

// Character 也有类似缓存（0~127 的 ASCII 字符）
Character c1 = Character.valueOf('A');
Character c2 = Character.valueOf('A');
System.out.println(c1 == c2);        // true（ASCII 范围内命中缓存）
```

### 现代重写：文字编辑器字符 Flyweight

用 Java 21+ 特性重写教科书的 `BigChar` 示例，将字形数据从文件加载替换为内存字符串，并用 `record` + `ConcurrentHashMap` 构建线程安全的 Flyweight 工厂：

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flyweight：持有字符的 intrinsic state（字形数据）
 * 用 record 确保不可变性——Flyweight 的 intrinsic state 必须不可变
 */
record Glyph(char charName, String fontData) {

    // 显示字形（extrinsic state 由调用方传入：坐标、颜色等）
    void render(int x, int y) {
        System.out.printf("[%d,%d] 渲染字符 '%c':%n%s%n", x, y, charName, fontData);
    }
}

/**
 * FlyweightFactory：管理 Glyph 实例池
 * 相同 charName 的 Glyph 只创建一次，后续调用复用同一实例
 */
class GlyphFactory {

    // 实例池：key = 字符，value = 共享的 Flyweight 实例
    private static final Map<Character, Glyph> POOL = new ConcurrentHashMap<>();

    // 私有构造，防止外部实例化（工厂本身用静态方法暴露）
    private GlyphFactory() {}

    /**
     * 获取指定字符的 Flyweight 实例
     * computeIfAbsent 保证线程安全：相同 key 不会创建两次
     */
    public static Glyph getGlyph(char c) {
        return POOL.computeIfAbsent(c, key -> {
            System.out.printf("[Factory] 创建新 Glyph('%c')%n", key);
            var fontData = loadFontData(key);   // 模拟加载字形数据
            return new Glyph(key, fontData);
        });
    }

    public static int poolSize() {
        return POOL.size();
    }

    /** 模拟从字体文件加载字形数据（实际项目中可能读取 TTF/OTF） */
    private static String loadFontData(char c) {
        return switch (c) {
            case 'A' -> "###\n# #\n###\n# #\n# #";
            case 'B' -> "## \n# #\n## \n# #\n## ";
            case 'C' -> " ##\n#  \n#  \n#  \n ##";
            default  -> String.valueOf(c);
        };
    }
}

/**
 * Client：使用 Flyweight 渲染一段文字
 * 只持有 extrinsic state（字符序列），每个位置的坐标在渲染时动态计算
 */
class TextLine {

    private final String text;

    TextLine(String text) {
        this.text = text;
    }

    void render() {
        for (int i = 0; i < text.length(); i++) {
            // extrinsic state（x 坐标）在调用时传入，不存储在 Flyweight 内
            var glyph = GlyphFactory.getGlyph(text.charAt(i));
            glyph.render(i * 10, 0);
        }
    }
}

// 演示：渲染 "ABCABA"，'A' 和 'B' 只各创建一次
var line = new TextLine("ABCABA");
line.render();
System.out.printf("Flyweight 池中共 %d 个实例（字符种类数）%n", GlyphFactory.poolSize());
// 输出:
// [Factory] 创建新 Glyph('A')
// [Factory] 创建新 Glyph('B')
// [Factory] 创建新 Glyph('C')
// [0,0] 渲染字符 'A': ...
// [10,0] 渲染字符 'B': ...
// ... （'A'、'B' 复用，不再打印 "创建新" 日志）
// Flyweight 池中共 3 个实例（字符种类数）
```

---

## Python 实战: 小整数缓存 + `sys.intern` + `__slots__`

### 源码解析

#### CPython 小整数缓存 — 解释器级别的 Flyweight

CPython（官方 Python 实现）在启动时预创建 -5 到 256 的所有整数对象，此后这个范围内的整数运算直接返回缓存对象，不再分配新内存。这是解释器层面的 Flyweight，对用户完全透明。

```python
import sys

# 小整数缓存范围：-5 到 256
a = 256
b = 256
print(a is b)    # True（同一个缓存对象）
print(id(a) == id(b))  # True

c = 257
d = 257
print(c is d)    # False（超出缓存范围，两个不同对象）
# 注意：在交互式 REPL 中可能因优化而偶尔为 True，
# 在脚本文件中行为更稳定

# 负数缓存范围
e = -5
f = -5
print(e is f)    # True

g = -6
h = -6
print(g is h)    # False（超出缓存范围）

# 查看对象身份（内存地址）
for i in range(-5, 260):
    x = i
    y = i
    if x is not y:
        print(f"缓存边界：{i} 开始不再共享")
        break
```

#### `sys.intern()` — 手动字符串驻留

```python
import sys

# 普通字符串：动态创建的字符串通常不共享对象
s1 = "hello world"      # 非标识符格式，解释器不自动驻留
s2 = "hello world"
print(s1 is s2)          # 可能 True（小字符串优化），也可能 False，不保证

# sys.intern() 强制驻留：同内容的字符串共享同一对象
t1 = sys.intern("hello world")
t2 = sys.intern("hello world")
print(t1 is t2)          # True（保证共享）

# 实际用途：大量重复字符串（如从数据库、CSV 加载的枚举值）
import csv
from io import StringIO

data = "city,score\nBeijing,90\nShanghai,85\nBeijing,92\nShanghai,88\n"
reader = csv.DictReader(StringIO(data))

# 不驻留：每行的 city 字符串可能是不同对象
rows_no_intern = [row for row in reader]

# 驻留：相同 city 字符串共享同一对象，节省内存，比较时可用 is（快）
reader = csv.DictReader(StringIO(data))
rows_interned = [
    {k: sys.intern(v) for k, v in row.items()}
    for row in reader
]

# 驻留后，字符串比较可以用 is 代替 ==（指针比较比字符逐一比快）
print(rows_interned[0]["city"] is rows_interned[2]["city"])  # True（都是 "Beijing"）
```

#### `__slots__` 实现紧凑的 Flyweight 对象

```python
import sys

class TreeFlyweight:
    """
    游戏地图中树的 Flyweight：只存储 intrinsic state（树种、纹理数据）
    使用 __slots__ 消除 __dict__，大幅节省每实例内存
    Extrinsic state（坐标、缩放比例）由调用方在 render() 时传入
    """
    __slots__ = ("species", "texture_data", "height")

    def __init__(self, species: str, texture_data: bytes, height: float):
        self.species = species
        self.texture_data = texture_data   # 可能是几 KB 的纹理数据
        self.height = height

    def render(self, x: float, y: float, scale: float = 1.0) -> None:
        """渲染到指定坐标，extrinsic state 通过参数传入"""
        print(f"渲染 {self.species} 树于 ({x:.1f}, {y:.1f})，缩放 {scale:.2f}")


class TreeFactory:
    """
    Flyweight Factory：按 (species, height) 键管理树的实例池
    相同种类和高度的树共享同一个 Flyweight 对象
    """
    _pool: dict[tuple, TreeFlyweight] = {}

    @classmethod
    def get_tree(cls, species: str, height: float) -> TreeFlyweight:
        key = (species, height)
        if key not in cls._pool:
            print(f"[Factory] 创建新 Flyweight: {species}({height}m)")
            texture = b"\xff" * 1024   # 模拟 1KB 纹理数据
            cls._pool[key] = TreeFlyweight(species, texture, height)
        return cls._pool[key]

    @classmethod
    def pool_info(cls) -> None:
        print(f"Flyweight 池：{len(cls._pool)} 种树")


# 演示：地图上放置 10000 棵树，但只有 3 种不同的 Flyweight
import random

map_trees = []   # extrinsic state：每棵树的具体坐标
for _ in range(10_000):
    species = random.choice(["松树", "桦树", "橡树"])
    height  = random.choice([5.0, 10.0, 15.0])
    x, y    = random.uniform(0, 1000), random.uniform(0, 1000)

    flyweight = TreeFactory.get_tree(species, height)
    map_trees.append((flyweight, x, y))   # 存引用（8 字节），而非完整对象

TreeFactory.pool_info()
print(f"地图上 {len(map_trees)} 棵树，Flyweight 对象仅 {len(TreeFactory._pool)} 个")

# 测量内存节省
sample_fw = TreeFactory.get_tree("松树", 10.0)
print(f"Flyweight 对象大小（无 __dict__）: {sys.getsizeof(sample_fw)} 字节")
```

#### `enum.Enum` 成员 — 天然的 Flyweight

Python 的 `enum.Enum` 保证每个成员在整个进程生命周期中只有一个实例，是 Flyweight 思想的内置实现：

```python
from enum import Enum, unique
import sys

@unique
class Direction(Enum):
    """方向枚举：每个成员是单例 Flyweight"""
    NORTH = "N"
    SOUTH = "S"
    EAST  = "E"
    WEST  = "W"

# 无论通过名称还是值获取，都是同一个对象
d1 = Direction.NORTH
d2 = Direction["NORTH"]
d3 = Direction("N")

print(d1 is d2)    # True
print(d2 is d3)    # True
print(id(d1) == id(d3))  # True

# 枚举成员可以携带方法（intrinsic behavior）
class Color(Enum):
    RED   = (255, 0,   0)
    GREEN = (0,   255, 0)
    BLUE  = (0,   0,   255)

    def __init__(self, r: int, g: int, b: int):
        self.r = r
        self.g = g
        self.b = b

    def to_hex(self) -> str:
        return f"#{self.r:02X}{self.g:02X}{self.b:02X}"

print(Color.RED.to_hex())    # #FF0000
print(Color.RED is Color.RED)  # True（单例）
```

#### 用 `tracemalloc` 量化内存节省

```python
import tracemalloc
import sys

tracemalloc.start()

# 方案 A：不使用 Flyweight，每个"字符"独立持有字形数据
class CharWithoutFlyweight:
    def __init__(self, c: str):
        self.char    = c
        self.font_data = c * 500   # 模拟 500 字节的字形数据

snapshot_a_start = tracemalloc.take_snapshot()
chars_a = [CharWithoutFlyweight(chr(65 + i % 26)) for i in range(10_000)]
snapshot_a_end = tracemalloc.take_snapshot()

# 方案 B：使用 Flyweight，字形数据只存 26 份
class CharFlyweight:
    __slots__ = ("char", "font_data")
    def __init__(self, c: str):
        self.char      = c
        self.font_data = c * 500

_flyweight_pool: dict[str, CharFlyweight] = {}
def get_char_flyweight(c: str) -> CharFlyweight:
    if c not in _flyweight_pool:
        _flyweight_pool[c] = CharFlyweight(c)
    return _flyweight_pool[c]

snapshot_b_start = tracemalloc.take_snapshot()
chars_b = [get_char_flyweight(chr(65 + i % 26)) for i in range(10_000)]
snapshot_b_end = tracemalloc.take_snapshot()

def diff_kb(s1, s2):
    total = sum(stat.size for stat in s2.compare_to(s1, "lineno"))
    return total / 1024

print(f"不用 Flyweight：增加约 {diff_kb(snapshot_a_start, snapshot_a_end):.1f} KB")
print(f"用 Flyweight：  增加约 {diff_kb(snapshot_b_start, snapshot_b_end):.1f} KB")
# 典型输出：不用约 5000 KB，用约 15 KB（300+ 倍差距）
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 缓存触发 | `valueOf()` 工厂方法约定；编译器 `new` vs `valueOf` 区分 | 解释器透明缓存；`sys.intern()` 手动触发 |
| 内存节省手段 | 对象池（`IntegerCache`、`POOL` Map） | `__slots__`（消除 `__dict__`）+ 对象池 |
| 不可变保证 | `final` 字段 + `record`；`@ValueBased` 语义约定 | `__slots__` + 不提供 setter；`frozenset` 等不可变类型 |
| 身份比较陷阱 | `==` 对包装类不可靠，必须用 `equals()` | `is` 对大整数不可靠，必须用 `==` |
| 枚举即 Flyweight | `enum` 类型（Java 5+），每个常量是单例 | `enum.Enum`，行为完全相同 |
| 工厂线程安全 | `ConcurrentHashMap.computeIfAbsent()` | GIL 提供基础保护，高并发仍需 `threading.Lock` |
| 典型应用场景 | 字符串常量池、整数缓存、连接池、字体渲染 | 枚举状态、大数据行对象、游戏实体、NLP token |

**关键差异**：Java 的包装类缓存是*语言规范*的一部分——你不能绕过它，也不能修改缓存范围（除非用 JVM 参数 `-XX:AutoBoxCacheMax`）。Python 的小整数缓存是*CPython 实现细节*——PyPy 等其他实现可能有不同边界，因此 Python 代码**绝对不应**依赖 `is` 来比较整数值。

```java
// Java：包装类永远用 equals()，这是规范要求
Integer a = 1000, b = 1000;
assert a.equals(b);   // 正确
// assert a == b;     // 错误：行为取决于缓存范围，不可预期
```

```python
# Python：整数值比较永远用 ==，is 仅用于 None / enum 成员
a, b = 1000, 1000
assert a == b    # 正确
# assert a is b  # 错误：仅在小整数范围内成立，是实现细节
assert a is None or a is not None   # is 用于 None 判断是惯用法
```

---

## 动手练习

**19.1 Java — 棋盘棋子的 Flyweight**

中国象棋棋盘上，同一方的"车"可以多次出现（残局中可能有两个车）。但相同颜色、相同棋子类型的图形数据是完全一样的。

实现 `ChessPiece` Flyweight 和 `ChessPieceFactory`：
- Intrinsic state：棋子类型（车/马/炮…）、颜色（红/黑）、图形数据（用字符串模拟）
- Extrinsic state：棋子在棋盘上的行列坐标
- 工厂确保相同类型+颜色的棋子只有一个 `ChessPiece` 实例

```java
interface ChessPiece {
    void render(int row, int col);   // extrinsic: 坐标
    String type();                   // intrinsic: 棋子类型名
}
// 用 record 实现 ChessPiece，确保不可变
// 用 Map<String, ChessPiece> 实现工厂池
```

**19.2 Java — 验证 `Integer` 缓存边界**

写一段程序，通过循环找出 `Integer.valueOf()` 缓存的精确边界（不要硬编码 -128/127，让程序自己发现）：

```java
// 提示：比较 Integer.valueOf(i) == Integer.valueOf(i) 的结果
// 找到第一个返回 false 的正整数和负整数
```

**19.3 Python — 用 `__slots__` 优化粒子系统**

实现一个粒子系统（Particle System），每帧可能有数十万个粒子：

```python
class ParticleFlyweight:
    """Intrinsic：粒子类型的图形数据、物理参数"""
    __slots__ = ("texture", "mass", "drag_coefficient")
    ...

# Extrinsic state 存在独立的结构中（列表/数组），不存在 Flyweight 对象内
# 用 sys.getsizeof() 对比有无 __slots__ 的内存差异
```

**19.4 思考题 — Intrinsic vs Extrinsic 的划分**

给定以下场景，判断哪些属性应该是 intrinsic state（存在 Flyweight 里），哪些是 extrinsic state（调用时传入）：

1. 网页浏览器渲染文字：字形轮廓、字体大小、字符颜色、在页面上的坐标
2. 地图应用渲染 POI 图标：图标图片、POI 名称、经纬度坐标、是否被用户收藏
3. 游戏里的子弹：子弹图形、飞行速度、当前位置、当前速度向量

---

## 回顾与连接

**三种"共享"模式的本质区分**:

- **Flyweight vs Singleton (Ch05)**: Singleton 保证整个系统中某个类*只有一个*实例；Flyweight 维护一个*实例池*，相同 key 对应一个实例，不同 key 可以有不同实例。Singleton 是 Flyweight 池大小为 1 的极端情况。`Boolean.TRUE` / `Boolean.FALSE` 同时体现了两个模式——只有两个实例，各自是该"值"的单例。

- **Flyweight vs Prototype (Ch11)**: Prototype 通过*克隆*来快速创建新对象——每次使用都是独立副本，可自由修改；Flyweight 则*拒绝创建副本*——相同 key 永远返回同一个对象，共享同一份 intrinsic state。选择标准：如果使用方需要独立修改对象，用 Prototype；如果对象状态可以安全共享（不可变），用 Flyweight。

- **Flyweight vs Cache（缓存）**: Flyweight 缓存的是*对象本身*（为了节省内存、避免重复初始化）；普通缓存（如 `@Cacheable`）缓存的是*计算结果*（为了避免重复计算）。前者是结构模式，后者是性能优化手段——两者经常同时出现。

**设计要点**:

1. **Intrinsic state 必须不可变**: 多个 Client 共享同一个 Flyweight 实例，如果 intrinsic state 可变，一个 Client 的修改会影响所有其他 Client。Java 用 `final` 字段和 `record` 强制不可变；Python 用 `__slots__` + 约定或 `@property` 只读属性。

2. **Extrinsic state 不能存在 Flyweight 里**: 如果把坐标存进 `BigChar`，那字符串 `"1212123"` 里的两个 `"1"` 就不能共享同一个 `BigChar` 对象了——这违背了 Flyweight 的核心原则。判断一个状态是否是 extrinsic 的标准：不同调用方（或同一调用方的不同调用）对这个状态有不同需求吗？有 → extrinsic，调用时传入。

3. **工厂是关键**: 直接 `new` Flyweight 会破坏共享——每次 `new` 都是新对象。Flyweight 工厂（Factory 或 `valueOf()` 风格的静态工厂方法）是确保共享的唯一入口。这也是为什么 `new Integer(5)` 被 Java 9 废弃——它绕过了缓存。

4. **线程安全的工厂**: 多线程环境下，Flyweight 工厂的 `pool.get()` + `pool.put()` 不是原子操作，可能导致同一 key 创建多个实例（破坏共享语义）或数据竞争。Java 用 `ConcurrentHashMap.computeIfAbsent()` 解决；Python 在 GIL 保护下的简单 dict 操作是安全的，但 `if key not in pool: pool[key] = ...` 在释放 GIL 的场景（如 I/O 密集）仍需加锁。

5. **不要过度应用**: 只有在*大量*细粒度对象且*大部分状态可共享*时，Flyweight 才值得引入——它的代价是设计复杂度上升（状态分离、工厂必须存在）。如果对象数量只有几百个，直接 `new` 清晰得多。
