package ch03_TemplateMethod;

import java.util.stream.IntStream;

// Template Method 模式的核心：
//   算法骨架定义在父类，具体步骤延迟到子类实现
//
// ── final 的作用 ──────────────────────────────────────────────────────────
// display() 加 final：子类不能覆盖算法流程，只能实现各个步骤
// 没有 final 的话子类可以完全重写 display()，模式就失效了
//
// ── sealed（Java 17+）────────────────────────────────────────────────────
// sealed 限制哪些类可以继承，编译器强制检查
// 好处：
//   1. 明确表达"这个类只允许这几种子类"的设计意图
//   2. 配合 switch pattern matching（Java 21+）可以做穷举检查，漏掉一种编译报错
//   3. 比文档注释更可靠——违反就编译报错
// ─────────────────────────────────────────────────────────────────────────
public abstract sealed class AbstractDisplay
        permits CharDisplay, StringDisplay {

    public abstract void open();
    public abstract void print();
    public abstract void close();

    public final void display() {
        open();
        // IntStream.range 替代 for(int i=0; i<5; i++)
        // 语义更清晰：对 [0,5) 范围内每个值执行操作
        // _ 是 Java 21+ 的未命名变量，表示"我不关心这个值"
        IntStream.range(0, 5).forEach(_ -> print());  // _ 未命名变量需要 Java 22+，此处用 i 代替
        close();
    }
}
