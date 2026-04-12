# Abstract Class 用途总结

## 三种用法

**1. Template Method — 定义流程骨架**
父类编排步骤顺序，子类填具体实现。
```java
abstract class Pipeline {
    final void run() { extract(); transform(); load(); }
    abstract void extract();
    abstract void transform();
    abstract void load();
}
```

**2. 共享状态和逻辑 — 避免重复代码**
多个子类有相同的字段或方法，提取到父类。
```java
abstract class Animal {
    protected String name;
    void eat() { System.out.println(name + " eating"); }
    abstract void speak();
}
```

**3. 提供默认实现 — 子类按需 override**
类似 interface 的 default 方法，但可以有状态。
```java
abstract class BaseRepository {
    abstract void save(Object o);
    void delete(Object o) { /* 默认实现，子类可以不管 */ }
}
```

---

## 和 interface 怎么选

| | interface | abstract class |
|--|-----------|----------------|
| 多继承 | ✅ | ❌ 只能一个 |
| 实例变量 | ❌ | ✅ |
| 表达语义 | 能做什么 | 是什么 |

**默认用 interface，只有需要共享状态或构造器时才用 abstract class。**

---

## 判断是不是 Template Method

父类里有没有一个方法**编排**了其他抽象方法的调用顺序？有就是，没有就只是普通抽象类。