# Chapter 12: Composite

> 类型: 结构 | 难度: ★★☆ | 原书: `src/ch11_Composite/` | 前置: Ch01 (Iterator)

---

## 模式速览

**问题**: 你在构建一个文件系统浏览器。文件夹里既可以放文件，也可以放文件夹。你想用同一段代码递归计算"总大小"——不管遇到的是文件还是文件夹，都调用 `size()`。但如果用 `if (node instanceof File) ... else if (node instanceof Directory) ...` 来区分，每次添加新节点类型都要修改所有遍历代码，完全违反开闭原则。

Composite 模式的解决方案是：**让叶节点和容器节点实现同一个接口**，容器节点在自身操作之外把操作递归委托给所有子节点。调用方面对统一的接口，永远不需要区分叶和容器。

```
         «interface»
          Component
         ┌──────────────┐
         │ size(): int  │
         │ print(int)   │
         └──────┬───────┘
                │
       ┌────────┴──────────┐
       │                   │
      Leaf               Composite
   ┌──────────┐        ┌────────────────────┐
   │ File     │        │ Directory          │
   │ size()   │        │ - children: List   │
   │ print()  │        │ size()  ← 递归求和 │
   └──────────┘        │ print() ← 递归打印 │
                       │ add(Component)     │
                       └────────────────────┘
```

**三个角色**:
- `Component` — 统一接口，叶节点和容器节点都实现它，调用方只依赖此接口
- `Leaf` — 叶节点，没有子节点，直接实现操作的基础语义（如 `File.size()` 返回自身大小）
- `Composite` — 容器节点，持有一组 `Component` 子节点，实现操作时递归调用所有子节点的同名方法

**核心洞察**: Composite 中的容器节点也实现了 `Component` 接口，因此树可以任意深度嵌套——目录里放目录，目录里放文件，调用方用同一套代码处理任意深度的树，无需知道自己走到了哪一层。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 代数数据类型 | `sealed interface` + `record` | `dataclass` + `match` |
| 求和类型 | `sealed` 约束子类穷举 | `X \| Y` 类型联合 |
| 模式匹配 | `instanceof` 模式（Java 16+）/ `switch` 模式（Java 21+） | `match` 语句（Python 3.10+） |
| 不可变节点 | `record` 自动生成构造器和 `equals` | `@dataclass(frozen=True)` |

### `sealed interface` — Java 的受控继承

`sealed` 关键字（Java 17 正式版）声明一个接口只允许指定的子类实现它。配合 `record`，可以用极少的代码建模类型安全的树结构：

```java
// sealed 限定子类：编译器知道 Expr 只能是 Num、Add 或 Mul
// 这使得 switch 模式匹配可以做穷举检查——漏掉任何一支都是编译错误
sealed interface Expr permits Num, Add, Mul {
    int eval();
}

// record：一行声明 = 字段 + 构造器 + equals/hashCode/toString
record Num(int value)             implements Expr {
    public int eval() { return value; }
}
record Add(Expr left, Expr right) implements Expr {
    public int eval() { return left.eval() + right.eval(); }
}
record Mul(Expr left, Expr right) implements Expr {
    public int eval() { return left.eval() * left.eval(); }
}
```

`sealed` 的关键价值：编译器可以在 `switch` 中强制要求穷举所有子类，漏掉任何一支都是编译错误，消灭了遗漏分支的运行时 bug。

### Python `match` 语句基础（Python 3.10+）

`match` 语句对值的结构进行匹配，天然适合树形递归——每个节点按类型分派，子树递归处理：

```python
from dataclasses import dataclass

@dataclass
class Num:
    value: int

@dataclass
class Add:
    left: "Expr"
    right: "Expr"

# 类型联合：X | Y 声明联合类型（Python 3.10+）
Expr = Num | Add

def evaluate(expr: Expr) -> int:
    match expr:
        case Num(value=v):          # 匹配 Num，绑定 value 字段到 v
            return v
        case Add(left=l, right=r):  # 匹配 Add，绑定 left 和 right
            return evaluate(l) + evaluate(r)
```

`match` 的结构匹配（structural pattern matching）不仅检查类型，还能同时解构字段——相当于 `isinstance` 检查加上字段提取，比 `if/elif` 链更简洁、不易出错。

---

## Java 实战: `java.awt` 组件树 / `java.nio.file` 文件树

### 源码解析

**AWT 组件树** 是 Java 标准库中最早的 Composite 实现：

- `java.awt.Component` — Component 角色，定义 `paint(Graphics g)` 等统一接口
- `java.awt.Container extends Component` — Composite 角色，持有 `Component[]` 子数组，`paint()` 递归调用所有子组件的 `paint()`
- `java.awt.Button`、`java.awt.Label` — Leaf 角色，直接绘制自身

```java
// AWT 的 Composite 结构（简化自 JDK 源码）
// Container.paint() 内部等价于：
public void paint(Graphics g) {
    // 先绘制容器自身背景
    super.paint(g);
    // 再递归绘制所有子组件——调用方不需要知道子组件是 Leaf 还是 Container
    for (Component child : getComponents()) {
        child.paint(g);   // 多态分派，Container 和 Button 都实现此方法
    }
}
```

**`java.nio.file.Files.walk()`** — 文件系统本身就是 Composite，`Files.walk()` 把深度优先遍历封装成 `Stream<Path>`：

```java
import java.nio.file.*;
import java.io.IOException;

// Files.walk 递归遍历目录树，文件（叶）和目录（容器）统一用 Path 表示
// 调用方用同一个 filter/map/forEach 处理，完全不区分两者
try (var paths = Files.walk(Path.of("/var/log"))) {
    paths
        .filter(Files::isRegularFile)                      // 只要叶节点（文件）
        .filter(p -> p.toString().endsWith(".log"))
        .mapToLong(p -> {
            try { return Files.size(p); }
            catch (IOException e) { return 0L; }
        })
        .sum();                                            // 递归求和，等价于 Directory.size()
}
```

递归计算目录大小——展示 Composite 递归语义：

```java
import java.nio.file.*;

// 手动递归版：直接体现 Composite 的"容器委托子节点"结构
static long directorySize(Path path) throws IOException {
    if (Files.isRegularFile(path)) {
        return Files.size(path);          // Leaf：直接返回自身大小
    }
    // Composite：对每个子节点递归调用同一方法，再求和
    try (var children = Files.list(path)) {
        return children
            .mapToLong(child -> {
                try { return directorySize(child); }
                catch (IOException e) { return 0L; }
            })
            .sum();
    }
}
```

### 现代写法：`sealed interface` + `record` 建模表达式树

用 Java 21 的特性将文件系统节点建模为类型安全的代数数据类型：

```java
import java.util.List;

// sealed 限定：FileNode 只能是 RegularFile 或 Dir，编译器可穷举
sealed interface FileNode permits RegularFile, Dir {
    String name();
    long size();
    void print(int indent);                         // 递归打印树形结构
}

// Leaf：普通文件，没有子节点
record RegularFile(String name, long size) implements FileNode {
    public void print(int indent) {
        System.out.println(" ".repeat(indent * 2) + name + " (" + size + " B)");
    }
}

// Composite：目录，持有子节点列表
record Dir(String name, List<FileNode> children) implements FileNode {
    public long size() {
        // 递归委托：容器的 size = 所有子节点 size 之和
        return children.stream().mapToLong(FileNode::size).sum();
    }

    public void print(int indent) {
        System.out.println(" ".repeat(indent * 2) + "[" + name + "] (" + size() + " B)");
        // 递归打印所有子节点，叶和容器统一处理
        for (var child : children) {
            child.print(indent + 1);
        }
    }
}

// switch 模式匹配：sealed 保证穷举，漏掉任何分支都是编译错误
static String describe(FileNode node) {
    return switch (node) {
        case RegularFile f -> "文件: " + f.name() + ", " + f.size() + " 字节";
        case Dir d         -> "目录: " + d.name() + ", 共 " + d.children().size() + " 个子节点";
    };
}

// 构建测试树并遍历
public static void main(String[] args) {
    var tree = new Dir("project", List.of(
        new RegularFile("README.md", 2048),
        new Dir("src", List.of(
            new RegularFile("Main.java", 4096),
            new RegularFile("Utils.java", 1024)
        )),
        new Dir("test", List.of(
            new RegularFile("MainTest.java", 2048)
        ))
    ));

    tree.print(0);
    // [project] (9216 B)
    //   README.md (2048 B)
    //   [src] (5120 B)
    //     Main.java (4096 B)
    //     Utils.java (1024 B)
    //   [test] (2048 B)
    //     MainTest.java (2048 B)
}
```

`record Dir` 的构造器参数是 `List<FileNode>`——这是不可变快照语义。如果需要动态 `add()`，改用 `ArrayList` 并将 `children` 声明为普通字段而非 `record` 组件。

---

## Python 实战: `pathlib.Path` / `xml.etree.ElementTree` / Django Q 对象

### 源码解析

**`pathlib.Path`** — 标准库中最自然的 Composite：

```python
from pathlib import Path

# Path 对象统一代表文件（叶）和目录（容器），接口完全一致
root = Path(".")

# iterdir() 只遍历直接子节点（广度一层）
for item in root.iterdir():
    if item.is_dir():
        # 容器：递归统计总大小
        total = sum(f.stat().st_size for f in item.rglob("*") if f.is_file())
        print(f"DIR  {item.name}/  {total:,} 字节")
    else:
        # 叶：直接读取大小
        print(f"FILE {item.name}  {item.stat().st_size:,} 字节")

# rglob 把递归遍历封装成扁平迭代器——Composite 树对调用方透明
all_py = list(root.rglob("*.py"))
print(f"共 {len(all_py)} 个 .py 文件")
```

**`xml.etree.ElementTree`** — XML 文档天然是 Composite，`Element` 既可以是叶（只含文本），也可以是容器（含子 `Element`）：

```python
import xml.etree.ElementTree as ET

xml_src = """
<root>
    <section title="第一章">
        <para>段落一</para>
        <para>段落二</para>
    </section>
    <section title="第二章">
        <para>段落三</para>
    </section>
</root>
"""

root = ET.fromstring(xml_src)

# 递归提取所有文本——调用方不区分叶和容器
def extract_text(element: ET.Element, depth: int = 0) -> None:
    indent = "  " * depth
    if element.text and element.text.strip():
        print(f"{indent}{element.text.strip()}")          # 叶节点：输出文本
    for child in element:                                  # 容器节点：递归子节点
        extract_text(child, depth + 1)

extract_text(root)
```

**Django Q 对象** — 查询条件的 Composite，`Q` 对象可以像树一样组合，`|`（OR）和 `&`（AND）运算符返回新的 `Q` 节点，ORM 最终递归将整棵树翻译成 SQL：

```python
from django.db.models import Q

# 叶节点：单条件
active   = Q(is_active=True)
adult    = Q(age__gte=18)
verified = Q(email_verified=True)

# 容器节点：用运算符组合，返回新 Q（Composite 节点）
# 等价于 SQL: WHERE (is_active=1 AND age>=18) OR email_verified=1
query = (active & adult) | verified

# ORM 递归遍历 Q 树，生成 SQL——调用方无需了解内部树结构
User.objects.filter(query)
```

### Pythonic 重写：`match` 语句处理表达式树

```python
from __future__ import annotations
from dataclasses import dataclass

# 用 dataclass 定义树节点——配合 match 使用
@dataclass
class Num:
    value: int

@dataclass
class Add:
    left:  Expr
    right: Expr

@dataclass
class Mul:
    left:  Expr
    right: Expr

# 类型联合（Python 3.10+）：明确声明 Expr 是哪些类型的联合
Expr = Num | Add | Mul

def evaluate(expr: Expr) -> int:
    """递归求值——match 按节点类型分派，结构解构同步完成"""
    match expr:
        case Num(value=v):
            return v                              # 叶节点：直接返回值
        case Add(left=l, right=r):
            return evaluate(l) + evaluate(r)      # 容器：递归左右子树
        case Mul(left=l, right=r):
            return evaluate(l) * evaluate(r)

def pretty(expr: Expr, depth: int = 0) -> str:
    """递归打印表达式树的缩进结构"""
    pad = "  " * depth
    match expr:
        case Num(value=v):
            return f"{pad}Num({v})"
        case Add(left=l, right=r):
            return f"{pad}Add\n{pretty(l, depth+1)}\n{pretty(r, depth+1)}"
        case Mul(left=l, right=r):
            return f"{pad}Mul\n{pretty(l, depth+1)}\n{pretty(r, depth+1)}"

# 构建表达式树：(1 + 2) * (3 + 4)
tree = Mul(Add(Num(1), Num(2)), Add(Num(3), Num(4)))

print(evaluate(tree))   # 21
print(pretty(tree))
# Mul
#   Add
#     Num(1)
#     Num(2)
#   Add
#     Num(3)
#     Num(4)
```

`match/case` 的结构匹配让树遍历代码的意图一目了然：每个 `case` 精确对应一种节点类型，字段绑定和类型检查合并在一行完成，没有嵌套的 `if isinstance` 干扰阅读。

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 类型约束 | `sealed interface` 限定子类集合，编译器强制穷举 | `X \| Y` 类型联合，类型检查器（mypy）可选验证 |
| 节点定义 | `record` 一行声明不可变节点 | `@dataclass` 或 `@dataclass(frozen=True)` |
| 树遍历分派 | `switch` 模式匹配（Java 21+），漏分支编译错误 | `match` 语句，漏分支仅运行时产生 `None` |
| 递归写法 | 方法在节点类内部定义，`child.method()` 多态调用 | 独立函数 + `match`，分派逻辑集中在一处 |
| 可变 vs 不可变 | `record` 不可变；可变版本用普通类 + `List.add()` | `dataclass` 默认可变；`frozen=True` 不可变 |
| 典型场景 | AWT/Swing 组件树、表达式树、AST | pathlib、ElementTree、Django Q、AST 处理 |

**核心差异**: Java 的 `sealed` 把"有哪些子类"编码进类型系统，编译器和 IDE 可以检测遗漏分支，适合对正确性要求高的场合（编译器、金融计算）。Python 的 `match` 把分派逻辑写在函数里而不是类里，更灵活，添加新操作无需修改节点类，适合快速迭代和数据处理场景。

两者都体现了 Composite 的本质：**用递归接口把树的复杂性封装起来，让调用方写出不感知层数的代码**。

---

## 动手练习

**12.1 Java** — 用 `sealed interface` + `record` 实现一个 JSON 树：
- `JsonNode` 是 sealed interface，子类为 `JsonNull`、`JsonBool`、`JsonNum`、`JsonStr`、`JsonArr`（含 `List<JsonNode>`）、`JsonObj`（含 `Map<String, JsonNode>`）
- 实现 `int depth(JsonNode node)`：返回树的最大深度，叶节点深度为 0
- 实现 `String stringify(JsonNode node)`：递归序列化回 JSON 字符串

**12.2 Python** — 实现一个简单的 HTML 构建器：
- `HtmlNode` 基类（或 dataclass），子类 `TextNode`（叶）和 `Element`（容器，含 `tag`、`attrs` 和 `children` 列表）
- `render(node, indent=0) -> str`：递归生成缩进的 HTML 字符串
- 用 `match` 语句区分叶节点和容器节点

**12.3 思考题** — Composite 与递归的边界：
- 如果树中出现循环引用（节点 A 的子节点包含 A 自身），递归遍历会无限循环。请思考如何检测并防止循环引用（提示：遍历时维护一个"已访问"集合）。
- Composite 模式要求叶节点也实现 `add()`/`remove()` 吗？不实现会怎样？实现了又有什么问题？（提示：这是"透明性"和"安全性"的经典权衡。）

---

## 回顾与连接

**与相关模式的区别**:

- **Composite vs Decorator (Ch09)**: 两者都递归持有同类型引用。区别在于**意图和结构**：Decorator 是**线性链**，每个节点恰好包含一个子节点，目的是添加行为；Composite 是**分叉树**，容器节点包含任意数量子节点，目的是统一叶和容器的接口。用一句话区分——Decorator 问"要给它加什么功能？"，Composite 问"它有哪些子节点？"

- **Composite vs Iterator (Ch01)**: Iterator 提供遍历 Composite 树的**游标**，两者天然配合——为 `Directory` 实现 `Iterator<FileNode>`，调用方可以用 `for-each` 循环遍历整棵树，而不需要关心递归细节。Java 中 `Iterable<T>` 接口是连接两个模式的标准纽带。

- **Composite vs Visitor (Ch22)**: Composite 把操作定义在节点类**内部**（如 `size()`、`print()`）；Visitor 把操作**外置**到独立的 Visitor 类中，节点只负责 `accept(visitor)` 调用。当需要频繁添加新操作但节点类型稳定时，Visitor 优于在 Composite 节点上堆方法。两者常常配合出现：Composite 定义树结构，Visitor 定义对树的各种操作。

- **Composite vs Interpreter (Ch23)**: Interpreter 是 Composite 的特化——表达式树本身就是 Composite，`interpret()` 方法就是 Composite 的递归操作。区别在于 Interpreter 强调"将语言规则编码为类层次结构"的语言学视角，而 Composite 强调"统一叶和容器接口"的结构视角。

**设计要点**:

1. **Component 接口精简**: 接口方法越多，Leaf 实现的"无意义"方法就越多（如 `File.add()` 永远抛异常）。优先只放叶和容器都真正需要的方法。
2. **透明性 vs 安全性**: 把 `add()`/`remove()` 放在 `Component` 接口上——调用方可以统一处理，但编译期无法阻止对叶节点调用 `add()`（透明但不安全）。只放在 `Composite` 上——编译期安全，但调用方有时需要向下转型（安全但不透明）。无绝对答案，按场景取舍。
3. **子节点顺序**: `List` 保留顺序（DOM、AWT），`Set` 去重无序（权限组），按业务需求选择容器类型。
4. **`sealed` + `record` 是现代 Java 的首选**: 比传统抽象类层次更简洁，且编译器辅助穷举检查能在树遍历代码中提前发现缺失分支。
