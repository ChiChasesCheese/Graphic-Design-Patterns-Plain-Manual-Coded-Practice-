# Chapter 23: Interpreter

> 类型: 行为 | 难度: ★★★ | 原书: `src/ch23_Interpreter/` | 前置: Ch12 (Composite), Ch22 (Visitor)

---

## 模式速览

**问题**: 你需要让用户通过配置文件或注解描述业务规则——比如权限表达式 `hasRole('ADMIN') or userId == me`、缓存 key 表达式 `#userId + ':' + #status`、或是一门简单的脚本语言。如果把这些规则硬编码进业务逻辑，每次改规则都要重新部署。Interpreter 模式的解法是：**为这门"迷你语言"定义语法，把每条语法规则映射为一个类，再递归组合成抽象语法树（AST），最后由树本身解释执行**。

```
         «interface»
            Node
         ┌──────────────────┐
         │ parse(Context)   │
         │ interpret()      │
         └────────┬─────────┘
                  │
     ┌────────────┴──────────────┐
     │                           │
TerminalExpression        NonterminalExpression
（叶节点 / 终结符）          （复合节点 / 非终结符）
┌──────────────────┐      ┌──────────────────────────┐
│ PrimitiveCommand │      │ ProgramNode              │
│ interpret()      │      │ - children: List<Node>   │
│  直接执行        │      │ interpret() ← 递归委托   │
└──────────────────┘      └──────────────────────────┘
```

**四个核心角色**:

- `AbstractExpression`（`Node`）— 抽象节点，声明 `interpret()` 或 `parse()` 接口，所有语法规则都实现它
- `TerminalExpression` — 终结符节点（叶节点），对应语法中不可再分的最小单元，如数字字面量、变量名、原始命令
- `NonterminalExpression` — 非终结符节点（复合节点），对应语法中由其他规则组成的规则，如加法表达式由左右两个子表达式构成
- `Context` — 上下文，携带解释过程中的全局状态，如变量表、符号流、执行环境

**原书示例**: 教科书实现了一门迷你编程语言，语法为 `program ... end` 包裹命令序列，`repeat N ... end` 表示循环，`right`/`left`/`go`/`stop` 是原始命令。`ProgramNode`、`RepeatCommandNode` 是非终结符，`PrimitiveCommandNode` 是终结符，`Context` 持有待解析的 token 流。

---

## 本章新语言特性 — CAPSTONE 章

本章是全书最后一个设计模式章节，也是语言特性的综合运用顶点。

| 特性 | Java | Python |
|------|------|--------|
| 代数数据类型 | `sealed interface` + `record`（全书汇总） | `dataclass` + `match`（全书汇总） |
| 穷举模式匹配 | `switch` 模式匹配，漏分支编译错误 | `match/case`，漏分支仅警告 |
| 解构绑定 | `case Add(var l, var r)` 直接解构 record 字段 | `case Add(left=l, right=r)` 解构 dataclass |
| 递归 AST 求值 | 方法在节点类内，或外置 `eval()` 函数 | 独立函数 + `match`，天然函数式风格 |
| 标准库 Interpreter | `java.util.regex`、`javax.script`、SpEL | `re`、`ast`、`string.Template` |

### Java：`sealed` + `record` + `switch` 三位一体

`sealed interface` 约束 AST 节点的合法子类集合，`record` 一行声明不可变节点，`switch` 模式匹配在求值时同时完成类型分派和字段解构——三者配合，让 AST 定义和解释代码紧凑到极限：

```java
// sealed 声明：Expr 的所有子类在编译期已知
// 编译器可以强制 switch 穷举所有分支，漏掉任何一支都是编译错误
sealed interface Expr permits Num, Add, Mul, Var {}

// record 一行 = 字段声明 + 构造器 + equals/hashCode/toString
record Num(double value)            implements Expr {}
record Add(Expr left, Expr right)   implements Expr {}
record Mul(Expr left, Expr right)   implements Expr {}
record Var(String name)             implements Expr {}

// switch 模式匹配：类型检查 + 字段解构合并在一步
static double eval(Expr expr, Map<String, Double> env) {
    return switch (expr) {
        case Num(var v)        -> v;                           // 解构 value 字段
        case Add(var l, var r) -> eval(l, env) + eval(r, env); // 递归左右子树
        case Mul(var l, var r) -> eval(l, env) * eval(r, env);
        case Var(var name)     -> env.getOrDefault(name, 0.0); // 查符号表
    };
}
```

### Python：`match/case` 递归求值

Python 的 `match` 语句用 class pattern 匹配 dataclass 实例，配合递归函数实现同样的 AST 求值逻辑——代码量相当，但分派逻辑外置在函数中而不是分散在各个节点类里：

```python
from dataclasses import dataclass
from typing import Union

@dataclass
class Num:   value: float
@dataclass
class Add:   left: "Expr"; right: "Expr"
@dataclass
class Mul:   left: "Expr"; right: "Expr"
@dataclass
class Var:   name: str

Expr = Union[Num, Add, Mul, Var]

def evaluate(expr: Expr, env: dict[str, float]) -> float:
    match expr:
        case Num(value=v):         return v
        case Add(left=l, right=r): return evaluate(l, env) + evaluate(r, env)
        case Mul(left=l, right=r): return evaluate(l, env) * evaluate(r, env)
        case Var(name=n):          return env.get(n, 0.0)
```

---

## Java 实战: `java.util.regex` + SpEL + 计算器 DSL

### `java.util.regex.Pattern` — 正则引擎就是 Interpreter

正则表达式是 Interpreter 模式最广泛的工业应用：正则语法定义了一门"模式描述语言"，`Pattern.compile()` 把字符串解析成 NFA/DFA（AST 的执行形式），`Matcher` 解释执行这棵 AST 对输入进行匹配：

```java
import java.util.regex.*;

// compile = 词法分析 + 语法分析 + 构建内部 AST（NFA）
// 这一步代价较高，应复用 Pattern 对象
Pattern datePattern = Pattern.compile(
    "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})"  // 具名捕获组 = AST 中的变量绑定
);

// matcher.find() = Interpreter 执行 NFA，遍历输入字符串
Matcher m = datePattern.matcher("会议时间：2025-12-25，截止：2026-03-01");
while (m.find()) {
    // 命名捕获组让字段提取有语义
    System.out.printf("年=%s 月=%s 日=%s%n",
        m.group("year"), m.group("month"), m.group("day"));
}
// 输出：
// 年=2025 月=12 日=25
// 年=2026 月=03 日=01

// 正则本身就是一段程序：分组、量词、断言都是语法节点
// (?=...) 前瞻断言，(?<=...) 后瞻断言，(a|b) 选择节点
Pattern emailCheck = Pattern.compile(
    "^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$"
);
boolean valid = emailCheck.matcher("user@example.com").matches();
```

### Spring Expression Language (SpEL) — 生产级 Interpreter

SpEL 是 Spring 内置的表达式语言，它的核心就是一个完整的 Interpreter：`ExpressionParser` 解析表达式字符串构建 AST，`Expression.getValue()` 解释执行：

```java
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;
import org.springframework.expression.spel.support.*;

// 编程式使用 SpEL Interpreter
ExpressionParser parser = new SpelExpressionParser();
StandardEvaluationContext ctx = new StandardEvaluationContext();

// Interpreter 解析并执行：字符串拼接
ctx.setVariable("name", "Alice");
Expression expr1 = parser.parseExpression("'Hello, ' + #name + '!'");
System.out.println(expr1.getValue(ctx, String.class));  // Hello, Alice!

// 复杂表达式：调用 Java 方法、三元运算符
Expression expr2 = parser.parseExpression(
    "#name.length() > 3 ? #name.toUpperCase() : 'SHORT'"
);
System.out.println(expr2.getValue(ctx, String.class));  // ALICE

// 注解中的 SpEL — Spring 在启动时 parse + cache，每次调用时 evaluate
// @PreAuthorize：权限规则 DSL
// @Cacheable：cache key 表达式 DSL
// 这两处的字符串都是完整的 SpEL 程序
```

### 现代 Capstone：完整计算器 DSL

将所有特性综合，构建一个支持变量和四则运算的完整小型语言：

```java
import java.util.*;

// ── AST 节点定义 ──────────────────────────────────────────────
sealed interface Expr permits Num, Add, Sub, Mul, Div, Var {}

record Num(double value)            implements Expr {}
record Add(Expr left, Expr right)   implements Expr {}
record Sub(Expr left, Expr right)   implements Expr {}
record Mul(Expr left, Expr right)   implements Expr {}
record Div(Expr left, Expr right)   implements Expr {}
record Var(String name)             implements Expr {}

// ── Interpreter：递归求值 ─────────────────────────────────────
static double eval(Expr expr, Map<String, Double> env) {
    return switch (expr) {
        case Num(var v)        -> v;
        case Var(var n)        -> env.getOrDefault(n, 0.0);
        case Add(var l, var r) -> eval(l, env) + eval(r, env);
        case Sub(var l, var r) -> eval(l, env) - eval(r, env);
        case Mul(var l, var r) -> eval(l, env) * eval(r, env);
        case Div(var l, var r) -> eval(l, env) / eval(r, env);  // 未处理除零，保持示例简洁
    };
}

// ── Pretty Printer：同一 AST，不同操作 ───────────────────────
static String pretty(Expr expr) {
    return switch (expr) {
        case Num(var v)        -> String.valueOf(v);
        case Var(var n)        -> n;
        case Add(var l, var r) -> "(" + pretty(l) + " + " + pretty(r) + ")";
        case Sub(var l, var r) -> "(" + pretty(l) + " - " + pretty(r) + ")";
        case Mul(var l, var r) -> "(" + pretty(l) + " * " + pretty(r) + ")";
        case Div(var l, var r) -> "(" + pretty(l) + " / " + pretty(r) + ")";
    };
}

// ── 使用示例 ─────────────────────────────────────────────────
public static void main(String[] args) {
    // 手动构建 AST：(x + 2) * (y - 1)
    Expr ast = new Mul(
        new Add(new Var("x"), new Num(2)),
        new Sub(new Var("y"), new Num(1))
    );

    var env = Map.of("x", 3.0, "y", 5.0);

    System.out.println(pretty(ast));    // ((x + 2.0) * (y - 1.0))
    System.out.println(eval(ast, env)); // 20.0（(3+2)*(5-1)）
}
```

注意 `eval` 和 `pretty` 是两个完全独立的函数，都接受同一棵 `Expr` 树——这正是将操作外置（而不是分散在节点类内部）的好处：新增一种操作只需新增一个函数，无需改动任何节点类。

---

## Python 实战: `re` + `ast` 模块 + 计算器 DSL

### `re` 模块 — Python 的正则 Interpreter

```python
import re

# re.compile = 解析正则语法，构建内部 NFA/DFA（AST 的编译产物）
# 正则字符串本身是一段程序：字符类、量词、分组都是语法节点
LOG_PATTERN = re.compile(
    r"(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})"
    r"\s+\[(?P<level>INFO|WARN|ERROR)\]"
    r"\s+(?P<message>.+)"
)

log_line = "2025-12-25 10:30:00 [ERROR] 数据库连接超时"
m = LOG_PATTERN.match(log_line)
if m:
    # 命名捕获组让字段提取具有语义，等价于 AST 中的变量绑定
    print(m.group("level"),    ":",  m.group("message"))
    # ERROR : 数据库连接超时
```

### `ast` 模块 — 操作 Python 自身的 AST

Python 的 `ast` 标准模块让你直接操作 Python 源代码的语法树，这是元编程和代码分析工具的基础：

```python
import ast

source = """
def compound_interest(p: float, r: float, n: int) -> float:
    return p * (1 + r) ** n - p
"""

# ast.parse = 词法分析 + 语法分析，得到完整 AST
tree = ast.parse(source)

# NodeVisitor：Visitor 模式遍历 AST（Interpreter + Visitor 的结合）
class FunctionAnalyzer(ast.NodeVisitor):
    """统计函数中每种二元运算符的出现次数"""

    def __init__(self):
        # 运算符名 → 出现次数
        self.ops: dict[str, int] = {}

    def visit_BinOp(self, node: ast.BinOp) -> None:
        op_name = type(node.op).__name__     # Mult、Add、Pow 等
        self.ops[op_name] = self.ops.get(op_name, 0) + 1
        self.generic_visit(node)             # 继续递归子节点

analyzer = FunctionAnalyzer()
analyzer.visit(tree)
print(analyzer.ops)   # {'Mult': 1, 'Add': 1, 'Pow': 1, 'Sub': 1}

# NodeTransformer：修改 AST——常量折叠优化
class ConstantFolder(ast.NodeTransformer):
    """将可在编译期计算的常量表达式折叠为单一常量：2 * 3 → 6"""

    def visit_BinOp(self, node: ast.BinOp) -> ast.AST:
        self.generic_visit(node)             # 先递归处理子节点
        if isinstance(node.left, ast.Constant) and isinstance(node.right, ast.Constant):
            try:
                # 安全求值：在受控环境中解释执行这个子树
                result = eval(compile(ast.Expression(node), "<fold>", "eval"))
                return ast.Constant(value=result)
            except Exception:
                pass
        return node

# ast.literal_eval：安全地解释执行 Python 字面量（无代码执行风险）
config = ast.literal_eval("{'host': 'localhost', 'port': 5432, 'debug': True}")
print(config["port"])  # 5432
```

### 现代 Capstone：带变量的计算器 DSL

```python
from __future__ import annotations
from dataclasses import dataclass
from typing import Union

# ── AST 节点定义 ─────────────────────────────────────────────
@dataclass
class Num:   value: float
@dataclass
class Add:   left: Expr; right: Expr
@dataclass
class Sub:   left: Expr; right: Expr
@dataclass
class Mul:   left: Expr; right: Expr
@dataclass
class Div:   left: Expr; right: Expr
@dataclass
class Var:   name: str

Expr = Union[Num, Add, Sub, Mul, Div, Var]

# ── Interpreter：递归求值 ─────────────────────────────────────
def evaluate(expr: Expr, env: dict[str, float]) -> float:
    match expr:
        case Num(value=v):         return v
        case Var(name=n):          return env.get(n, 0.0)
        case Add(left=l, right=r): return evaluate(l, env) + evaluate(r, env)
        case Sub(left=l, right=r): return evaluate(l, env) - evaluate(r, env)
        case Mul(left=l, right=r): return evaluate(l, env) * evaluate(r, env)
        case Div(left=l, right=r): return evaluate(l, env) / evaluate(r, env)

# ── Pretty Printer：同一 AST，另一种操作 ─────────────────────
def pretty(expr: Expr) -> str:
    match expr:
        case Num(value=v): return str(v)
        case Var(name=n):  return n
        case Add(left=l, right=r): return f"({pretty(l)} + {pretty(r)})"
        case Sub(left=l, right=r): return f"({pretty(l)} - {pretty(r)})"
        case Mul(left=l, right=r): return f"({pretty(l)} * {pretty(r)})"
        case Div(left=l, right=r): return f"({pretty(l)} / {pretty(r)})"

# ── 使用示例 ─────────────────────────────────────────────────
# 构建 AST：(x + 2) * (y - 1)
ast_tree = Mul(
    Add(Var("x"), Num(2)),
    Sub(Var("y"), Num(1))
)

env = {"x": 3.0, "y": 5.0}

print(pretty(ast_tree))         # ((x + 2) * (y - 1))
print(evaluate(ast_tree, env))  # 20.0
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 节点约束 | `sealed interface` 限定合法子类，编译期穷举检查 | `Union[...]` 类型注解，mypy 可选验证 |
| 节点声明 | `record` 一行，自动生成所有 boilerplate | `@dataclass`，接近同等简洁 |
| 求值分派 | `switch` 模式匹配，漏分支是编译错误 | `match/case`，漏分支静默返回 `None` |
| 操作组织 | 可放节点内（OOP），也可外置函数（函数式） | 天然倾向外置函数 + `match` |
| 标准库 Interpreter | `java.util.regex`、`javax.script`、SpEL | `re`、`ast`、`string.Template`、`lark` |
| 典型适用场景 | 编译器前端、金融规则引擎（正确性优先） | 数据处理脚本、代码分析工具、快速 DSL 原型 |

Java 的 `sealed` 把"有哪些节点类型"编码进类型系统，编译器帮你检查遗漏——在构建编译器或金融规则引擎时，这种静态保障价值巨大。Python 的 `match` 把分派逻辑集中在一个函数里，添加新操作无需触碰节点类，更适合频繁迭代的分析脚本。

两者最终都汇聚到同一个本质：**把语言规则映射为类层次结构，通过递归组合表达任意复杂的句子，再用多态分派解释执行**。这是本书 23 个模式中结构最完整、技术密度最高的一个——它同时是 Composite（树结构）、Visitor（对树的操作外置）、和 Strategy（可替换的解释策略）的综合体现。

---

## 动手练习

**23.1 Java — 布尔表达式语言**

用 `sealed interface` + `record` + `switch` 构建一个迷你布尔表达式语言：

```
BoolExpr := True | False | Var(name) | And(left, right) | Or(left, right) | Not(expr)
```

- 定义 `sealed interface BoolExpr`，各节点实现为 `record`
- 实现 `boolean eval(BoolExpr expr, Map<String, Boolean> env)`
- 实现 `String toSql(BoolExpr expr)`：将 AST 转译为 SQL WHERE 子句字符串（`AND`、`OR`、`NOT`）
- 测试：`And(Or(Var("a"), Var("b")), Not(Var("c")))` 在 `{a:true, b:false, c:false}` 下求值，并输出对应 SQL

**23.2 Python — 布尔表达式语言**

实现与 23.1 等价的 Python 版本：

```python
@dataclass
class And:  left: BoolExpr; right: BoolExpr
@dataclass
class Or:   left: BoolExpr; right: BoolExpr
@dataclass
class Not:  expr: BoolExpr
```

- 实现 `evaluate(expr, env) -> bool` 和 `to_sql(expr) -> str`
- 额外挑战：实现 `simplify(expr) -> BoolExpr`，执行简单的逻辑化简：`And(True, x) → x`、`Or(False, x) → x`、`Not(Not(x)) → x`

**23.3 思考题 — Interpreter vs 函数式求值**

本章展示的 `eval()` 函数其实可以不用 Interpreter 模式——直接用 Lambda 或高阶函数也能求值：

```java
// 把表达式直接编码为 Java 函数，不需要 AST
Function<Map<String,Double>, Double> expr =
    env -> (env.get("x") + 2) * (env.get("y") - 1);
```

思考：什么情况下应该用 Interpreter（构建 AST）？什么情况下直接用高阶函数更好？（提示：考虑序列化、调试、运行时动态生成、跨语言传输等需求。）

---

## 回顾与连接

**Interpreter = Composite + Visitor 的语言学视角**

这是本书最后一个 GoF 模式，它不引入新的结构原理——它把已经学过的模式提升到语言实现的层面：

- **与 Composite (Ch12) 的关系**: AST 就是 Composite 树。`TerminalExpression` 是叶节点，`NonterminalExpression` 是复合节点，`interpret()` 是递归操作。区别仅在视角：Composite 强调"统一叶和容器的接口"，Interpreter 强调"把语法规则映射为类层次"。
- **与 Visitor (Ch22) 的关系**: 当 AST 节点类型稳定、操作种类频繁变化时（如求值、类型检查、代码生成、优化……），用 Visitor 把操作外置。本章的 `eval()` 和 `pretty()` 就是两个隐式的 Visitor——它们在 AST 上做不同操作，却不需要修改任何节点类。
- **与 Strategy (Ch02) 的关系**: 可替换的 Interpreter 就是 Strategy——同一棵 AST，传入不同的 `eval` 实现，就得到不同的执行语义（求值、类型推断、转译到 SQL……）。

**适用场景的三个信号**:

1. 用户需要用**配置而不是代码**描述业务规则（权限、过滤条件、计算公式）
2. 语言的语法**相对简单且稳定**（规则类型不超过十几种）
3. 需要对同一段逻辑做**多种操作**（求值、序列化、可视化、优化）

**何时不用 Interpreter**:

语法复杂时（如完整的 SQL、Java），不要手写——用 ANTLR（Java）、Lark（Python）、PEG.js（JS）等工具从 EBNF 语法自动生成 Parser。理解 Interpreter 模式的更大价值在于**读懂这些工具的输出和框架源码**，知道它们生成的 AST 为什么长成那个样子。

---

> **全书收尾**: 23 个 GoF 模式，覆盖了软件设计中"对象如何创建"（创建型）、"对象如何组合"（结构型）、"对象如何协作"（行为型）三个维度。Interpreter 是行为型的压轴——它把语言本身作为设计对象，用对象的层次结构表达语法规则，用递归调用表达语义，完美呼应了 Composite 的结构递归和 Visitor 的操作外置。掌握 Interpreter，意味着你已经具备阅读编译器前端、DSL 框架和规则引擎源码的基础认知框架。
