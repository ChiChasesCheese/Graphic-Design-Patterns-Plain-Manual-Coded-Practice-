package ch07_Builder;

import static java.lang.IO.print;

public class Main {
    static void main() {
        var tb = new TextBuilder();
        var hb = new HTMLBuilder();
        var textDir = new Director(tb);
        var htmlDir = new Director(hb);
        textDir.construct();

        htmlDir.construct();

        print(tb.getResult());
        print(hb.getResult());
    }
}