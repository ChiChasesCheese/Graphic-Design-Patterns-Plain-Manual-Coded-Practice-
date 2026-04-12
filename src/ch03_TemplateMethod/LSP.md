# 里氏替换原则 Liskov Substitution Principle

> "凡是用父类的地方，换成子类，程序行为不能变。"
> — Barbara Liskov，1987

---

## 一句话理解

你去咖啡店点了"一杯咖啡"，服务员端来美式或拿铁都没问题——你喝咖啡的目的达到了。  
但如果端来一杯茶，虽然也是热饮，但**违背了你的预期**，这就违反了 LSP。

---

## 在本章的体现

```java
AbstractDisplay d = new CharDisplay('c');
d.display();  // 你只知道"它能 display"，不关心它是 Char 还是 String
```

`Main.java` 里把声明类型改成了 `AbstractDisplay`，这正是 LSP 的标准用法：

```java
AbstractDisplay chDis = new CharDisplay('c');   // ✅ 可替换
AbstractDisplay sDis  = new StringDisplay("S"); // ✅ 可替换
```

`display()` 的行为（open 5次print close）对所有子类一致，只有视觉效果不同。  
调用方完全不需要知道背后是哪个子类——**可以随意替换，行为不会出乎意料**。

---

## 违反 LSP 的长啥样

### 案例一：子类削弱了父类的能力

```java
abstract class Bird {
    abstract void fly();
}

class Penguin extends Bird {
    @Override
    void fly() {
        throw new UnsupportedOperationException("企鹅不会飞");
    }
}
```

```java
Bird b = new Penguin();
b.fly(); // 💥 运行时炸了——父类承诺"能飞"，子类违约
```

表面上继承关系在现实中成立（企鹅是鸟），但在代码语义上违反了 LSP。  
**修法**：把 `fly()` 从 `Bird` 里拿掉，单独用 `Flyable` 接口。

---

### 案例二：子类改变了父类方法的含义

```java
class Rectangle {
    int width, height;
    void setWidth(int w)  { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int area() { return width * height; }
}

class Square extends Rectangle {
    @Override
    void setWidth(int w) {
        this.width = w;
        this.height = w;
    }
    @Override
    void setHeight(int h) {
        this.width = h;
        this.height = h;
    }
}
```

```java
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(3);
System.out.println(r.area()); // 期望 15，实际输出 9 💥
```

**修法**：不要让 Square 继承 Rectangle，两者都实现同一个 `Shape` 接口。

---

## 和 Template Method 的关系

| 父类承诺 | 子类义务 |
|---------|---------|
| `display()` 会按 open→print×5→close 执行 | 实现 `open/print/close`，不改变整体流程 |
| `display()` 是 `final`，不可覆盖 | 无法破坏父类的承诺 |

`final` 是**用编译器强制执行 LSP 的手段**。`sealed` 进一步收紧：不在 `permits` 列表里的类根本不能继承。

---

## 记忆口诀

```
父类能做的，子类都能做。
子类额外能做的，父类不需要知道。
子类绝对不能做的：让父类的承诺落空。
```

---

## SOLID 速查

| 原则 | 一句话 |
|------|--------|
| SRP 单一职责 | 一个类只做一件事 |
| OCP 开闭原则 | 对扩展开放，对修改关闭 |
| **LSP** 里氏替换 | 子类可以无缝替换父类 |
| ISP 接口隔离 | 接口不要太胖，拆小 |
| DIP 依赖倒置 | 依赖抽象，不依赖具体类 |
