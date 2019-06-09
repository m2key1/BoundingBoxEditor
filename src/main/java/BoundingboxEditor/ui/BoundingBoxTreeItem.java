package BoundingboxEditor.ui;

import javafx.scene.control.TreeItem;

/**
 * A tree-item representing an existing {@link BoundingBoxView} in a {@link BoundingBoxTreeCell} of a {@link BoundingBoxTreeView}.
 *
 * @see TreeItem
 */
class BoundingBoxTreeItem extends TreeItem<BoundingBoxView> {
    private static final double TOGGLE_ICON_SIDE_LENGTH = 9.5;

    private final ToggleSquare toggleIcon = new ToggleSquare(TOGGLE_ICON_SIDE_LENGTH);
    private int id = 1;

    /**
     * Creates a new tree-item representing a {@link BoundingBoxView} in a {@link BoundingBoxTreeCell} that is part of
     * a {@link BoundingBoxTreeView}.
     *
     * @param boundingBoxView the {@link BoundingBoxView} that should be associated with the tree-item
     */
    BoundingBoxTreeItem(BoundingBoxView boundingBoxView) {
        super(boundingBoxView);
        setGraphic(toggleIcon);

        setUpInternalListeners();
    }

    /**
     * Returns the toggle-state of the tree-item's toggle-square.
     *
     * @return true if toggled on, false otherwise
     */
    boolean isIconToggledOn() {
        return toggleIcon.isToggledOn();
    }

    /**
     * Sets the toggle-state of the tree-item's toggle-square (and all its children)
     * and updates the parent {@link BoundingBoxCategoryTreeItem} object's number of
     * toggled-on children.
     *
     * @param toggledOn true to toggle on, false to toggle off
     */
    void setIconToggledOn(boolean toggledOn) {
        if(toggledOn == isIconToggledOn()) {
            return;
        }

        toggleIcon.setToggledOn(toggledOn);

        getValue().setVisible(toggledOn);
        // A BoundingBoxTreeItem either does not have any children, or
        // every child is an instance of BoundingBoxCategoryTreeItem.
        getChildren().stream()
                .map(child -> (BoundingBoxCategoryTreeItem) child)
                .forEach(child -> child.setIconToggledOn(toggledOn));
        // Similarly the parent of a BoundingBoxTreeItem is always an instance of
        // BoundingBoxCategoryTreeItem.
        if(toggledOn) {
            ((BoundingBoxCategoryTreeItem) getParent()).incrementNrToggledOnChildren();
        } else {
            ((BoundingBoxCategoryTreeItem) getParent()).decrementNrToggledOnChildren();
        }
    }

    /**
     * Returns the tree-item's id. This id is always kept equivalent to the tree-item's index in the
     * children-list of its parent-{@link BoundingBoxCategoryTreeItem} plus 1.
     * It is displayed as part of the name of the tree-cell this tree-item is assigned to.
     *
     * @return the id
     */
    int getId() {
        return id;
    }

    /**
     * Sets the tree-item's id. This id is always kept equivalent to the tree-item's index in the
     * children-list of its parent-{@link BoundingBoxCategoryTreeItem} plus 1.
     * It is displayed as part of the name of the tree-cell this tree-item is assigned to.
     *
     * @param id the id to set
     */
    void setId(int id) {
        this.id = id;
    }

    private void setUpInternalListeners() {
        toggleIcon.fillProperty().bind(getValue().getBoundingBoxCategory().colorProperty());

        toggleIcon.setOnMousePressed(event -> {
            setIconToggledOn(!isIconToggledOn());
            event.consume();
        });
    }
}