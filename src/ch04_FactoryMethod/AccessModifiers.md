# Java Access Modifiers 访问修饰符

## 四种级别

| 修饰符 | 同类 | 同包 | 子类 | 其他包 |
|--------|------|------|------|--------|
| `public` | ✅ | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| _(无修饰符)_ package-private | ✅ | ✅ | ❌ | ❌ |
| `private` | ✅ | ❌ | ❌ | ❌ |

---

## 逐个说明

**`public`** — 所有人可见
```java
public class IDCard { }          // 任何包都能用
public void use() { }            // 任何人都能调用
```

**`protected`** — 同包 + 子类
```java
protected Product createProduct(String owner) { }
// IDCardFactory extends Factory，可以 @Override
// 但包外的陌生类不能直接调用
```
Factory Method 模式里 `createProduct` 用 `protected` 是刻意的：
只允许子类 override，不允许外部直接调用。

**package-private（不写修饰符）** — 仅同包
```java
IDCard(String name) { }   // 不写修饰符
// IDCardFactory 和 IDCard 同在 idcard 包，Factory 可以 new IDCard()
// Main.java 在外部包，new IDCard() 编译报错
```
Factory Method 模式里构造器用 package-private 是刻意的：
强制外部只能通过 Factory 创建对象，不能绕过。

**`private`** — 仅本类
```java
private final String owner;   // IDCard 内部字段，外部无法读写
private void helper() { }     // 实现细节，不对外暴露
```

---

## 选择原则

**能用更严格的就用更严格的，只在需要时才放开。**

```
默认思路：private
需要子类用：protected
需要同包用：package-private
需要所有人用：public
```

---

## 和设计模式的关系

| 模式 | 用法 |
|------|------|
| Factory Method | 构造器 package-private，强制走 Factory |
| Template Method | 骨架方法 `public final`，钩子 `protected abstract` |
| Singleton | 构造器 `private`，禁止外部 new |


---


## 骨架方法 vs 钩子

**骨架方法** — 定义流程，`public final`，子类不能覆盖：
```java
public final void display() {  // public：所有人能调用；final：子类不能覆盖
    open();
    print();
    close();
}
```

**钩子（hook）** — 骨架里预留的空位，`protected abstract`，子类来填：
```java
protected abstract void open();  // protected：只有子类能实现；abstract：必须实现
```

"钩子"来自英文 hook（挂钩）。骨架方法是流水线，钩子是流水线上预留的挂钩，子类把自己的实现挂上去：

```
流水线（骨架）：  open() ──→ print() ──→ close()
                   ↑              ↑            ↑
                 钩子1          钩子2         钩子3
                （子类挂）    （子类挂）    （子类挂）
```
