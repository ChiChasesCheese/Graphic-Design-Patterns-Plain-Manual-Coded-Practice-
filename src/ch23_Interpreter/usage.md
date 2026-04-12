# Interpreter 模式 — 真实应用

核心：**为一种语言定义语法表示，并提供一个解释器来处理这种语法。**

---

## 1. SQL 解析器

所有数据库引擎的核心是 Interpreter：解析 SQL 语法树，解释执行。

```
SQL 字符串
    ↓ Lexer（词法分析）
Token 流: SELECT, *, FROM, users, WHERE, age, >, 18
    ↓ Parser（语法分析）
AST（抽象语法树）:
    SelectStatement
    ├── columns: [*]
    ├── table: users
    └── where: BinaryExpr(age > 18)
    ↓ Interpreter（执行）
结果集
```

```java
// JOOQ：在 Java 里用类型安全的方式构建 SQL AST
Result<Record> result = dsl
    .select(USER.NAME, USER.EMAIL)        // AST 节点：SelectField
    .from(USER)                           // AST 节点：Table
    .where(USER.AGE.greaterThan(18)       // AST 节点：Condition
        .and(USER.ACTIVE.isTrue()))
    .orderBy(USER.CREATED_AT.desc())      // AST 节点：SortField
    .fetch();
// JOOQ 把这棵 AST 解释成对应数据库的 SQL 方言
```

---

## 2. 正则表达式

正则表达式是 Interpreter 最广泛的应用：
正则语法定义了一门"模式描述语言"，引擎解释执行。

```python
import re

# 正则表达式本身是一段程序，被 Interpreter 解析执行
pattern = re.compile(
    r'^(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})'  # 语法树节点
    r'T(?P<hour>\d{2}):(?P<minute>\d{2})'
    r'(?:Z|(?P<offset>[+-]\d{2}:\d{2}))$'
)

# 解释器执行：把输入字符串与语法树匹配
m = pattern.match("2025-12-25T10:30Z")
print(m.group('year'))  # 2025

# Python 的 re 模块把正则编译成字节码，再由 NFA/DFA 引擎解释执行
```

---

## 3. Jinja2 / Handlebars — 模板引擎

模板引擎是 Interpreter：模板语言有自己的语法，引擎解析并渲染。

```python
from jinja2 import Environment

env = Environment()
# 模板语言（一门迷你语言）
template = env.from_string("""
{% for user in users %}
    {% if user.active %}
        Hello, {{ user.name | upper }}!
    {% endif %}
{% endfor %}
""")

# Interpreter 解析模板 → 构建 AST → 执行
output = template.render(users=[
    {'name': 'alice', 'active': True},
    {'name': 'bob',   'active': False},
])
```

```typescript
// Handlebars（JavaScript 模板引擎）
const template = Handlebars.compile(`
    {{#each users}}
        {{#if this.active}}
            <div>{{this.name}}</div>
        {{/if}}
    {{/each}}
`);
// compile → parse → 生成 AST → render 时 Interpreter 执行 AST
const html = template({ users });
```

---

## 4. Babel / TypeScript — 编译器即 Interpreter

TypeScript 编译器是一个完整的 Interpreter：
解析 TypeScript 语法，类型检查，最终输出 JavaScript。

```typescript
// TypeScript Compiler API：直接操作 AST
import * as ts from 'typescript';

const source = `
    const add = (a: number, b: number): number => a + b;
    const result = add(1, 2);
`;

// Interpreter 的核心步骤
const sourceFile = ts.createSourceFile(
    'example.ts',
    source,
    ts.ScriptTarget.Latest
);

// 遍历 AST（Visitor + Interpreter）
function visit(node: ts.Node) {
    if (ts.isVariableDeclaration(node)) {
        console.log('Variable:', node.name.getText(sourceFile));
    }
    ts.forEachChild(node, visit);
}
visit(sourceFile);
```

---

## 5. Spring Expression Language (SpEL)

Spring 内置的表达式语言，用于配置文件、注解中的动态表达式求值。

```java
// SpEL 是一门迷你语言，Spring 内置 Interpreter 解析执行
@Value("#{systemProperties['user.home']}")          // 读系统属性
private String homeDir;

@Value("#{T(Math).random() * 100}")                 // 调用 Java 类方法
private double randomValue;

// Spring Security 用 SpEL 表达权限规则
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
public User getUser(@PathVariable Long userId) { ... }

// Spring Cache 用 SpEL 构建缓存 key
@Cacheable(value = "orders", key = "#userId + ':' + #status")
public List<Order> getOrders(String userId, String status) { ... }

// 编程式 SpEL 解释器
ExpressionParser parser = new SpelExpressionParser();
Expression expr = parser.parseExpression("'Hello ' + name.toUpperCase()");
String result = expr.getValue(context, String.class);
```

---

## Interpreter 的实现层次

| 层次 | 例子 | 复杂度 |
|------|------|--------|
| 字符串替换 | Mustache 模板 | 低 |
| 正则匹配 | `re`、`java.util.regex` | 中 |
| 表达式求值 | SpEL、OGNL、JMESPath | 中 |
| 完整语言解析 | SQL、GraphQL、TypeScript | 高 |

---

## Python 生态

Python 拥有最丰富的解析器生态：`ast` 标准库操作 Python 自身 AST，`lark` / `pyparsing` 构建自定义语言解析器。

```python
# 1. ast 模块 — 操作 Python 自身的 AST
import ast

source = """
def calculate(x: int, y: int) -> int:
    return x * x + 2 * x * y + y * y

result = calculate(3, 4)
"""

tree = ast.parse(source)

# Interpreter 遍历 AST（NodeVisitor 模式）
class ComplexityAnalyzer(ast.NodeVisitor):
    """统计函数中的运算符数量"""
    def __init__(self):
        self.operations: dict[str, int] = {}

    def visit_BinOp(self, node: ast.BinOp):
        op_name = type(node.op).__name__
        self.operations[op_name] = self.operations.get(op_name, 0) + 1
        self.generic_visit(node)

analyzer = ComplexityAnalyzer()
analyzer.visit(tree)
print(analyzer.operations)   # {'Mult': 3, 'Add': 2}

# NodeTransformer：修改 AST（代码转换工具的基础）
class ConstantFolder(ast.NodeTransformer):
    """常量折叠优化：2 + 3 → 5"""
    def visit_BinOp(self, node: ast.BinOp):
        self.generic_visit(node)
        if isinstance(node.left, ast.Constant) and isinstance(node.right, ast.Constant):
            try:
                result = eval(compile(ast.Expression(node), "<string>", "eval"))
                return ast.Constant(value=result)
            except Exception:
                pass
        return node

# 2. lark — 用 EBNF 语法构建 Parser（最流行的 Python 解析器库）
# pip install lark
# from lark import Lark, Transformer
#
# CALC_GRAMMAR = """
#     ?start: expr
#     ?expr: expr "+" term   -> add
#          | expr "-" term   -> sub
#          | term
#     ?term: term "*" atom   -> mul
#          | term "/" atom   -> div
#          | atom
#     ?atom: NUMBER          -> number
#          | "(" expr ")"
#     NUMBER: /\d+(\.\d+)?/
#     %ignore " "
# """
#
# class CalcTransformer(Transformer):
#     """Interpreter：把 ParseTree 转为计算结果"""
#     def add(self, args): return args[0] + args[1]
#     def sub(self, args): return args[0] - args[1]
#     def mul(self, args): return args[0] * args[1]
#     def div(self, args): return args[0] / args[1]
#     def number(self, args): return float(args[0])
#
# parser = Lark(CALC_GRAMMAR, parser="earley")
# calc   = CalcTransformer()
# print(calc.transform(parser.parse("3 + 4 * 2")))   # 11.0

# 3. 手写递归下降 Parser — 理解 Interpreter 原理
class SimpleExprInterpreter:
    """手写解释器：解析并求值简单算术表达式"""
    def __init__(self, text: str):
        self._tokens = iter(text.replace(" ", ""))
        self._current: str | None = None
        self._advance()

    def _advance(self) -> None:
        try:
            self._current = next(self._tokens)
        except StopIteration:
            self._current = None

    def parse(self) -> float:
        return self._expr()

    def _expr(self) -> float:
        result = self._term()
        while self._current in ("+", "-"):
            op = self._current
            self._advance()
            if op == "+":
                result += self._term()
            else:
                result -= self._term()
        return result

    def _term(self) -> float:
        result = self._atom()
        while self._current in ("*", "/"):
            op = self._current
            self._advance()
            if op == "*":
                result *= self._atom()
            else:
                result /= self._atom()
        return result

    def _atom(self) -> float:
        digits = []
        while self._current and self._current.isdigit():
            digits.append(self._current)
            self._advance()
        return float("".join(digits))

print(SimpleExprInterpreter("3+4*2").parse())     # 11.0
print(SimpleExprInterpreter("10-2*3").parse())    # 4.0
```

> **Python 洞察**：Python 的 `ast` 模块让你直接操作 Python 源代码的语法树——
> 代码检查工具（pylint、flake8）、代码格式化器（black）、类型检查器（mypy）
> 都基于 `ast` 模块实现，本质都是 Interpreter 模式的应用。
> `lark` 是构建自定义 DSL 的首选库，只需定义 EBNF 语法就能得到完整的 Parser。

---

## 关键洞察

> Interpreter 是"语言中的语言"——当你需要让用户描述逻辑（配置、查询、规则），
> 而不是让他们写 Java/Python 代码时，就需要 Interpreter。
> 实际项目里很少从零实现，通常用 ANTLR、PEG.js 等工具生成 Parser。
> 理解 Interpreter 更大的价值是**读懂编译器和框架的源码**。
