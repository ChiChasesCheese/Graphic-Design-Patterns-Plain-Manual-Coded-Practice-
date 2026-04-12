package ch07_Builder;

public class Director {
    private final Builder builder;
    public Director(Builder builder) {
        this.builder = builder;
    }
    public void construct() {
        builder.makeTitle("This is a title");
        builder.makeString("String is here");
        builder.makeItems(new String[] {
                "Item1",
                "Item2"
        });
        builder.close();
    }
}