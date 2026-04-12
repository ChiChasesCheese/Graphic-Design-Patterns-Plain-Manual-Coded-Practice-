package ch01_Iterator;

// ── 原始写法 ──────────────────────────────────────────────────────────────
// public class Book {
//     private String name;
//     public Book(String name) { this.name = name; }
//     public String getName()  { return name; }
//     public void setName(String name) { this.name = name; }
// }
//
// 问题：
//   1. 需要手写构造器、getter、setter、equals、hashCode、toString —— 全是样板代码
//   2. setName 让 Book 可变（mutable）：book1.setName("NEW") 会同时影响
//      书架里所有指向同一个对象的槽位，Main.java 里你已经踩到这个坑了
// ─────────────────────────────────────────────────────────────────────────

// Java 16+ record：
//   编译器自动生成：构造器、accessor、equals()、hashCode()、toString()
//   record 天生不可变（所有字段隐式 final），没有 setter
//
// 访问方式变化：
//   旧：book.getName()
//   新：book.name()   ← record accessor 直接用字段名，不加 get 前缀
public record Book(String name) {

    // "修改"书名的惯用法：返回新对象，原对象不受影响（wither 模式）
    // 类似 String.replace()，String 本身也是不可变的
    public Book withName(String newName) {
        return new Book(newName);
    }
}
