package boundingboxeditor.model.io;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class YOLOSaveStrategy implements ImageAnnotationSaveStrategy {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    private static final String YOLO_ANNOTATION_FILE_EXTENSION = ".txt";
    private static final String OBJECT_DATA_FILE_NAME = "object.data";
    private Path saveFolderPath;
    private List<String> categories;

    @Override
    public IOResult save(ImageAnnotationData annotations, Path saveFolderPath, DoubleProperty progress) {
        this.saveFolderPath = saveFolderPath;
        this.categories = annotations.getCategoryToShapeCountMap().entrySet().stream()
                .filter(stringIntegerEntry -> stringIntegerEntry.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages = Collections.synchronizedList(new ArrayList<>());

        try {
            createObjectDataFile();
        } catch(IOException e) {
            unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(OBJECT_DATA_FILE_NAME, e.getMessage()));
        }

        int totalNrOfAnnotations = annotations.getImageAnnotations().size();
        AtomicInteger nrProcessedAnnotations = new AtomicInteger(0);

        annotations.getImageAnnotations().parallelStream().forEach(annotation -> {
            try {
                createAnnotationFile(annotation);
            } catch(IOException e) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotation.getImageFileName(), e.getMessage()));
            }

            progress.set(1.0 * nrProcessedAnnotations.incrementAndGet() / totalNrOfAnnotations);
        });

        return new IOResult(
                IOResult.OperationType.ANNOTATION_SAVING,
                totalNrOfAnnotations - unParsedFileErrorMessages.size(),
                unParsedFileErrorMessages
        );
    }

    private void createObjectDataFile() throws IOException {
        try(BufferedWriter fileWriter = Files.newBufferedWriter(saveFolderPath
                .resolve(OBJECT_DATA_FILE_NAME))) {
            for(int i = 0; i < categories.size() - 1; ++i) {
                fileWriter.write(categories.get(i));
                fileWriter.newLine();
            }

            if(!categories.isEmpty()) {
                fileWriter.write(categories.get(categories.size() - 1));
            }
        }
    }

    private void createAnnotationFile(ImageAnnotation annotation) throws IOException {
        String imageFileName = annotation.getImageFileName();
        String imageFileNameWithoutExtension = imageFileName.substring(0, imageFileName.lastIndexOf('.'));

        try(BufferedWriter fileWriter = Files.newBufferedWriter(saveFolderPath
                .resolve(imageFileNameWithoutExtension + YOLO_ANNOTATION_FILE_EXTENSION))) {
            List<BoundingShapeData> boundingShapeDataList = annotation.getBoundingShapeData();

            for(int i = 0; i < boundingShapeDataList.size() - 1; ++i) {
                BoundingShapeData boundingShapeData = boundingShapeDataList.get(i);

                if(boundingShapeData instanceof BoundingBoxData) {
                    fileWriter.write(createBoundingBoxDataEntry((BoundingBoxData) boundingShapeData));
                    fileWriter.newLine();
                }
            }

            if(!boundingShapeDataList.isEmpty()) {
                BoundingShapeData lastShapeData = boundingShapeDataList.get(boundingShapeDataList.size() - 1);

                if(lastShapeData instanceof BoundingBoxData) {
                    fileWriter.write(createBoundingBoxDataEntry((BoundingBoxData) lastShapeData));
                }
            }
        }
    }

    private String createBoundingBoxDataEntry(BoundingBoxData boundingBoxData) {
        int categoryIndex = categories.indexOf(boundingBoxData.getCategoryName());

        Bounds relativeBounds = boundingBoxData.getRelativeBoundsInImage();

        String xMidRelative = DECIMAL_FORMAT.format(relativeBounds.getCenterX());
        String yMidRelative = DECIMAL_FORMAT.format(relativeBounds.getCenterY());
        String widthRelative = DECIMAL_FORMAT.format(relativeBounds.getWidth());
        String heightRelative = DECIMAL_FORMAT.format(relativeBounds.getHeight());

        return StringUtils.join(List.of(categoryIndex, xMidRelative, yMidRelative, widthRelative, heightRelative), " ");
    }
}