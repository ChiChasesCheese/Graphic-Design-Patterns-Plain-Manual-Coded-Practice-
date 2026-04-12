package ch01_Iterator;

// ── 原始写法 ──────────────────────────────────────────────────────────────
// public interface Aggregate {
//     public abstract Iterator iterator();
// }
// ─────────────────────────────────────────────────────────────────────────

// 泛型版本：Aggregate<T> 对应"装着 T 类型元素的集合"

// ── 与标准库的关系 ────────────────────────────────────────────────────────
// Java 标准库用 java.lang.Iterable<T> 表达同样的概念：
//   interface Iterable<T> {
//       java.util.Iterator<T> iterator();
//   }
// 实现了 Iterable<T> 的类可以直接用 for-each 语法：
//   for (Book b : bookShelf) { ... }
//
// 书里自定义 Aggregate 是为了展示模式结构；实际项目直接实现 Iterable<T>。
// ─────────────────────────────────────────────────────────────────────────
public interface Aggregate<T> {
    Iterator<T> iterator();
}
