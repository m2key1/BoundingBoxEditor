package BoundingboxEditor.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Represents a tree-cell in a {@link BoundingBoxTreeView}. Instances of this class are either associated
 * with a {@link BoundingBoxCategoryTreeItem} or a {@link BoundingBoxTreeItem} and are responsible for the
 * visual representation of these items in the {@link BoundingBoxTreeView}.
 *
 * @see TreeCell
 */
class BoundingBoxTreeCell extends TreeCell<BoundingBoxView> {
    private static final String DELETE_CONTEXT_MENU_STYLE = "delete-context-menu";
    private static final String DELETE_BOUNDING_BOX_MENU_ITEM_TEXT = "Delete";
    private static final String NAME_TEXT_STYLE = "default-text";
    private static final String INFO_TEXT_ID = "info-text";
    private static final String TAG_ICON_REGION_ID = "tag-icon";
    private static final PseudoClass draggedOverPseudoClass = PseudoClass.getPseudoClass("dragged-over");
    private static final String CATEGORY_NAME_TEXT_ID = "category-name-text";
    private static final String TREE_CELL_CONTENT_ID = "tree-cell-content";
    private static final String HIDE_BOUNDING_BOX_MENU_ITEM_TEXT = "Hide";

    private final BooleanProperty draggedOver = new SimpleBooleanProperty(false);
    private final MenuItem deleteBoundingBoxMenuItem = new MenuItem(DELETE_BOUNDING_BOX_MENU_ITEM_TEXT);
    private final MenuItem hideBoundingBoxMenuItem = new MenuItem(HIDE_BOUNDING_BOX_MENU_ITEM_TEXT);
    private final ContextMenu contextMenu = new ContextMenu();
    private final Text nameText = new Text();
    private final Text additionalInfoText = new Text();
    private final Region tagIconRegion = createTagIconRegion();

    private final EventHandler<ContextMenuEvent> showContextMenu = createShowContextMenuEventHandler();

    /**
     * Creates a new tree-cell object responsible for the visual representation of a {@link BoundingBoxCategoryTreeItem}
     * or a {@link BoundingBoxTreeItem} in a {@link BoundingBoxTreeView}.
     */
    BoundingBoxTreeCell() {
        contextMenu.getItems().addAll(hideBoundingBoxMenuItem, deleteBoundingBoxMenuItem);
        deleteBoundingBoxMenuItem.setId(DELETE_CONTEXT_MENU_STYLE);

        nameText.getStyleClass().add(NAME_TEXT_STYLE);
        additionalInfoText.setId(INFO_TEXT_ID);

        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setUpInternalListeners();
    }

    @Override
    protected void updateItem(BoundingBoxView newBoundingBoxView, boolean empty) {
        BoundingBoxView oldItem = getItem();
        // If necessary remove the old item's context-menu event-handler.
        if(oldItem != null) {
            oldItem.removeEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, showContextMenu);
        }

        super.updateItem(newBoundingBoxView, empty);

        nameText.textProperty().unbind();
        nameText.setId(null);
        additionalInfoText.textProperty().unbind();
        tagIconRegion.visibleProperty().unbind();

        if(empty || newBoundingBoxView == null) {
            setGraphic(null);
            setContextMenu(null);
            setDraggedOver(false);
        } else {
            setGraphic(createContentBox());
            // Register the contextMenu with the cell.
            setContextMenu(contextMenu);
            // Register the contextMenu with the BoundingBoxView associated with the cell. This
            // allows to display the contextMenu by right-clicking on the bounding-box itself.
            newBoundingBoxView.setOnContextMenuRequested(showContextMenu);
        }
    }

    /**
     * Sets the dragged-over state.
     *
     * @param draggedOver true or false
     */
    void setDraggedOver(boolean draggedOver) {
        this.draggedOver.set(draggedOver);
    }

    /**
     * Returns the menu-item of the context-menu which allows
     * the user to delete the currently associated {@link BoundingBoxView}.
     *
     * @return the menu-item
     */
    MenuItem getDeleteBoundingBoxMenuItem() {
        return deleteBoundingBoxMenuItem;
    }

    private void setUpInternalListeners() {
        setOnMouseEntered(event -> {
            if(!isEmpty()) {
                fillBoundingBoxesOpaque();
            }
        });

        contextMenu.showingProperty().addListener(((observable, oldValue, newValue) -> {
            if(!isEmpty() && !newValue) {
                fillBoundingBoxesTransparent();
            }
        }));

        setOnMouseExited(event -> {
            if(!isEmpty() && !contextMenu.isShowing()) {
                fillBoundingBoxesTransparent();
            }
        });

        draggedOver.addListener((observable, oldValue, newValue) -> pseudoClassStateChanged(draggedOverPseudoClass, newValue));

        hideBoundingBoxMenuItem.setOnAction(event -> {
            if(getTreeItem() instanceof BoundingBoxCategoryTreeItem) {
                ((BoundingBoxCategoryTreeItem) getTreeItem()).setIconToggledOn(false);
            } else {
                ((BoundingBoxTreeItem) getTreeItem()).setIconToggledOn((false));
            }
        });
    }

    private void fillBoundingBoxesOpaque() {
        TreeItem<BoundingBoxView> treeItem = getTreeItem();

        if(treeItem instanceof BoundingBoxTreeItem) {
            if(!treeItem.getValue().isSelected()) {
                treeItem.getValue().fillOpaque();
            }
        } else {
            treeItem.getChildren().stream().filter(child -> !child.getValue().isSelected()).forEach(child -> child.getValue().fillOpaque());
        }
    }

    private void fillBoundingBoxesTransparent() {
        TreeItem<BoundingBoxView> treeItem = getTreeItem();

        if(treeItem instanceof BoundingBoxTreeItem) {
            if(!treeItem.getValue().isSelected()) {
                treeItem.getValue().fillTransparent();
            }
        } else {
            treeItem.getChildren().stream().filter(child -> !child.getValue().isSelected()).forEach(child -> child.getValue().fillTransparent());
        }
    }

    private HBox createContentBox() {
        final TreeItem<BoundingBoxView> treeItem = getTreeItem();
        final HBox content = new HBox(treeItem.getGraphic(), nameText);
        content.setId(TREE_CELL_CONTENT_ID);
        content.setAlignment(Pos.CENTER_LEFT);

        if(treeItem instanceof BoundingBoxTreeItem) {
            nameText.textProperty().bind(treeItem.getValue().getBoundingBoxCategory()
                    .nameProperty().concat(" ").concat(((BoundingBoxTreeItem) treeItem).getId()));
            tagIconRegion.visibleProperty().bind(Bindings.size(treeItem.getValue().getTags()).greaterThan(0));
            content.getChildren().add(tagIconRegion);
        } else {
            nameText.textProperty().bind(((BoundingBoxCategoryTreeItem) treeItem).getBoundingBoxCategory().nameProperty());
            nameText.setId(CATEGORY_NAME_TEXT_ID);
            additionalInfoText.textProperty().bind(Bindings.format("(%d)", treeItem.getChildren().size()));
            content.getChildren().add(additionalInfoText);
        }

        return content;
    }

    private Region createTagIconRegion() {
        Region region = new Region();
        region.setId(TAG_ICON_REGION_ID);
        return region;
    }

    private EventHandler<ContextMenuEvent> createShowContextMenuEventHandler() {
        return event -> contextMenu.show(getItem(), event.getScreenX(), event.getScreenY());
    }
}