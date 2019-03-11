package BoundingboxEditor;

import com.sun.javafx.iio.ImageMetadata;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Model {
    private static final String BOUNDING_BOX_COORDINATES_PATTERN = "#0.0000";
    private static final String[] imageExtensions = {".jpg", ".bmp", ".png"};
    private static final int MAX_DIRECTORY_DEPTH = 1;
    private static final DecimalFormat numberFormat = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ENGLISH);

    private ObservableList<File> imageFileList = FXCollections.observableArrayList();
    private ObservableList<BoundingBoxCategory> boundingBoxCategories = FXCollections.observableArrayList();
    private final ObservableList<ImageAnnotationDataElement> imageAnnotations = FXCollections.observableArrayList();
    private final ObservableList<ImageMetaData> imageMetaData = FXCollections.observableArrayList();
    private IntegerProperty fileIndex = new SimpleIntegerProperty(0);
    private IntegerProperty imageFileListSize = new SimpleIntegerProperty(0);
    private BooleanBinding hasNextFile;
    private BooleanBinding hasPreviousFile;
    private ObjectBinding<File> currentFile;
    private HashSet<String> boundingBoxCategoryNames = new HashSet<>();

    public Model() {
        BoundingBoxCategory defaultCategory = new BoundingBoxCategory();
        boundingBoxCategories.add(defaultCategory);
        boundingBoxCategoryNames.add(defaultCategory.getName());
        numberFormat.applyPattern(BOUNDING_BOX_COORDINATES_PATTERN);
    }

    public void setImageFileListFromPath(Path path) throws Exception {
        imageFileList = FXCollections.observableArrayList(
                Files.walk(path, MAX_DIRECTORY_DEPTH)
                        .filter(p -> Arrays.stream(imageExtensions).anyMatch(p.toString()::endsWith))
                        .map(p -> new File(p.toString()))
                        .collect(Collectors.toList())
        );

        if(imageFileList.isEmpty()) {
            throw new NoValidImagesException(String.format("The path \"%s\" does not contain any valid images.",
                    path.toString()));
        }

        fileIndex.set(0);
        imageFileListSize.bind(Bindings.size(imageFileList));
        hasNextFile = fileIndex.lessThan(imageFileListSize.subtract(1));
        hasPreviousFile = fileIndex.greaterThan(0);
        currentFile = Bindings.valueAt(imageFileList, fileIndex);
    }

    public Image getCurrentImage() {
        return getImageFromFile(currentFile.get());
    }

    public IntegerProperty fileIndexProperty() {
        return fileIndex;
    }

    public ObservableList<BoundingBoxCategory> getBoundingBoxCategories() {
        return boundingBoxCategories;
    }

    public void incrementFileIndex() {
        fileIndex.set(fileIndex.get() + 1);
    }

    public void decrementFileIndex() {
        fileIndex.set(fileIndex.get() - 1);
    }

    public IntegerProperty fileListSizeProperty() {
        return imageFileListSize;
    }

    public BooleanBinding hasNextFileBinding() {
        return hasNextFile;
    }

    public BooleanBinding hasPreviousFileBinding() {
        return hasPreviousFile;
    }

    public Boolean isHasNextFile() {
        return hasNextFile.get();
    }

    public Boolean isHasPreviousFile() {
        return hasPreviousFile.get();
    }

    public BooleanBinding hasPreviousFileProperty() {
        return hasPreviousFile;
    }

    public BooleanBinding hasNextFileProperty() {
        return hasNextFile;
    }

    public String getCurrentImageFilePath() {
        final String imagePath = getCurrentImage().getUrl()
                .replace("/", "\\")
                .replace("%20", " ");
        return imagePath.substring(imagePath.indexOf("\\") + 1);
    }

    public HashSet<String> getBoundingBoxCategoryNames() {
        return boundingBoxCategoryNames;
    }

    public ObservableList<File> getImageFileList() {
        return imageFileList;
    }

    public ObservableList<ImageAnnotationDataElement> getImageAnnotations() {
        return imageAnnotations;
    }

    private Image getImageFromFile(File file) {
        return new Image(file.toURI().toString(), true);
    }
}
