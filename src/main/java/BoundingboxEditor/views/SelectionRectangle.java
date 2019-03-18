package BoundingboxEditor.views;

import BoundingboxEditor.BoundingBoxCategory;
import BoundingboxEditor.DragAnchor;
import BoundingboxEditor.ImageMetaData;
import BoundingboxEditor.MathUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

public class SelectionRectangle extends Rectangle {

    private static final String SELECTION_RECTANGLE_STYLE = "selectionRectangle";
    private static final double DEFAULT_FILL_OPACITY = 0.4;
    private static final SelectionRectangle nullSelectionRectangle = new SelectionRectangle();
    private final List<ResizeHandle> resizeHandles = new ArrayList<>();
    private final DragAnchor dragAnchor = new DragAnchor();

    private final Property<Bounds> confinementBounds = new SimpleObjectProperty<>();
    private BoundingBoxCategory boundingBoxCategory;
    private ImageMetaData imageMetaData;


    public SelectionRectangle(BoundingBoxCategory category, ImageMetaData imageMetaData) {
        this.getStyleClass().add(SELECTION_RECTANGLE_STYLE);
        this.imageMetaData = imageMetaData;
        boundingBoxCategory = category;
        setVisible(false);

        createResizeHandles();
        addMoveFunctionality();
    }

    private SelectionRectangle() {
    }

    public BoundingBoxCategory getBoundingBoxCategory() {
        return boundingBoxCategory;
    }

    public ImageMetaData getImageMetaData() {
        return imageMetaData;
    }

    public Bounds getImageRelativeBounds() {
        final Bounds imageViewBounds = confinementBounds.getValue();
        final Bounds selectionRectangleBounds = this.getBoundsInParent();

        double imageWidth = imageMetaData.getImageWidth();
        double imageHeight = imageMetaData.getImageHeight();

        double xMinRelative = selectionRectangleBounds.getMinX() * imageWidth / imageViewBounds.getWidth();
        double yMinRelative = selectionRectangleBounds.getMinY() * imageHeight / imageViewBounds.getHeight();
        double widthRelative = selectionRectangleBounds.getWidth() * imageWidth / imageViewBounds.getWidth();
        double heightRelative = selectionRectangleBounds.getHeight() * imageHeight / imageViewBounds.getHeight();

        return new BoundingBox(xMinRelative, yMinRelative, widthRelative, heightRelative);
    }

    public void confineTo(final ReadOnlyObjectProperty<Bounds> bounds) {
        confinementBounds.bind(bounds);

        bounds.addListener((observable, oldValue, newValue) -> {
            this.setWidth(this.getWidth() * newValue.getWidth() / oldValue.getWidth());
            this.setHeight(this.getHeight() * newValue.getHeight() / oldValue.getHeight());

            this.setX(newValue.getMinX() + (this.getX()
                    - oldValue.getMinX()) * newValue.getWidth() / oldValue.getWidth());
            this.setY(newValue.getMinY() + (this.getY()
                    - oldValue.getMinY()) * newValue.getHeight() / oldValue.getHeight());
        });
    }

    public void setXYWH(double x, double y, double w, double h) {
        this.setX(x);
        this.setY(y);
        this.setWidth(w);
        this.setHeight(h);
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    static SelectionRectangle getDummy() {
        return nullSelectionRectangle;
    }

    List<Node> getNodes() {
        this.setManaged(false);
        for(Rectangle rect : resizeHandles) {
            rect.setManaged(false);
        }

        ArrayList<Node> nodeList = new ArrayList<>();
        nodeList.add(this);
        nodeList.addAll(resizeHandles);

        return nodeList;
    }

    void fillOpaque() {
        setFill(Color.web(getStroke().toString(), DEFAULT_FILL_OPACITY));
    }

    private double getMaxX() {
        return this.getX() + this.getWidth();
    }

    private double getMaxY() {
        return this.getY() + this.getHeight();
    }

    private void createResizeHandles() {
        for(CompassPoint compass_point : CompassPoint.values()) {
            resizeHandles.add(new ResizeHandle(compass_point));
        }
    }

    private void addMoveFunctionality() {
        this.setOnMouseEntered(event -> {
            this.setCursor(Cursor.MOVE);
            event.consume();
        });

        this.setOnMousePressed(event -> {
            if(event.getButton().equals(MouseButton.PRIMARY)) {
                final Point2D eventXY = new Point2D(event.getX(), event.getY());
                dragAnchor.setFromPoint2D(eventXY.subtract(this.getX(), this.getY()));
            }
            event.consume();
        });

        this.setOnMouseDragged(event -> {
            if(event.getButton().equals(MouseButton.PRIMARY)) {
                final Point2D eventXY = new Point2D(event.getX(), event.getY());
                final Point2D newXY = eventXY.subtract(dragAnchor.getX(), dragAnchor.getY());
                final Bounds regionBounds = confinementBounds.getValue();
                final Bounds moveBounds = new BoundingBox(regionBounds.getMinX(), regionBounds.getMinY(),
                        regionBounds.getWidth() - this.getWidth(),
                        regionBounds.getHeight() - this.getHeight());
                final Point2D newConfinedXY = MathUtils.clampWithinBounds(newXY, moveBounds);

                this.setX(newConfinedXY.getX());
                this.setY(newConfinedXY.getY());
            }
            event.consume();
        });
    }

    private enum CompassPoint {NW, N, NE, E, SE, S, SW, W}

    private class ResizeHandle extends Rectangle {

        private static final double SIDE_LENGTH = 8.0;
        private final CompassPoint compassPoint;
        private final DragAnchor dragAnchor = new DragAnchor();

        ResizeHandle(CompassPoint compassPoint) {
            super(SIDE_LENGTH, SIDE_LENGTH);
            this.compassPoint = compassPoint;
            bindToParentRectangle();
            addResizeFunctionality();
        }

        private void bindToParentRectangle() {
            final SelectionRectangle rectangle = SelectionRectangle.this;
            final DoubleProperty rectangle_x = rectangle.xProperty();
            final DoubleProperty rectangle_y = rectangle.yProperty();
            final DoubleProperty rectangle_w = rectangle.widthProperty();
            final DoubleProperty rectangle_h = rectangle.heightProperty();

            fillProperty().bind(rectangle.strokeProperty());
            visibleProperty().bind(rectangle.visibleProperty());

            switch(compassPoint) {
                case NW:
                    xProperty().bind(rectangle_x.subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.subtract(SIDE_LENGTH / 2));
                    break;
                case N:
                    xProperty().bind(rectangle_x.add(rectangle_w.subtract(SIDE_LENGTH).divide(2)));
                    yProperty().bind(rectangle_y.subtract(SIDE_LENGTH / 2));
                    break;
                case NE:
                    xProperty().bind(rectangle_x.add(rectangle_w).subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.subtract(SIDE_LENGTH / 2));
                    break;
                case E:
                    xProperty().bind(rectangle_x.add(rectangle_w).subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.add(rectangle_h.subtract(SIDE_LENGTH).divide(2)));
                    break;
                case SE:
                    xProperty().bind(rectangle_x.add(rectangle_w).subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.add(rectangle_h).subtract(SIDE_LENGTH / 2));
                    break;
                case S:
                    xProperty().bind(rectangle_x.add(rectangle_w.subtract(SIDE_LENGTH).divide(2)));
                    yProperty().bind(rectangle_y.add(rectangle_h).subtract(SIDE_LENGTH / 2));
                    break;
                case SW:
                    xProperty().bind(rectangle_x.subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.add(rectangle_h).subtract(SIDE_LENGTH / 2));
                    break;
                case W:
                    xProperty().bind(rectangle_x.subtract(SIDE_LENGTH / 2));
                    yProperty().bind(rectangle_y.add(rectangle_h.subtract(SIDE_LENGTH).divide(2)));
            }
        }

        private void addResizeFunctionality() {
            setOnMouseEntered(event -> {
                setCursor(Cursor.cursor(compassPoint.toString() + "_RESIZE"));
                event.consume();
            });

            setOnMousePressed(event -> {
                if(event.getButton().equals(MouseButton.PRIMARY)) {
                    dragAnchor.setFromMouseEvent(event);
                }
                event.consume();
            });

            final SelectionRectangle rectangle = SelectionRectangle.this;

            switch(compassPoint) {
                case NW:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(parentBounds.getMinX(), parentBounds.getMinY(),
                                    rectangle.getMaxX() - parentBounds.getMinX(),
                                    rectangle.getMaxY() - parentBounds.getMinY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setX(clampedEventXY.getX());
                            rectangle.setY(clampedEventXY.getY());
                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMaxX()));
                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMaxY()));
                        }

                        event.consume();
                    });
                    break;
                case N:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(rectangle.getX(), parentBounds.getMinY(),
                                    rectangle.getWidth(), rectangle.getMaxY() - parentBounds.getMinY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setY(clampedEventXY.getY());
                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMaxY()));
                        }

                        event.consume();
                    });
                    break;
                case NE:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(rectangle.getX(), parentBounds.getMinY(),
                                    parentBounds.getMaxX() - rectangle.getX(),
                                    rectangle.getMaxY() - parentBounds.getMinY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setY(clampedEventXY.getY());
                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMinX()));
                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMaxY()));
                        }

                        event.consume();
                    });
                    break;
                case E:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(rectangle.getX(), rectangle.getY(),
                                    parentBounds.getMaxX() - rectangle.getX(), rectangle.getHeight());
                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMinX()));
                        }

                        event.consume();
                    });
                    break;
                case SE:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(rectangle.getX(), rectangle.getY(),
                                    parentBounds.getMaxX() - rectangle.getX(),
                                    parentBounds.getMaxY() - rectangle.getY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMinX()));
                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMinY()));
                        }

                        event.consume();
                    });
                    break;
                case S:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(rectangle.getX(), rectangle.getY(),
                                    rectangle.getWidth(),
                                    parentBounds.getMaxY() - rectangle.getY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMinY()));
                        }

                        event.consume();
                    });
                    break;
                case SW:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(parentBounds.getMinX(), rectangle.getY(),
                                    rectangle.getMaxX() - parentBounds.getMinX(),
                                    parentBounds.getMaxY() - rectangle.getY());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setX(clampedEventXY.getX());
                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMaxX()));
                            rectangle.setHeight(Math.abs(clampedEventXY.getY() - bounds.getMinY()));
                        }

                        event.consume();
                    });
                    break;
                case W:
                    setOnMouseDragged(event -> {
                        if(event.getButton().equals(MouseButton.PRIMARY)) {
                            final Bounds parentBounds = rectangle.confinementBounds.getValue();
                            final Bounds bounds = new BoundingBox(parentBounds.getMinX(), rectangle.getY(),
                                    rectangle.getMaxX() - parentBounds.getMinX(),
                                    rectangle.getHeight());

                            final Point2D eventXY = new Point2D(event.getX(), event.getY());
                            final Point2D clampedEventXY = MathUtils.clampWithinBounds(eventXY, bounds);

                            rectangle.setX(clampedEventXY.getX());
                            rectangle.setWidth(Math.abs(clampedEventXY.getX() - bounds.getMaxX()));
                        }

                        event.consume();
                    });
                    break;
            }
        }
    }
}