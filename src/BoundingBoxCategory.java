import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;


/**
 * A class representing the category (e.g. "Car", "Bus" etc.) of a bounding-box.
 */
public class BoundingBoxCategory {

    private static final String DEFAULT_NAME = "Default";

    private final StringProperty name;
    private final ObjectProperty<Color> color;

    public BoundingBoxCategory() {
        this.name = new SimpleStringProperty(DEFAULT_NAME);
        this.color = new SimpleObjectProperty<>(Color.ORANGE);
    }

    /**
     * @param name  The name of the bounding-box category.
     * @param color The color of the visual representation of
     *              the bounding-box in the program.
     */
    public BoundingBoxCategory(String name, Color color) {
        this.name = new SimpleStringProperty(name);
        this.color = new SimpleObjectProperty<>(color);
    }


    /**
     * @return The name of this bounding-box category.
     */
    public String getName() {
        return name.get();
    }

    /**
     * @param name The desired name of this bounding-box category.
     */
    public void setName(String name) {
        this.name.set(name);
    }

    /**
     * @return The color of the visual representation bounding-boxes belonging
     * to this category.
     */
    public Color getColor() {
        return color.get();
    }

    /**
     * @param color The desired color of the visual representation bounding-boxes belonging
     *              to this category.
     */
    public void setColor(Color color) {
        this.color.set(color);
    }

    /**
     * Allows to bind or add listeners to the name of this bounding-box category.
     *
     * @return The string-property of the name of this bounding-box category.
     */
    public StringProperty nameProperty() {
        return name;
    }

    /**
     * Allows to bind or add listeners to the color of this bounding-box category.
     *
     * @return The object-property of the color of this bounding-box category.
     */
    public ObjectProperty<Color> colorProperty() {
        return color;
    }
}
