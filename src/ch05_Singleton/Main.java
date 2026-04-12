package ch05_Singleton;

import static java.lang.IO.print;

public class Main {
    public static void main(String[] args) {
        Singleton s1 = Singleton.getInstance();
        Singleton s2 = Singleton.getInstance();
        print(s1 == s2);
    }
}