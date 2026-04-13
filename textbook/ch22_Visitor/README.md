# Chapter 22: Visitor

> 类型: 行为 | 难度: ★★★ | 原书: `src/ch13_Visitor/` | 前置: Ch12 (Composite), Ch01 (Iterator)

---

## 模式速览

**问题**: 你有一棵文件系统树——`File` 和 `Directory` 两种节点。现在需要对整棵树做很多不同的操作：列出所有文件、统计总大小、查找特定扩展名的文件、生成报表……如果把每种操作都塞进 `File` 和 `Directory` 类，这两个类会越来越臃肿。更糟糕的是：每次增加新操作，都要修改已经稳定的类。

Visitor 模式的解决方案是：**把"操作"从"数据结构"中完全分离**，把每一种操作封装成一个独立的 Visitor 类。数据结构中的每个节点提供一个 `accept(Visitor)` 方法，用来"接受"访问者的到来。

```
    «abstract»                    «interface»
     Visitor                       Element
  ┌───────────────────┐         ┌──────────────────┐
  │ visit(File)       │         │ accept(Visitor v) │
  │ visit(Directory)  │         └────────┬─────────┘
  └────────┬──────────┘                  │
           │                    ┌────────┴────────┐
    ┌──────┴──────┐             │                 │
    │             │            File           Directory
 ListVisitor  FindVisitor   ┌────────┐      ┌──────────────┐
 ┌──────────┐ ┌──────────┐  │accept()│      │accept()      │
 │visit(f)  │ │visit(f)  │  │  v.visit(this)│  v.visit(this)│
 │visit(d)  │ │visit(d)  │  └────────┘      │  遍历子节点   │
 └──────────┘ └──────────┘                  └──────────────┘
```

**双分派（Double Dispatch）** 是 Visitor 的核心机制，也是它难以理解的原因：

1. 第一次分派：`entry.accept(visitor)` — 根据 `entry` 的运行时类型，决定调用 `File.accept` 还是 `Directory.accept`
2. 第二次分派：`visitor.visit(this)` — 在 `accept` 内部，根据 `visitor` 的运行时类型，决定调用哪个 `visit` 重载

两次多态调用共同确定了最终执行的操作。这就是"双分派"——Java 等单分派语言必须用这个技巧才能根据**两个**对象的类型来选择行为。

**四个角色**：
- `Visitor` — 抽象访问者，为每种 Element 类型声明一个 `visit` 重载
- `ConcreteVisitor` — 具体访问者，实现针对每种 Element 的具体操作（如 `ListVisitor`）
- `Element` — 被访问的接口，声明 `accept(Visitor)` 方法
- `ConcreteElement` — 具体节点（如 `File`、`Directory`），实现 `accept` 时调用 `visitor.visit(this)`

**核心权衡**：

| 操作 | 难易程度 |
|------|---------|
| 增加新操作（新 Visitor） | 容易 — 新建一个类，不动已有代码 |
| 增加新节点类型（新 Element） | 困难 — 必须修改所有已有 Visitor |

当元素类型稳定、操作种类频繁扩展时，Visitor 是正确的选择。反之则不然。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 密封类型 | `sealed interface` + `permits`（Java 17+） | `match`/`case` 结构匹配（Python 3.10+） |
| 模式匹配 | `switch` 表达式 + 解构模式（Java 21+） | `case ClassName(field=val)` 类模式 |
| Record 解构 | `case Add(var l, var r)` | `case Add(left=l, right=r)` |

### `sealed` + `switch` — 让 Visitor 过时的现代 Java

传统 Visitor 需要双分派是因为 Java 是单分派语言——`switch` 只能根据一个值的类型分发。Java 21 引入的**密封接口 + switch 模式匹配**直接解决了这个问题：

```java
// sealed 声明穷举的子类集合，permits 后列出所有允许的实现
// 编译器掌握完整类型信息 → switch 可以做穷举检查
sealed interface Expr permits Num, Add, Mul {}

// record 自动生成构造器、equals、hashCode、toString
// record 的字段可以在 switch 的 case 中直接解构
record Num(int value)             implements Expr {}
record Add(Expr left, Expr right) implements Expr {}
record Mul(Expr left, Expr right) implements Expr {}
```

有了 `sealed` + `record`，不需要 `accept`/`visit` 机制，直接用 `switch` 做类型分发：

```java
// 求值操作：不需要 Visitor，直接 switch
static int eval(Expr expr) {
    return switch (expr) {
        case Num(var v)         -> v;                       // 解构 record 字段
        case Add(var l, var r)  -> eval(l) + eval(r);       // 递归求值
        case Mul(var l, var r)  -> eval(l) * eval(r);
        // 漏掉任何一个 case → 编译错误，不会有运行时漏网
    };
}

// 打印操作：同样是 switch，独立于 eval，不修改节点类
static String pretty(Expr expr) {
    return switch (expr) {
        case Num(var v)         -> String.valueOf(v);
        case Add(var l, var r)  -> "(%s + %s)".formatted(pretty(l), pretty(r));
        case Mul(var l, var r)  -> "(%s * %s)".formatted(pretty(l), pretty(r));
    };
}

// 使用
var e = new Add(new Mul(new Num(2), new Num(3)), new Num(4));
System.out.println(pretty(e));  // ((2 * 3) + 4)
System.out.println(eval(e));    // 10
```

`sealed` 的关键价值：**编译器知道所有子类**，所以 `switch` 可以强制穷举——漏掉 `case Mul` 是编译错误，而不是运行时的 `ClassCastException` 或漏处理的节点。这正是 Visitor 模式努力要保证的类型安全，现在由语言本身提供。

### Python `match`/`case` — 结构模式匹配

Python 3.10 的 `match` 语句支持**类模式**，可以同时匹配类型和解构字段：

```python
from dataclasses import dataclass

@dataclass
class Num:
    value: int

@dataclass
class Add:
    left: "Expr"
    right: "Expr"

@dataclass
class Mul:
    left: "Expr"
    right: "Expr"

# 求值：match 替代 visitor.visit()，无需 accept()
def eval_expr(expr) -> int:
    match expr:
        case Num(value=v):
            return v
        case Add(left=l, right=r):  # 类模式：同时检查类型 + 解构字段
            return eval_expr(l) + eval_expr(r)
        case Mul(left=l, right=r):
            return eval_expr(l) * eval_expr(r)
        case _:
            raise TypeError(f"未知节点类型: {type(expr)}")

# 打印操作：另一个函数，不修改节点类
def pretty(expr) -> str:
    match expr:
        case Num(value=v):          return str(v)
        case Add(left=l, right=r):  return f"({pretty(l)} + {pretty(r)})"
        case Mul(left=l, right=r):  return f"({pretty(l)} * {pretty(r)})"
        case _:                     raise TypeError(f"未知节点: {type(expr)}")
```

---

## Java 实战: FileVisitor 与 ElementVisitor

### `java.nio.file.FileVisitor` — 遍历文件树

`Files.walkFileTree()` 是 Java 标准库中最典型的 Visitor 应用。文件树的节点类型（目录、文件）是稳定的，而遍历时"做什么"是可扩展的：

```java
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

// 具体 Visitor：收集所有 .java 文件
class JavaFileFinder extends SimpleFileVisitor<Path> {

    private final List<Path> javaFiles = new ArrayList<>();

    // 访问文件时调用（对应 visit(File)）
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.toString().endsWith(".java")) {
            javaFiles.add(file);
        }
        return FileVisitResult.CONTINUE;  // 继续遍历
    }

    // 进入目录前调用（对应 visit(Directory) 的前半段）
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        // 跳过隐藏目录和 build 输出目录
        var name = dir.getFileName().toString();
        if (name.startsWith(".") || name.equals("out") || name.equals("target")) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    // 离开目录后调用（对应 visit(Directory) 的后半段）
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }

    List<Path> getJavaFiles() { return Collections.unmodifiableList(javaFiles); }
}

// 使用：只需更换 Visitor 就能改变遍历行为，不动文件树本身
var finder = new JavaFileFinder();
Files.walkFileTree(Path.of("src"), finder);
finder.getJavaFiles().forEach(System.out::println);
```

`FileVisitResult` 的返回值体现了 Visitor 的另一个优点：访问者可以**控制遍历流程**（继续、跳过子树、终止）而不必修改遍历逻辑本身。

### `javax.lang.model.element.ElementVisitor` — 注解处理

Java 编译期注解处理器（APT）用 Visitor 遍历源码的语法元素。Lombok、Dagger、Room 等工具都基于此机制：

```java
import javax.lang.model.element.*;
import javax.lang.model.util.SimpleElementVisitor14;

// 具体 Visitor：收集所有带 @Override 方法的名字
class OverrideMethodCollector extends SimpleElementVisitor14<Void, List<String>> {

    // 访问方法元素
    @Override
    public Void visitExecutable(ExecutableElement e, List<String> result) {
        boolean hasOverride = e.getAnnotationMirrors().stream()
            .anyMatch(a -> a.getAnnotationType().asElement()
                           .getSimpleName().contentEquals("Override"));
        if (hasOverride) {
            result.add(e.getSimpleName().toString());
        }
        return null;
    }

    // 访问类型元素（类/接口），递归进入其成员
    @Override
    public Void visitType(TypeElement e, List<String> result) {
        for (Element enclosed : e.getEnclosedElements()) {
            enclosed.accept(this, result);  // accept → visit，双分派在此发生
        }
        return null;
    }
}
```

这里的双分派一目了然：`enclosed.accept(this, result)` 先由 `enclosed` 的运行时类型（方法？字段？内部类？）决定调用哪个 `accept`，再由 `this`（`OverrideMethodCollector`）决定调用哪个 `visit` 重载。

### 传统双分派 vs 现代 sealed + switch

```java
// ===== 传统 Visitor 实现（双分派）=====

interface Element { void accept(Visitor v); }
abstract class Visitor {
    abstract void visit(File f);
    abstract void visit(Directory d);
}

class File implements Element {
    final String name;
    final int size;
    File(String name, int size) { this.name = name; this.size = size; }

    @Override
    public void accept(Visitor v) { v.visit(this); }  // 第二次分派
    @Override public String toString() { return name + "(" + size + ")"; }
}

class Directory implements Element {
    final String name;
    final List<Element> children = new ArrayList<>();
    Directory(String name) { this.name = name; }
    Directory add(Element e) { children.add(e); return this; }

    @Override
    public void accept(Visitor v) { v.visit(this); }  // 第二次分派
    @Override public String toString() { return name + "/"; }
}

// 第一个 Visitor：列出所有条目
class ListVisitor extends Visitor {
    private String indent = "";

    @Override
    public void visit(File f) {
        System.out.println(indent + f);
    }

    @Override
    public void visit(Directory d) {
        System.out.println(indent + d);
        var saved = indent;
        indent += "  ";
        for (var child : d.children) child.accept(this);  // 第一次分派
        indent = saved;
    }
}

// 增加新操作：只需新建 Visitor 类，File 和 Directory 零改动
class SizeVisitor extends Visitor {
    int total = 0;

    @Override public void visit(File f)      { total += f.size; }
    @Override public void visit(Directory d) { for (var c : d.children) c.accept(this); }
}
```

```java
// ===== 现代替代方案（sealed + switch）=====

sealed interface Entry permits FileEntry, DirEntry {}
record FileEntry(String name, int size) implements Entry {}
record DirEntry(String name, List<Entry> children) implements Entry {}

// 操作 1：列出条目（函数，不是类）
static void list(Entry e, String indent) {
    switch (e) {
        case FileEntry(var n, var s)    -> System.out.println(indent + n + "(" + s + ")");
        case DirEntry(var n, var kids)  -> {
            System.out.println(indent + n + "/");
            kids.forEach(k -> list(k, indent + "  "));
        }
    }
}

// 操作 2：求总大小（增加操作 = 增加函数，Entry 不变）
static int totalSize(Entry e) {
    return switch (e) {
        case FileEntry(_, var s)       -> s;
        case DirEntry(_, var kids)     -> kids.stream().mapToInt(Main::totalSize).sum();
    };
}
```

两种方案对比：

| 维度 | 传统 Visitor（双分派） | sealed + switch |
|------|----------------------|----------------|
| 增加新操作 | 新建 Visitor 类 | 新建函数 |
| 增加新节点类型 | 修改所有 Visitor | 修改所有 switch（编译器报错提示） |
| 类型安全 | 运行时（漏写 visit 重载 → 继承默认行为） | 编译期（漏写 case → 编译错误） |
| 代码量 | 多（接口 + 抽象类 + 具体类） | 少（sealed record + 函数） |
| 适用场景 | 旧版 Java、需要有状态 Visitor | Java 21+，新代码首选 |

---

## Python 实战: ast.NodeVisitor 与 pathlib.Path.walk

### `ast.NodeVisitor` — 访问 Python 自身的 AST

Python 标准库 `ast` 模块内置了经典 GoF Visitor 实现。每个 AST 节点类型对应一个 `visit_NodeType` 方法：

```python
import ast

# 具体 Visitor：统计每种函数调用的出现次数
class CallCounter(ast.NodeVisitor):

    def __init__(self):
        self.counts: dict[str, int] = {}

    def visit_Call(self, node: ast.Call):
        # 提取调用的函数名
        match node.func:
            case ast.Name(id=name):
                key = name
            case ast.Attribute(attr=attr, value=ast.Name(id=obj)):
                key = f"{obj}.{attr}"
            case _:
                key = "<complex>"

        self.counts[key] = self.counts.get(key, 0) + 1
        self.generic_visit(node)  # 继续递归遍历子节点

    def visit_FunctionDef(self, node: ast.FunctionDef):
        # 进入函数定义时打印名字
        print(f"[函数] {node.name}，共 {len(node.body)} 条语句")
        self.generic_visit(node)  # 必须手动调用，否则不会遍历函数体内的节点


source = """
import os

def process(path):
    files = os.listdir(path)
    result = []
    for f in files:
        full = os.path.join(path, f)
        if os.path.isfile(full):
            result.append(full)
    print(len(result))
    return result
"""

tree = ast.parse(source)
counter = CallCounter()
counter.visit(tree)
print(counter.counts)
# {'os.listdir': 1, 'os.path.join': 1, 'os.path.isfile': 1, 'result.append': 1, 'print': 1, 'len': 1}
```

### `ast.NodeTransformer` — 变换而非只读

`NodeTransformer` 是 `NodeVisitor` 的子类，`visit_*` 方法返回新节点，可以修改 AST：

```python
import ast

# Transformer：把所有 print() 调用替换为 logging.debug()
class PrintToLogging(ast.NodeTransformer):

    def visit_Call(self, node: ast.Call):
        self.generic_visit(node)  # 先递归处理子节点
        # 检查是否是 print(...)
        if isinstance(node.func, ast.Name) and node.func.id == "print":
            # 替换为 logging.debug(...)
            node.func = ast.Attribute(
                value=ast.Name(id="logging", ctx=ast.Load()),
                attr="debug",
                ctx=ast.Load(),
            )
        return node  # 返回修改后的节点（返回 None 表示删除该节点）


source = "print('hello'); x = 1; print(x + 2)"
tree = ast.parse(source)
new_tree = PrintToLogging().visit(tree)
ast.fix_missing_locations(new_tree)
print(ast.unparse(new_tree))
# logging.debug('hello'); x = 1; logging.debug(x + 2)
```

### `pathlib.Path.walk()` — Python 3.12 的文件树遍历

Python 3.12 引入 `Path.walk()`，类似 `os.walk` 但更 Pythonic。与 Java 的 `FileVisitor` 不同，它用生成器而非 Visitor 接口：

```python
from pathlib import Path

def find_large_files(root: Path, min_size_kb: int = 100) -> list[Path]:
    """找出所有超过 min_size_kb 的文件"""
    large = []
    for dirpath, dirnames, filenames in root.walk():
        # 原地修改 dirnames 可以跳过子目录（类似 FileVisitResult.SKIP_SUBTREE）
        dirnames[:] = [d for d in dirnames
                       if not d.startswith(".") and d not in {"node_modules", "target"}]
        for name in filenames:
            path = dirpath / name
            if path.stat().st_size > min_size_kb * 1024:
                large.append(path)
    return sorted(large, key=lambda p: p.stat().st_size, reverse=True)
```

### `dis` 模块 — 字节码访问者

Python 的 `dis` 模块是另一个 Visitor 模式的体现——遍历函数的字节码指令序列：

```python
import dis

def mystery(n: int) -> int:
    result = 0
    for i in range(n):
        result += i * i
    return result

# dis.get_instructions 返回 Instruction 对象的迭代器（隐式 Visitor）
for instr in dis.get_instructions(mystery):
    if instr.opname in ("LOAD_FAST", "STORE_FAST"):
        print(f"{instr.offset:4d}  {instr.opname:<20} {instr.argval}")
```

---

## 两种哲学

传统 Visitor 诞生于 1994 年——那时 Java 还没有模式匹配，C++ 没有 `std::visit`，语言本身无法根据运行时类型安全地分发到多个操作。双分派是当时的最优解。

三十年后，现代语言用语言特性直接解决了同一个问题：

**Java 的答案**：`sealed interface` 告诉编译器"子类是穷举的"，`switch` 模式匹配在编译期验证所有分支都处理了。新增操作 = 新建函数，类型安全由编译器保证，不需要 `accept`/`visit` 的机械双分派。

**Python 的答案**：`match`/`case` 的类模式可以同时匹配类型和解构字段，`functools.singledispatch` 实现基于类型的函数分派。两者都让"根据节点类型做不同操作"成为直接的语言表达，而不是需要绕弯子的设计模式。

**结论**：在新代码中，如果使用 Java 21+ 或 Python 3.10+，优先考虑 sealed + switch / match + case。经典 Visitor 双分派仍然有价值的场景：

- 维护旧版 Java（< 21）的代码库
- Visitor 本身需要携带可变状态（如 `ListVisitor` 中追踪当前目录路径）
- 需要外部插件动态注册新操作（如 Babel 插件系统），此时 Visitor 作为接口比 switch 更灵活

---

## 动手练习

**表达式树求值器 + 格式化打印机**

构建一个支持加减乘除和变量的表达式树，用两种方式实现两个操作：

目标结构：`Num | Add | Sub | Mul | Div | Var`

操作 1 — `eval(expr, env: Map<String, Integer>)`：对表达式求值，`Var` 从环境中查变量值，除以零时抛出异常。

操作 2 — `pretty(expr)`：格式化输出，`Add(Mul(Num(2), Var("x")), Num(1))` → `"(2 * x + 1)"`（乘除优先级高于加减，自动省略不必要的括号）。

**方式 A**：传统 Visitor 双分派（适用 Java 8+）
- `Visitor` 抽象类，`EvalVisitor` 和 `PrettyVisitor` 两个具体实现
- `EvalVisitor` 用栈保存中间结果，`PrettyVisitor` 用栈保存字符串片段

**方式 B**：sealed + switch（Java 21+）或 dataclass + match（Python）
- 节点为 `sealed record`（Java）或 `@dataclass`（Python）
- `eval` 和 `pretty` 是普通静态方法/函数，不需要任何 Visitor 基础设施

完成后对比两种实现：增加一种新操作（如 `countOps` 统计运算符数量）各需要修改哪些文件？增加一种新节点类型（如 `Neg` 取反）各需要修改哪些地方？

---

## 回顾与连接

| 模式 | 与 Visitor 的关系 |
|------|------------------|
| Composite（Ch12） | Visitor 最常见的搭档——对 Composite 树进行操作时，`Directory.accept` 负责递归遍历子节点，Visitor 负责在每个节点上执行操作 |
| Iterator（Ch01） | Iterator 提供顺序遍历，Visitor 提供类型感知的双分派访问。`ListVisitor` 内部用 Iterator 遍历 `Directory` 的子节点 |
| Interpreter（Ch23） | Interpreter 是 Composite（树）+ Visitor（求值）的组合。语法树的 `interpret()` 方法等价于一个内置的 EvalVisitor |
| Strategy（Ch02） | Strategy 封装算法，Visitor 封装对数据结构的操作。区别：Strategy 替换单个方法，Visitor 针对整个类型层次结构定义一组操作 |
| Command（Ch13） | Command 封装请求对象，可以用 Visitor 遍历一系列 Command 对象并分析或重组它们 |

**一句话总结**：Visitor 把操作从数据结构中抽离，代价是固化了元素类型；现代语言的 sealed + switch / match/case 用更少的代码达到同样效果，同时让编译器而非运行时来保证类型安全。
