package boundingboxeditor.ui;

import boundingboxeditor.BoundingBoxEditorTestBase;
import boundingboxeditor.model.ObjectCategory;
import javafx.geometry.Point2D;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.matcher.control.ComboBoxMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.testfx.api.FxAssert.verifyThat;

class ObjectTreeTests extends BoundingBoxEditorTestBase {
    @Start
    void start(Stage stage) {
        super.onStart(stage);
        controller.loadImageFiles(new File(getClass().getResource(TEST_IMAGE_FOLDER_PATH_1).getFile()));
    }

    @Test
    void onBoundingBoxesDrawnAndInteractedWith_ShouldCorrectlyDisplayTreeItems(FxRobot robot) {
        waitUntilCurrentImageIsLoaded();
        WaitForAsyncUtils.waitForFxEvents();

        // Enter new category
        enterNewCategory(robot, "Test");
        WaitForAsyncUtils.waitForFxEvents();

        /* ----Drawing---- */
        // Draw first bounding-box.
        moveRelativeToImageView(robot, new Point2D(0.25, 0.25), new Point2D(0.5, 0.5));
        WaitForAsyncUtils.waitForFxEvents();

        final List<TreeItem<Object>> topLevelTreeItems = mainView.getObjectTree().getRoot().getChildren();

        verifyThat(topLevelTreeItems.size(), Matchers.equalTo(1));
        verifyThat(topLevelTreeItems.get(0), Matchers.instanceOf(ObjectCategoryTreeItem.class));

        final ObjectCategoryTreeItem testCategoryTreeItem = (ObjectCategoryTreeItem) topLevelTreeItems.get(0);
        verifyThat(mainView.getObjectTree().getRow(testCategoryTreeItem), Matchers.equalTo(0));
        verifyThat(testCategoryTreeItem.getObjectCategory().getName(), Matchers.equalTo("Test"));
        verifyThat(testCategoryTreeItem.getChildren().size(), Matchers.equalTo(1));
        verifyThat(testCategoryTreeItem.getChildren().get(0), Matchers.instanceOf(BoundingBoxTreeItem.class));

        final BoundingBoxTreeItem firstTestChildTreeItem =
                (BoundingBoxTreeItem) testCategoryTreeItem.getChildren().get(0);

        verifyThat(((BoundingBoxView) firstTestChildTreeItem.getValue()).getObjectCategory(),
                   Matchers.equalTo(testCategoryTreeItem.getObjectCategory()));
        verifyThat(firstTestChildTreeItem.getId(), Matchers.equalTo(1));
        verifyThat(((BoundingBoxView) firstTestChildTreeItem.getValue()).isSelected(), Matchers.equalTo(true));

        // Draw second bounding-box.
        moveRelativeToImageView(robot, new Point2D(0.6, 0.25), new Point2D(0.85, 0.5));
        WaitForAsyncUtils.waitForFxEvents();
        // Still there should be only one category...
        verifyThat(topLevelTreeItems.size(), Matchers.equalTo(1));
        verifyThat(topLevelTreeItems.get(0), Matchers.equalTo(testCategoryTreeItem));

        // ...but now there should be two child tree-items corresponding to the two drawn bounding-boxes.
        verifyThat(testCategoryTreeItem.getChildren().size(), Matchers.equalTo(2));

        verifyThat(testCategoryTreeItem.getChildren().get(0), Matchers.equalTo(firstTestChildTreeItem));
        verifyThat(((BoundingBoxView) firstTestChildTreeItem.getValue()).isSelected(), Matchers.equalTo(false));

        final BoundingBoxTreeItem secondTestChildTreeItem =
                (BoundingBoxTreeItem) testCategoryTreeItem.getChildren().get(1);

        verifyThat(((BoundingBoxView) secondTestChildTreeItem.getValue()).getObjectCategory(),
                   Matchers.equalTo(testCategoryTreeItem.getObjectCategory()));
        verifyThat(secondTestChildTreeItem.getId(), Matchers.equalTo(2));
        verifyThat(((BoundingBoxView) secondTestChildTreeItem.getValue()).isSelected(), Matchers.equalTo(true));

        /* ----Hiding And Showing---- */
        // Hide first bounding-box by right-clicking.
        robot.rightClickOn("Test 1");
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Hide");

        verifyThat(firstTestChildTreeItem.isIconToggledOn(), Matchers.equalTo(false));
        verifyThat((BoundingBoxView) firstTestChildTreeItem.getValue(), NodeMatchers.isInvisible());

        // Hide second bounding-box by right-clicking.
        robot.rightClickOn("Test 2");
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Hide");

        verifyThat(secondTestChildTreeItem.isIconToggledOn(), Matchers.equalTo(false));
        verifyThat((BoundingBoxView) secondTestChildTreeItem.getValue(), NodeMatchers.isInvisible());

        // Now the parent-category-item's square-icon should be toggled off (because all children are toggled-off.
        verifyThat(testCategoryTreeItem.isIconToggledOn(), Matchers.equalTo(false));

        // Now toggle the category-item's icon to on.
        robot.clickOn(testCategoryTreeItem.getGraphic());
        verifyThat(testCategoryTreeItem.isIconToggledOn(), Matchers.equalTo(true));
        // This should toggle on all child-items.
        verifyThat(firstTestChildTreeItem.isIconToggledOn(), Matchers.equalTo(true));
        verifyThat((BoundingBoxView) firstTestChildTreeItem.getValue(), NodeMatchers.isVisible());
        verifyThat(secondTestChildTreeItem.isIconToggledOn(), Matchers.equalTo(true));
        verifyThat((BoundingBoxView) secondTestChildTreeItem.getValue(), NodeMatchers.isVisible());

        /* ----Nesting---- */
        // Draw another bounding-box belonging to the Test-category.
        moveRelativeToImageView(robot, new Point2D(0.25, 0.6), new Point2D(0.5, 0.85));
        WaitForAsyncUtils.waitForFxEvents();

        final BoundingBoxTreeItem thirdTestChildTreeItem =
                (BoundingBoxTreeItem) testCategoryTreeItem.getChildren().get(2);

        // Enter new category
        enterNewCategory(robot, "Dummy");
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat(mainView.getObjectCategoryTable().getSelectedCategory().getName(), Matchers.equalTo("Dummy"));

        // Draw a bounding-box belonging to the Dummy-category
        moveRelativeToImageView(robot, new Point2D(0.6, 0.6), new Point2D(0.85, 0.85));
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat(topLevelTreeItems.size(), Matchers.equalTo(2));
        verifyThat(topLevelTreeItems.get(0), Matchers.equalTo(testCategoryTreeItem));
        verifyThat(topLevelTreeItems.get(1), Matchers.instanceOf(ObjectCategoryTreeItem.class));

        final ObjectCategoryTreeItem dummyCategoryTreeItem = (ObjectCategoryTreeItem) topLevelTreeItems.get(1);
        verifyThat(dummyCategoryTreeItem.getObjectCategory().getName(), Matchers.equalTo("Dummy"));
        verifyThat(dummyCategoryTreeItem.getChildren().size(), Matchers.equalTo(1));

        final BoundingBoxTreeItem firstDummyChildTreeItem =
                (BoundingBoxTreeItem) dummyCategoryTreeItem.getChildren().get(0);
        verifyThat(firstDummyChildTreeItem.getId(), Matchers.equalTo(1));
        verifyThat(((BoundingBoxView) firstDummyChildTreeItem.getValue()).isSelected(), Matchers.equalTo(true));

        // Make the third child of the Test-category a nested part of the first item of the Dummy-category.
        robot.moveTo("Test 3").press(MouseButton.PRIMARY).moveTo("Dummy 1").release(MouseButton.PRIMARY);
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat(testCategoryTreeItem.getChildren().size(), Matchers.equalTo(2));
        verifyThat(dummyCategoryTreeItem.getChildren().size(), Matchers.equalTo(1));
        verifyThat(firstDummyChildTreeItem.getChildren().size(), Matchers.equalTo(1));
        verifyThat(firstDummyChildTreeItem.getChildren().get(0), Matchers.instanceOf(ObjectCategoryTreeItem.class));
        // The dragged item should be automatically selected after the completion of a successful drag.
        verifyThat(((BoundingBoxView) thirdTestChildTreeItem.getValue()).isSelected(), Matchers.equalTo(true));

        final ObjectCategoryTreeItem nestedTestCategoryTreeItem =
                (ObjectCategoryTreeItem) firstDummyChildTreeItem.getChildren().get(0);
        verifyThat(nestedTestCategoryTreeItem.getObjectCategory(),
                   Matchers.equalTo(testCategoryTreeItem.getObjectCategory()));
        verifyThat(nestedTestCategoryTreeItem.getChildren().size(), Matchers.equalTo(1));
        verifyThat(nestedTestCategoryTreeItem.getChildren().get(0), Matchers.equalTo(thirdTestChildTreeItem));

        /* ----Reloading On Image Change---- */
        // Switch to the next image.
        timeOutClickOn(robot, "#next-button");

        waitUntilCurrentImageIsLoaded();
        WaitForAsyncUtils.waitForFxEvents();
        // Now the tree-should be empty, as no bounding-boxes have been created for the current image.
        verifyThat(mainView.getObjectTree().getRoot().getChildren().size(), Matchers.equalTo(0));
        verifyThat(mainView.getCurrentBoundingShapes().size(), Matchers.equalTo(0));

        // Switch back to the previous image.
        timeOutClickOn(robot, "#previous-button");
        waitUntilCurrentImageIsLoaded();
        WaitForAsyncUtils.waitForFxEvents();
        // The old tree should have been exactly reconstructed.
        final List<TreeItem<Object>> newTopLevelTreeItems = mainView.getObjectTree().getRoot().getChildren();
        verifyThat(newTopLevelTreeItems, Matchers.equalTo(topLevelTreeItems));

        verifyThat(mainView.getImageFileListView().getSelectionModel()
                           .getSelectedItem().isHasAssignedBoundingShapes(), Matchers.is(true));

        /* ----Deleting---- */
        final ObjectCategoryTreeItem newTestCategoryTreeItem = (ObjectCategoryTreeItem) newTopLevelTreeItems.get(0);
        final ObjectCategoryTreeItem newDummyCategoryTreeItem = (ObjectCategoryTreeItem) newTopLevelTreeItems.get(1);
        final BoundingBoxTreeItem newFirstTestChildTreeItem =
                (BoundingBoxTreeItem) newTestCategoryTreeItem.getChildren().get(0);
        final BoundingBoxTreeItem newSecondTestChildTreeItem =
                (BoundingBoxTreeItem) newTestCategoryTreeItem.getChildren().get(1);

        // Delete first Test-bounding-box via context-menu on the tree-cell.
        robot.rightClickOn("Test 1");
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Delete");
        WaitForAsyncUtils.waitForFxEvents();

        // There should still be two categories...
        verifyThat(newTopLevelTreeItems.size(), Matchers.equalTo(2));
        // ...but one less Test-children.
        verifyThat(newTestCategoryTreeItem.getChildren().size(), Matchers.equalTo(1));

        verifyThat(mainView.getCurrentBoundingShapes(),
                   Matchers.not(Matchers.hasItem((BoundingBoxView) newFirstTestChildTreeItem.getValue())));
        // After the first bounding-box and its tree-item was deleted, the (formerly) second tree-item's id should have been updated.
        verifyThat(newSecondTestChildTreeItem.getId(), Matchers.equalTo(1));

        verifyThat(mainView.getImageFileListView().getSelectionModel()
                           .getSelectedItem().isHasAssignedBoundingShapes(), Matchers.is(true));

        // Delete second Test-bounding-box via the context-menu on the element itself.
        robot.rightClickOn((BoundingBoxView) newSecondTestChildTreeItem.getValue());
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Delete");
        WaitForAsyncUtils.waitForFxEvents();
        // Now just the Dummy-category item should be left.
        verifyThat(mainView.getObjectTree().getRoot().getChildren().size(), Matchers.equalTo(1));
        verifyThat(mainView.getCurrentBoundingShapes(),
                   Matchers.not(Matchers.hasItem((BoundingBoxView) newSecondTestChildTreeItem.getValue())));

        verifyThat(mainView.getImageFileListView().getSelectionModel()
                           .getSelectedItem().isHasAssignedBoundingShapes(), Matchers.is(true));

        // Delete Dummy-category-item. This should delete all children recursively.
        robot.rightClickOn(newDummyCategoryTreeItem.getGraphic());
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Delete");
        WaitForAsyncUtils.waitForFxEvents();
        // Now the tree-view should be empty (besides the invisible root-item).
        verifyThat(mainView.getObjectTree().getRoot().getChildren().size(), Matchers.equalTo(0));
        // There should be no remaining bounding-boxes.
        verifyThat(mainView.getCurrentBoundingShapes().size(), Matchers.equalTo(0));

        verifyThat(mainView.getImageFileListView().getSelectionModel()
                           .getSelectedItem().isHasAssignedBoundingShapes(), Matchers.is(false));

        /* ---- Object category change ---- */
        moveRelativeToImageView(robot, new Point2D(0.25, 0.6), new Point2D(0.5, 0.85));
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat(mainView.getObjectTree().getRoot().getChildren().size(), Matchers.equalTo(1));
        verifyThat(mainView.getCurrentBoundingShapes().size(), Matchers.equalTo(1));
        verifyThat(mainView.getCurrentBoundingShapes().get(0).getViewData().getObjectCategory().getName(),
                   Matchers.equalTo("Dummy"));

        robot.rightClickOn("Dummy 1");
        WaitForAsyncUtils.waitForFxEvents();
        timeOutClickOn(robot, "Change Category");
        WaitForAsyncUtils.waitForFxEvents();

        Assertions.assertDoesNotThrow(() -> WaitForAsyncUtils.waitFor(TIMEOUT_DURATION_IN_SEC, TimeUnit.SECONDS,
                                                                      () -> getTopModalStage(robot,
                                                                                             "Change Category") !=
                                                                              null),
                                      "Expected change category dialog did not open within " + TIMEOUT_DURATION_IN_SEC +
                                              " sec.");

        final Stage changeCategoryStage = getTopModalStage(robot, "Change Category");
        verifyThat(changeCategoryStage, Matchers.notNullValue());

        final DialogPane changeCategoryDialog = (DialogPane) changeCategoryStage.getScene().getRoot();
        verifyThat(changeCategoryDialog.getHeaderText(), Matchers.equalTo("Select new Category (current: \"Dummy\")"));
        verifyThat(changeCategoryDialog.getContentText(), Matchers.equalTo("New Category:"));
        verifyThat(model.getObjectCategories(), Matchers.hasSize(2));
        verifyThat(model.getObjectCategories().stream().map(ObjectCategory::getName).collect(Collectors.toList()),
                   Matchers.containsInRelativeOrder("Test", "Dummy"));

        ObjectCategory testCategory = model.getObjectCategories().get(0);
        ObjectCategory dummyCategory = model.getObjectCategories().get(1);

        Assertions.assertDoesNotThrow(() -> WaitForAsyncUtils.waitFor(TIMEOUT_DURATION_IN_SEC, TimeUnit.SECONDS,
                                                                      () -> robot.from(changeCategoryDialog)
                                                                                 .lookup(".combo-box").tryQuery()
                                                                                 .isPresent()),
                                      "Expected combo-box not found within " + TIMEOUT_DURATION_IN_SEC + " sec.");
        WaitForAsyncUtils.waitForFxEvents();

        ComboBox<ObjectCategory> comboBox = robot.from(changeCategoryDialog).lookup(".combo-box").queryComboBox();

        verifyThat(comboBox, ComboBoxMatchers.containsExactlyItemsInOrder(testCategory, dummyCategory));
        verifyThat(comboBox, ComboBoxMatchers.hasSelectedItem(dummyCategory));

        robot.clickOn(comboBox);
        WaitForAsyncUtils.waitForFxEvents();

        Assertions.assertDoesNotThrow(
                () -> WaitForAsyncUtils.waitFor(TIMEOUT_DURATION_IN_SEC, TimeUnit.SECONDS, comboBox::isShowing),
                "Combo-box not shown within " + TIMEOUT_DURATION_IN_SEC + " sec.");
        WaitForAsyncUtils.waitForFxEvents();

        robot.type(KeyCode.UP);
        WaitForAsyncUtils.waitForFxEvents();

        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat(comboBox, ComboBoxMatchers.hasSelectedItem(testCategory));

        timeOutLookUpInStageAndClickOn(robot, changeCategoryStage, "OK");
        WaitForAsyncUtils.waitForFxEvents();

        Assertions.assertDoesNotThrow(() -> WaitForAsyncUtils.waitFor(TIMEOUT_DURATION_IN_SEC, TimeUnit.SECONDS,
                                                                      () -> getTopModalStage(robot,
                                                                                             "Change Category") ==
                                                                              null),
                                      "Expected change category dialog did not close within " +
                                              TIMEOUT_DURATION_IN_SEC + " sec.");

        verifyThat(mainView.getObjectTree().getRoot().getChildren().size(), Matchers.equalTo(1));
        verifyThat(mainView.getCurrentBoundingShapes().size(), Matchers.equalTo(1));
        verifyThat(mainView.getCurrentBoundingShapes().get(0).getViewData().getObjectCategory(),
                   Matchers.equalTo(testCategory));
        verifyThat(model.getCategoryToAssignedBoundingShapesCountMap().get("Test"), Matchers.equalTo(1));
        verifyThat(model.getCategoryToAssignedBoundingShapesCountMap().get("Dummy"), Matchers.equalTo(0));
    }
}
