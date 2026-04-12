package ch06_Prototype;

import ch06_Prototype.framework.Product;

import static java.lang.IO.print;
import static java.lang.IO.println;

public class MessageBox implements Product {
    private final char decoChar;
    public MessageBox(char decoChar) {
        this.decoChar = decoChar;
    }

    @Override
    public void use(String s) {
        int len = s.length();
        String line = String.valueOf(decoChar).repeat(len + 2);

        System.out.println(line);
        System.out.println(s);
        System.out.println(line);
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