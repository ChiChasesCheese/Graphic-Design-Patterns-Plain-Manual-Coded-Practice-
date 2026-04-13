package ch12_Decorator;

import static java.lang.IO.print;
import static java.lang.IO.println;

public abstract class Display {
    abstract int getRows();
    abstract String getRowText(int row);
    abstract int getColumns();
    public void show() {
        for (int i = 0; i < getRows(); i++) {
            println(getRowText(i));
        }
    }
}