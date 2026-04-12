# Visitor 模式 — 真实应用

核心：**在不修改对象结构的前提下，新增对整个结构的操作。把"操作"从"结构"中分离。**

---

## 1. Babel — AST 变换

Babel 是 Visitor 模式最广为人知的应用。
JavaScript 源码解析成 AST（抽象语法树），每种插件是一个 Visitor，
遍历 AST 节点并做变换。

```javascript
// Babel 插件（简化）：把 console.log 替换为 logger.debug
module.exports = function(babel) {
    const { types: t } = babel;

    return {
        visitor: {
            // 每种 AST 节点类型对应一个访问方法
            CallExpression(path) {
                if (
                    t.isMemberExpression(path.node.callee) &&
                    path.node.callee.object.name === 'console' &&
                    path.node.callee.property.name === 'log'
                ) {
                    // 替换节点：console.log → logger.debug
                    path.node.callee.object.name = 'logger';
                    path.node.callee.property.name = 'debug';
                }
            },
            // 其他节点类型：ArrowFunctionExpression, ImportDeclaration...
        }
    };
};

// Babel 遍历整棵 AST，对每个节点调用匹配的 visitor 方法
// 新增变换 = 新增 visitor，不修改 AST 结构本身
```

---

## 2. ESLint — 代码检查规则

ESLint 的每条规则是一个 Visitor，遍历 AST 检查代码风格和错误。

```javascript
// ESLint 自定义规则：禁止使用 var
module.exports = {
    create(context) {
        return {
            // Visitor：每次遇到变量声明节点时触发
            VariableDeclaration(node) {
                if (node.kind === 'var') {
                    context.report({
                        node,
                        message: "Use 'const' or 'let' instead of 'var'.",
                        fix(fixer) {
                            return fixer.replaceText(
                                node.parent.tokens[0],
                                'let'
                            );
                        }
                    });
                }
            }
        };
    }
};
```

---

## 3. Java — `javax.lang.model` 注解处理器

Java 编译期注解处理（APT）用 Visitor 遍历语法树，Lombok 就是这么实现的。

```java
// Java 编译器的语法树 Visitor
public class LombokProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment env) {

        for (Element element : env.getElementsAnnotatedWith(Data.class)) {
            // Visitor 访问每个带 @Data 注解的类
            element.accept(new DataAnnotationVisitor(), null);
        }
        return true;
    }
}

// Visitor 实现：为类生成 getter/setter/equals/hashCode
class DataAnnotationVisitor extends SimpleElementVisitor14<Void, Void> {
    @Override
    public Void visitType(TypeElement e, Void unused) {
        generateGetters(e);
        generateSetters(e);
        generateEquals(e);
        return null;
    }
}
```

---

## 4. TypeScript — `ts-morph` / TypeScript Compiler API

`ts-morph` 封装了 TypeScript 编译器 API，用 Visitor 分析和修改 TypeScript 代码，
常用于代码生成、重构工具、类型检查工具。

```typescript
import { Project, SyntaxKind } from 'ts-morph';

const project = new Project();
const sourceFile = project.addSourceFileAtPath('src/service.ts');

// Visitor：找到所有 async 函数，检查是否有 try-catch
sourceFile.forEachDescendant((node) => {
    if (node.getKind() === SyntaxKind.AsyncKeyword) {
        const func = node.getParent();
        const hasTryCatch = func.getDescendantsOfKind(SyntaxKind.TryStatement).length > 0;
        if (!hasTryCatch) {
            console.warn(`Async function without try-catch: ${func.getText().slice(0, 50)}`);
        }
    }
});
```

---

## 5. Python — `ast` 模块

Python 标准库内置 AST Visitor，用于代码分析、自动化重构。

```python
import ast

# Visitor：统计代码里所有函数调用的函数名
class FunctionCallCollector(ast.NodeVisitor):

    def __init__(self):
        self.calls = []

    def visit_Call(self, node):
        # 访问每个函数调用节点
        if isinstance(node.func, ast.Name):
            self.calls.append(node.func.id)
        elif isinstance(node.func, ast.Attribute):
            self.calls.append(f"{node.func.value.id}.{node.func.attr}")
        self.generic_visit(node)  # 继续遍历子节点

source = open("my_module.py").read()
tree = ast.parse(source)

visitor = FunctionCallCollector()
visitor.visit(tree)
print(visitor.calls)  # ['print', 'os.path.join', 'requests.get', ...]
```

---

## Python 生态

Python 用 `functools.singledispatch` 实现多分派，是比手写 `visit_XxxNode` 更 Pythonic 的 Visitor 替代方案。

```python
from functools import singledispatch
from dataclasses import dataclass
from typing import Union

# 1. AST 节点定义（数据结构）
@dataclass
class Number:
    value: float

@dataclass
class Add:
    left: "Expr"
    right: "Expr"

@dataclass
class Multiply:
    left: "Expr"
    right: "Expr"

@dataclass
class Variable:
    name: str

Expr = Union[Number, Add, Multiply, Variable]

# 2. singledispatch Visitor — 操作与结构分离
@singledispatch
def evaluate(node: Expr, env: dict[str, float] = {}) -> float:
    raise NotImplementedError(f"evaluate not implemented for {type(node)}")

@evaluate.register(Number)
def _(node: Number, env: dict = {}) -> float:
    return node.value

@evaluate.register(Variable)
def _(node: Variable, env: dict = {}) -> float:
    if node.name not in env:
        raise KeyError(f"Undefined variable: {node.name}")
    return env[node.name]

@evaluate.register(Add)
def _(node: Add, env: dict = {}) -> float:
    return evaluate(node.left, env) + evaluate(node.right, env)

@evaluate.register(Multiply)
def _(node: Multiply, env: dict = {}) -> float:
    return evaluate(node.left, env) * evaluate(node.right, env)

# 另一个 Visitor：把 AST 转为字符串
@singledispatch
def to_string(node: Expr) -> str:
    raise NotImplementedError

@to_string.register(Number)
def _(node: Number) -> str:
    return str(node.value)

@to_string.register(Variable)
def _(node: Variable) -> str:
    return node.name

@to_string.register(Add)
def _(node: Add) -> str:
    return f"({to_string(node.left)} + {to_string(node.right)})"

@to_string.register(Multiply)
def _(node: Multiply) -> str:
    return f"({to_string(node.left)} * {to_string(node.right)})"

# (x + 2) * (y + 3)
expr = Multiply(
    Add(Variable("x"), Number(2)),
    Add(Variable("y"), Number(3))
)

print(to_string(expr))                         # ((x + 2.0) * (y + 3.0))
print(evaluate(expr, {"x": 1.0, "y": 4.0}))   # 21.0

# 新增操作：不修改 AST 节点，只添加新的 singledispatch 函数
@singledispatch
def count_nodes(node: Expr) -> int:
    raise NotImplementedError

@count_nodes.register(Number)
@count_nodes.register(Variable)
def _(node) -> int:
    return 1

@count_nodes.register(Add)
@count_nodes.register(Multiply)
def _(node) -> int:
    return 1 + count_nodes(node.left) + count_nodes(node.right)

print(count_nodes(expr))   # 5

# 3. ast 标准库 — Python 自身 AST 的 Visitor
import ast

class FunctionFinder(ast.NodeVisitor):
    """Visitor：收集所有函数定义的名字"""
    def __init__(self):
        self.functions: list[str] = []

    def visit_FunctionDef(self, node: ast.FunctionDef):
        self.functions.append(node.name)
        self.generic_visit(node)   # 继续遍历子节点

source = """
def greet(name):
    def inner():
        pass
    return inner

def add(a, b):
    return a + b
"""

tree = ast.parse(source)
finder = FunctionFinder()
finder.visit(tree)
print(finder.functions)   # ['greet', 'inner', 'add']
```

> **Python 洞察**：`singledispatch` 解决了 Python 单分派的限制——
> 根据第一个参数的类型自动选择实现，效果类似 Visitor 的双分派。
> Python 标准库的 `ast.NodeVisitor` 就是经典 GoF Visitor 模式的直接实现。

---

## 关键洞察

> Visitor 解决的问题：**数据结构稳定，但操作种类不断增加**。
> AST 节点类型不常变，但你需要不断增加新的分析/变换操作。
> 如果用继承，每加一种操作就要给所有节点类加一个方法——Visitor 把操作集中在一处。
>
> 现实里 Visitor 几乎专属于"编译器/代码工具"领域，
> 如果你在其他场景想用 Visitor，先想想是否有更简单的方案。
