package ch06_Prototype.framework;

public interface Product extends Cloneable { // 这里是extends => 我拓展你的能力 ， 而不是我实现（implements）你的能力
    public abstract void use(String s);
    public abstract Product createClone();
}