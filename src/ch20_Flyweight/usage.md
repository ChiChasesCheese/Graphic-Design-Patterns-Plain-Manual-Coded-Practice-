# Flyweight 模式 — 真实应用

核心：**共享大量细粒度对象的公共状态，只存储一份，节省内存。**

---

## 1. Java — `Integer.valueOf()` 缓存

JVM 对 -128 ~ 127 的 Integer 做了 Flyweight：这个范围内的对象只创建一次，全局共享。

```java
Integer a = Integer.valueOf(100);
Integer b = Integer.valueOf(100);
System.out.println(a == b);   // true  ← 同一个对象（Flyweight）

Integer c = Integer.valueOf(200);
Integer d = Integer.valueOf(200);
System.out.println(c == d);   // false ← 超出缓存范围，不同对象

// String 也是 Flyweight：字符串字面量从常量池取，不重复创建
String s1 = "hello";
String s2 = "hello";
System.out.println(s1 == s2); // true ← 同一个常量池对象
```

---

## 2. React — 虚拟 DOM 与 Fiber 复用

React 的 reconciliation 复用已有 DOM 节点，避免重复创建，是 Flyweight 思想在渲染层的体现。

```typescript
// React key 告诉 reconciler 哪些节点是"同一个"，可以复用（共享）
function UserList({ users }: { users: User[] }) {
    return (
        <ul>
            {users.map(user => (
                <UserItem key={user.id} user={user} />
                //         ↑ key 是 Flyweight 的标识
                // React 用 key 判断：这个 DOM 节点能复用吗？
                // 复用 = 不销毁旧节点、不创建新节点，只更新属性（节省内存和 GC）
            ))}
        </ul>
    );
}

// React 内部 Fiber 对象池（概念简化）
// 已完成渲染的 Fiber 节点不销毁，放入对象池
// 下次渲染时从池中取出复用，减少 GC 压力
```

---

## 3. 游戏引擎 — 粒子系统

游戏里成千上万的粒子（子弹、火花、雨滴），共享纹理和网格数据，只有位置/速度不同。

```typescript
// Three.js / 游戏引擎粒子系统（简化）
class ParticleSystem {
    // Flyweight：共享的不变状态（内在状态）
    private sharedTexture: THREE.Texture;
    private sharedGeometry: THREE.BufferGeometry;
    private sharedMaterial: THREE.Material;

    // 每个粒子独有的状态（外在状态）——只存这些，节省大量内存
    private positions: Float32Array;   // x,y,z * N
    private velocities: Float32Array;
    private lifetimes: Float32Array;

    constructor(count: number) {
        // 纹理/几何体只创建一次，所有粒子共享
        this.sharedTexture  = new THREE.TextureLoader().load('spark.png');
        this.sharedGeometry = new THREE.PlaneGeometry(0.1, 0.1);
        // 存储 N 个粒子的独有状态
        this.positions  = new Float32Array(count * 3);
        this.velocities = new Float32Array(count * 3);
    }

    // 渲染时用 InstancedMesh：一次 draw call 渲染所有粒子
    render() {
        // GPU instancing：共享几何体，每个实例只传位置矩阵
    }
}
```

---

## 4. Python — `sys.intern` / 小整数缓存

Python 解释器内置多种 Flyweight 优化。

```python
import sys

# 小整数缓存（CPython 实现，通常 -5 ~ 256）
a = 100
b = 100
print(a is b)   # True ← 同一个对象

a = 1000
b = 1000
print(a is b)   # False ← 超出缓存范围

# 字符串驻留（intern）：相同字符串共享对象
s1 = sys.intern("frequently_used_key")
s2 = sys.intern("frequently_used_key")
print(s1 is s2)  # True ← 同一个对象，节省内存 + 比较更快（指针比较 vs 逐字符）

# 实际应用：大量重复字符串的场景（配置 key、数据库字段名）
import pandas as pd
df = pd.read_csv("large_file.csv")
# pandas 的 Categorical 类型用 Flyweight 存储重复字符串
df['status'] = df['status'].astype('category')  # "active"/"inactive" 只存一次
```

---

## 5. Font Rendering — 字形缓存

字体渲染系统是 Flyweight 的经典应用：
每个字符的字形（glyph）只渲染一次，缓存后所有相同字符复用。

```typescript
// 浏览器字体渲染（概念）/ 游戏文字渲染
class GlyphCache {
    private cache = new Map<string, RenderedGlyph>();

    getGlyph(char: string, fontFamily: string, fontSize: number): RenderedGlyph {
        const key = `${char}:${fontFamily}:${fontSize}`;  // Flyweight key

        if (!this.cache.has(key)) {
            // 第一次：渲染字形（开销大）
            this.cache.set(key, this.renderGlyph(char, fontFamily, fontSize));
        }
        // 之后：直接返回缓存（0 开销）
        return this.cache.get(key)!;
    }
}

// 渲染一段文字：每个字符从缓存取字形，只传位置（外在状态）
function renderText(text: string, x: number, y: number) {
    let cursorX = x;
    for (const char of text) {
        const glyph = glyphCache.getGlyph(char, 'Arial', 16);  // 共享字形（内在状态）
        drawGlyph(glyph, cursorX, y);  // 位置是外在状态，不共享
        cursorX += glyph.advance;
    }
}
```

---

## Python 生态

Python 用 `__slots__` 减少实例内存，`sys.intern` 共享字符串，`weakref` 实现可回收的共享池。

```python
import sys
import weakref
from dataclasses import dataclass

# 1. __slots__ — 减少实例内存占用
class PointWithDict:
    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y
    # 默认每个实例有 __dict__（字典开销 ~240 字节）

class PointWithSlots:
    __slots__ = ("x", "y")   # 用固定结构替代 __dict__（节省 ~50-70% 内存）
    def __init__(self, x: float, y: float):
        self.x = x
        self.y = y

# 内存对比
p1 = PointWithDict(1.0, 2.0)
p2 = PointWithSlots(1.0, 2.0)
print(sys.getsizeof(p1.__dict__))   # ~232 字节（字典开销）
# p2 没有 __dict__，节省大量内存

# 大量细粒度对象时效果显著
points_dict  = [PointWithDict(i, i) for i in range(100_000)]
points_slots = [PointWithSlots(i, i) for i in range(100_000)]
# points_slots 内存占用约为 points_dict 的 50%

# 2. sys.intern — 字符串 Flyweight
# Python 会自动 intern 看起来像标识符的字符串字面量
a = "hello_world"
b = "hello_world"
print(a is b)   # True（自动 intern）

# 运行时生成的字符串不会自动 intern
a = "hello" + "_world"
b = "hello" + "_world"
print(a is b)   # False（不同对象）

a = sys.intern("hello" + "_world")
b = sys.intern("hello" + "_world")
print(a is b)   # True（手动 intern，共享同一对象）

# 实际应用：大量重复的配置 key、数据库字段名
field_names = [sys.intern(f"field_{i % 10}") for i in range(10_000)]
# 只有 10 个唯一字符串对象，无论列表多长

# 3. weakref — 可回收的 Flyweight 池
class Color:
    _pool: weakref.WeakValueDictionary = weakref.WeakValueDictionary()

    def __new__(cls, r: int, g: int, b: int):
        key = (r, g, b)
        obj = cls._pool.get(key)
        if obj is None:
            obj = super().__new__(cls)
            obj.r, obj.g, obj.b = r, g, b
            cls._pool[key] = obj   # 弱引用：无其他引用时自动回收
        return obj

red1 = Color(255, 0, 0)
red2 = Color(255, 0, 0)
print(red1 is red2)   # True（共享同一对象）

del red1
del red2
# 此时 Color(255,0,0) 无强引用，被 GC 回收，WeakValueDictionary 自动清理

# 4. pandas Categorical — 大数据 Flyweight
import pandas as pd

df = pd.DataFrame({
    "user_id": range(1_000_000),
    "status": ["active", "inactive", "pending"] * 333334,   # 大量重复值
})

# 普通 object 列：每个字符串独立存储
print(df["status"].memory_usage(deep=True))   # ~大量内存

# Categorical：每个唯一值只存一份（Flyweight）
df["status"] = df["status"].astype("category")
print(df["status"].memory_usage(deep=True))   # 大幅减少
print(df["status"].cat.categories)            # ['active', 'inactive', 'pending']
```

> **Python 洞察**：`__slots__` 是 Python 手动优化内存的最直接方式——
> 当你需要创建数百万个小对象（点、粒子、记录），总是考虑用 `__slots__`。
> `pandas` 的 `Categorical` 类型是 Flyweight 的生产实践：把重复字符串转为整数索引，内存可减少 10x+。

---

## 关键洞察

> Flyweight 的核心思想：把对象状态分成**内在状态（不变，可共享）** 和 **外在状态（可变，不共享）**。
> 识别信号：当你创建大量相似对象，且这些对象有大量重复数据时，
> 把重复数据提取出来共享，只保留差异化的部分。
> 现代应用：对象池、连接池、线程池——本质都是 Flyweight 思想。
