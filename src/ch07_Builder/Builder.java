package ch07_Builder;

public interface Builder {
    // 接口方法默认就是 public abstract，显式写出反而是噪音
    void makeTitle(String title);
    void makeString(String str);
    void makeItems(String[] items);
    void close();
}