package ch01_Iterator;

public interface Iterator {
    public abstract boolean hasNext();
    public abstract Object next();

    public abstract void resetIdx();
}