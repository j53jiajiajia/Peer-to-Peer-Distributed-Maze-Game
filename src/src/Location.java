package src;

public class Location {
    private int x;
    private int y;

    // Constructor
    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Getter for x coordinate
    public int getX() {
        return x;
    }

    // Setter for x coordinate
    public void setX(int x) {
        this.x = x;
    }

    // Getter for y coordinate
    public int getY() {
        return y;
    }

    // Setter for y coordinate
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}