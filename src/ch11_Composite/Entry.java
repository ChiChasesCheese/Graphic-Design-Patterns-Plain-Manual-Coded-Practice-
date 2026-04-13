package ch11_Composite;

public abstract class Entry {
    public abstract String getName();
    public abstract int getSize();
    public void printList() {
        printList("");
    }
    public abstract void printList(String prefix);
    public Entry add(Entry entry) throws FileTreatmentException {
        throw new FileTreatmentException("You can't directly use Entry.add, use Directory.add instead");
    }

    public String toString() {
        return getName() + "(" + getSize() + ")";
    }
}