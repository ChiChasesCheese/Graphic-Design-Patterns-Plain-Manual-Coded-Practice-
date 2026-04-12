package ch06_Prototype;

import ch06_Prototype.framework.Manager;

import static java.lang.IO.print;

public class Main {
    static void main() {
        var u1 = new UnderlinePen('_');
        var m1 = new MessageBox('*');
        var m2 = new MessageBox('~');
        var mgr = new Manager();
        mgr.register("u1", u1);
        mgr.register("m1", m1);
        mgr.register("m2", m2);

        var new_u1 = mgr.create("u1");
        var new_m1 = mgr.create("m1");
        var new_m2 = mgr.create("m2");

        new_u1.use("New U1");
        new_m1.use("New M1");
        new_m2.use("New M2");
        print(new_m1 == new_m2);
        print(new_m1 == m1);
        print(new_m1.getClass() == m1.getClass());

    }
}