package ch06_Prototype;

import ch06_Prototype.framework.Product;

import static java.lang.IO.print;
import static java.lang.IO.println;

public class UnderlinePen implements Product {
    private final char underline_ch;
    public UnderlinePen(char underline_ch) {
        this.underline_ch = underline_ch;
    }

    @Override
    public void use(String s) {
        int len = s.length();
        System.out.println(s);
        System.out.println(String.valueOf(underline_ch).repeat(len));
    }

    @Override
    public Product createClone() {
        try {
            return (Product) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

}