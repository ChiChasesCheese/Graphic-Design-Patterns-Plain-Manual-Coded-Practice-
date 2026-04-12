package ch01_Iterator;

// ── 原始写法 ──────────────────────────────────────────────────────────────
// public interface Iterator {
//     public abstract boolean hasNext();
//     public abstract Object next();
//     public abstract void resetIdx();
// }
//
// 问题：
//   1. Object next() 导致调用方必须强转：(Book) it.next()
//      缺少泛型 → 运行时才报 ClassCastException，编译期查不出来
//   2. resetIdx() 不属于 Iterator 契约（见下方说明）
// ─────────────────────────────────────────────────────────────────────────

// 泛型版本：<T> 替代 Object，调用方 next() 直接得到 T，编译期类型安全

// ── 关于 resetIdx() ──────────────────────────────────────────────────────
// Iterator 模式的核心契约：迭代器是"一次性"的，用完即弃。
// 标准库 java.util.Iterator 没有 reset，原因：
//   1. 并非所有数据源都支持重置（网络流、文件流无法倒回）
//   2. 正确做法：再调一次 bookShelf.iterator() 获得全新迭代器
//
// 如果确实需要 reset，应拆成独立接口，由调用方决定是否依赖：
//   interface Resettable { void reset(); }
//   class BookShelfIterator implements Iterator<Book>, Resettable { ... }
// ─────────────────────────────────────────────────────────────────────────

// interface 方法默认 public abstract，不需要显式写出
public interface Iterator<T> {
    boolean hasNext();
    T next();
}
