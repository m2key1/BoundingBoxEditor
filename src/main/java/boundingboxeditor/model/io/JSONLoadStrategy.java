package boundingboxeditor.model.io;

import boundingboxeditor.model.ImageMetaData;
import boundingboxeditor.model.Model;
import boundingboxeditor.model.ObjectCategory;
import boundingboxeditor.utils.ColorUtils;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JSONLoadStrategy implements ImageAnnotationLoadStrategy {
    private static final String OBJECT_CATEGORY_NAME_SERIALIZED_NAME = "name";
    private static final String OBJECT_CATEGORY_SERIALIZED_NAME = "category";
    private static final String OBJECT_COLOR_SERIALIZED_NAME = "color";
    private static final String IMAGE_META_DATA_SERIALIZED_NAME = "image";
    private static final String BOUNDING_SHAPE_DATA_SERIALIZED_NAME = "objects";
    private static final String BOUNDS_MIN_X_SERIALIZED_NAME = "minX";
    private static final String BOUNDS_MIN_Y_SERIALIZED_NAME = "minY";
    private static final String BOUNDS_MAX_X_SERIALIZED_NAME = "maxX";
    private static final String BOUNDS_MAX_Y_SERIALIZED_NAME = "maxY";
    private static final String IMAGE_FILE_NAME_SERIALIZED_NAME = "fileName";
    private static final String BOUNDING_BOX_SERIALIZED_NAME = "bndbox";
    private static final String BOUNDING_POLYGON_SERIALIZED_NAME = "polygon";
    private static final String TAGS_SERIALIZED_NAME = "tags";
    private static final String PARTS_SERIALIZED_NAME = "parts";
    private static final String IMAGE_ATTRIBUTION_MESSAGE_PART = " element in annotation for image ";
    private static final String MISSING_CATEGORY_ERROR_MESSAGE = "Missing category element in ";
    private static final String INVALID_COORDINATES_ERROR_MESSAGE = "Invalid coordinate value(s) in ";
    private static final String INVALID_COORDINATE_ERROR_MESSAGE = "Invalid coordinate value for ";
    private static final String INVALID_COORDINATE_NUMBER_ERROR_MESSAGE = "Invalid number of coordinates in ";
    private static final String INVALID_TAGS_ERROR_MESSAGE = "Invalid tags value(s) in ";
    private static final String INVALID_PARTS_ERROR_MESSAGE = "Invalid parts value(s) in ";
    private static final String MISSING_IMAGE_FILE_NAME_ERROR_MESSAGE = "Missing image fileName element.";
    private static final String MISSING_IMAGES_FIELD_ERROR_MESSAGE = "Missing images element.";
    private static final String ELEMENT_LOCATION_ERROR_MESSAGE_PART = " element in ";
    private static final String MISSING_OBJECTS_FIELD_ERROR_MESSAGE =
            "Missing objects" + IMAGE_ATTRIBUTION_MESSAGE_PART;
    private static final String MISSING_CATEGORY_NAME_ERROR_MESSAGE =
            "Missing category name" + IMAGE_ATTRIBUTION_MESSAGE_PART;
    private static final String MISSING_BOUNDING_SHAPE_ERROR_MESSAGE =
            "Missing bndbox or polygon" + IMAGE_ATTRIBUTION_MESSAGE_PART;
    private static final String INVALID_COLOR_ERROR_MESSAGE = "Invalid color";

    @Override
    public IOResult load(Model model, Path path, DoubleProperty progress) throws IOException {
        final Set<String> fileNamesToLoad = model.getImageFileNameSet();
        final Map<String, Integer> boundingShapeCountPerCategory =
                new ConcurrentHashMap<>(model.getCategoryToAssignedBoundingShapesCountMap());
        final Map<String, ObjectCategory> nameToObjectCategoryMap =
                new ConcurrentHashMap<>(model.getObjectCategories().stream()
                                             .collect(Collectors.toMap(ObjectCategory::getName, Function.identity())));
        final List<IOResult.ErrorInfoEntry> errorInfoEntries = Collections.synchronizedList(new ArrayList<>());
        final Map<String, ImageMetaData> imageMetaDataMap = model.getImageFileNameToMetaDataMap();
        final AtomicReference<String> currentFilename = new AtomicReference<>();
        final String annotationFileName = path.getFileName().toString();

        final java.lang.reflect.Type imageAnnotationListType =
                new TypeToken<List<ImageAnnotation>>() {}.getType();

        final Gson gson = new GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(imageAnnotationListType, new ImageAnnotationDataListDeserializer(progress))
                .registerTypeAdapter(ImageAnnotation.class,
                                     new ImageAnnotationDeserializer(errorInfoEntries, annotationFileName))
                .registerTypeAdapter(ImageMetaData.class,
                                     new ImageMetaDataDeserializer(errorInfoEntries, annotationFileName,
                                                                   currentFilename,
                                                                   fileNamesToLoad,
                                                                   imageMetaDataMap))
                .registerTypeAdapter(BoundingShapeData.class, new BoundingShapeDataDeserializer(errorInfoEntries,
                                                                                                currentFilename,
                                                                                                annotationFileName))
                .registerTypeAdapter(BoundingBoxData.class, new BoundingBoxDataDeserializer(errorInfoEntries,
                                                                                            currentFilename,
                                                                                            annotationFileName,
                                                                                            nameToObjectCategoryMap,
                                                                                            boundingShapeCountPerCategory))
                .registerTypeAdapter(BoundingPolygonData.class, new BoundingPolygonDataDeserializer(errorInfoEntries,
                                                                                                    currentFilename,
                                                                                                    annotationFileName,
                                                                                                    nameToObjectCategoryMap,
                                                                                                    boundingShapeCountPerCategory))
                .registerTypeAdapter(ObjectCategory.class,
                                     new ObjectCategoryDeserializer(errorInfoEntries, currentFilename,
                                                                    annotationFileName))
                .registerTypeHierarchyAdapter(Bounds.class, new BoundsDeserializer(errorInfoEntries, currentFilename,
                                                                                   annotationFileName))
                .create();

        try(final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final List<ImageAnnotation> imageAnnotations = gson.fromJson(reader, imageAnnotationListType);

            if(!imageAnnotations.isEmpty()) {
                model.getObjectCategories().setAll(nameToObjectCategoryMap.values());
                model.getCategoryToAssignedBoundingShapesCountMap().putAll(boundingShapeCountPerCategory);
                model.updateImageAnnotations(imageAnnotations);
            }

            return new IOResult(
                    IOResult.OperationType.ANNOTATION_IMPORT,
                    imageAnnotations.size(),
                    errorInfoEntries
            );
        }
    }

    private static List<String> parseBoundingShapeTags(JsonDeserializationContext context, JsonObject jsonObject,
                                                       List<IOResult.ErrorInfoEntry> unparsedFileErrorMessages,
                                                       String elementName,
                                                       String annotationFileName,
                                                       String currentFileName) {
        final java.lang.reflect.Type tagsType = new TypeToken<List<String>>() {}.getType();

        List<String> tags;

        try {
            tags = context.deserialize(jsonObject.get(TAGS_SERIALIZED_NAME), tagsType);
        } catch(JsonParseException e) {
            unparsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                      INVALID_TAGS_ERROR_MESSAGE +
                                                                              elementName +
                                                                              IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                              currentFileName + "."));
            return null;
        }

        if(tags == null) {
            tags = new ArrayList<>();
        } else {
            tags.removeIf(tag -> tag == null || tag.isBlank());
        }

        return tags;
    }

    private static List<BoundingShapeData> parseBoundingShapeDataParts(JsonDeserializationContext context,
                                                                       JsonObject jsonObject,
                                                                       List<IOResult.ErrorInfoEntry> unparsedFileErrorMessages,
                                                                       String elementName, String annotationFileName,
                                                                       String currentFileName) {
        final java.lang.reflect.Type partsType = new TypeToken<List<BoundingShapeData>>() {}.getType();

        List<BoundingShapeData> parts;

        try {
            parts = context.deserialize(jsonObject.get(PARTS_SERIALIZED_NAME), partsType);
        } catch(JsonParseException e) {
            unparsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                      INVALID_PARTS_ERROR_MESSAGE +
                                                                              elementName +
                                                                              IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                              currentFileName + "."));
            return null;
        }

        if(parts == null) {
            parts = Collections.emptyList();
        } else {
            parts.removeIf(Objects::isNull);

            if(parts.isEmpty()) {
                parts = Collections.emptyList();
            }
        }

        return parts;
    }

    private static boolean isValidRelativeCoordinate(double value) {
        return 0 <= value && value <= 1;
    }

    private static ObjectCategory parseObjectCategory(JsonDeserializationContext context,
                                                      JsonObject jsonObject,
                                                      List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                                      String elementName,
                                                      String annotationFileName,
                                                      String currentFileName) {
        if(!jsonObject.has(OBJECT_CATEGORY_SERIALIZED_NAME)) {
            unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                      MISSING_CATEGORY_ERROR_MESSAGE +
                                                                              elementName +
                                                                              IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                              currentFileName + "."));
            return null;
        }

        return context.deserialize(jsonObject.get(OBJECT_CATEGORY_SERIALIZED_NAME),
                                   ObjectCategory.class);
    }

    private static class BoundingShapeDataDeserializer implements JsonDeserializer<BoundingShapeData> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final AtomicReference<String> currentFileName;
        private final String annotationFileName;

        public BoundingShapeDataDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                             AtomicReference<String> currentFileName,
                                             String annotationFileName) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.currentFileName = currentFileName;
            this.annotationFileName = annotationFileName;
        }

        @Override
        public BoundingShapeData deserialize(JsonElement json, java.lang.reflect.Type type,
                                             JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            BoundingShapeData boundingShapeData = null;

            if(jsonObject.has(BOUNDING_BOX_SERIALIZED_NAME)) {
                boundingShapeData = context.deserialize(json, BoundingBoxData.class);
            } else if(jsonObject.has(BOUNDING_POLYGON_SERIALIZED_NAME)) {
                boundingShapeData = context.deserialize(json, BoundingPolygonData.class);
            } else {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          MISSING_BOUNDING_SHAPE_ERROR_MESSAGE +
                                                                                  currentFileName
                                                                                          .get() + "."));
            }

            return boundingShapeData;
        }
    }

    private static class ObjectCategoryDeserializer implements JsonDeserializer<ObjectCategory> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final AtomicReference<String> currentFilename;
        private final String annotationFileName;

        public ObjectCategoryDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                          AtomicReference<String> currentFilename,
                                          String annotationFileName) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.currentFilename = currentFilename;
            this.annotationFileName = annotationFileName;
        }

        @Override
        public ObjectCategory deserialize(JsonElement json, java.lang.reflect.Type type,
                                          JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            if(!jsonObject.has(OBJECT_CATEGORY_NAME_SERIALIZED_NAME)) {
                unParsedFileErrorMessages
                        .add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                         MISSING_CATEGORY_NAME_ERROR_MESSAGE + currentFilename.get() +
                                                                 "."));
                return null;
            }

            final String categoryName = jsonObject.get(OBJECT_CATEGORY_NAME_SERIALIZED_NAME).getAsString();

            Color categoryColor;

            if(jsonObject.has(OBJECT_COLOR_SERIALIZED_NAME)) {
                try {
                    categoryColor = Color.web(jsonObject.get(OBJECT_COLOR_SERIALIZED_NAME).getAsString());
                } catch(IllegalArgumentException | ClassCastException e) {
                    unParsedFileErrorMessages
                            .add(new IOResult.ErrorInfoEntry(annotationFileName, INVALID_COLOR_ERROR_MESSAGE +
                                    IMAGE_ATTRIBUTION_MESSAGE_PART + currentFilename.get() + "."));
                    return null;
                }
            } else {
                categoryColor = ColorUtils.createRandomColor();
            }

            return new ObjectCategory(categoryName, categoryColor);
        }
    }

    private static class BoundsDeserializer implements JsonDeserializer<Bounds> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final AtomicReference<String> currentFilename;
        private final String annotationFileName;

        public BoundsDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                  AtomicReference<String> currentFilename, String annotationFileName) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.currentFilename = currentFilename;
            this.annotationFileName = annotationFileName;
        }

        @Override
        public Bounds deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            final Double minX = parseCoordinateField(jsonObject, BOUNDS_MIN_X_SERIALIZED_NAME);

            if(minX == null) {
                return null;
            }

            final Double minY = parseCoordinateField(jsonObject, BOUNDS_MIN_Y_SERIALIZED_NAME);

            if(minY == null) {
                return null;
            }

            final Double maxX = parseCoordinateField(jsonObject, BOUNDS_MAX_X_SERIALIZED_NAME);

            if(maxX == null) {
                return null;
            }

            final Double maxY = parseCoordinateField(jsonObject, BOUNDS_MAX_Y_SERIALIZED_NAME);

            if(maxY == null) {
                return null;
            }

            return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
        }

        private Double parseCoordinateField(JsonObject jsonObject, String name) {
            if(!jsonObject.has(name)) {
                unParsedFileErrorMessages
                        .add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                         "Missing " + name + ELEMENT_LOCATION_ERROR_MESSAGE_PART +
                                                                 BOUNDING_BOX_SERIALIZED_NAME +
                                                                 IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                 currentFilename.get() + "."));
                return null;
            }

            double value;

            try {
                value = jsonObject.get(name).getAsDouble();
            } catch(ClassCastException | NumberFormatException e) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          INVALID_COORDINATE_ERROR_MESSAGE + name +
                                                                                  ELEMENT_LOCATION_ERROR_MESSAGE_PART +
                                                                                  BOUNDING_BOX_SERIALIZED_NAME +
                                                                                  IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                                  currentFilename.get() + "."));
                return null;
            }

            if(!isValidRelativeCoordinate(value)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          INVALID_COORDINATE_ERROR_MESSAGE + name +
                                                                                  ELEMENT_LOCATION_ERROR_MESSAGE_PART +
                                                                                  BOUNDING_BOX_SERIALIZED_NAME +
                                                                                  IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                                  currentFilename.get() + "."));
                return null;
            }

            return value;
        }
    }

    private static class ImageMetaDataDeserializer implements JsonDeserializer<ImageMetaData> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final String annotationFileName;
        private final AtomicReference<String> currentFileName;
        private final Set<String> fileNamesToLoad;
        private final Map<String, ImageMetaData> imageMetaDataMap;

        public ImageMetaDataDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                         String annotationFileName, AtomicReference<String> currentFileName,
                                         Set<String> fileNamesToLoad,
                                         Map<String, ImageMetaData> imageMetaDataMap) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.annotationFileName = annotationFileName;
            this.currentFileName = currentFileName;
            this.fileNamesToLoad = fileNamesToLoad;
            this.imageMetaDataMap = imageMetaDataMap;
        }

        @Override
        public ImageMetaData deserialize(JsonElement json, java.lang.reflect.Type type,
                                         JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            if(!jsonObject.has(IMAGE_FILE_NAME_SERIALIZED_NAME)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          MISSING_IMAGE_FILE_NAME_ERROR_MESSAGE));
                return null;
            }

            final String imageFileName = jsonObject.get(IMAGE_FILE_NAME_SERIALIZED_NAME).getAsString();

            if(!fileNamesToLoad.contains(imageFileName)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          "Image " + imageFileName +
                                                                                  " does not belong to currently loaded image files."));
                return null;
            }

            currentFileName.set(imageFileName);

            return imageMetaDataMap.getOrDefault(imageFileName,
                                                 new ImageMetaData(imageFileName));
        }
    }

    private static class ImageAnnotationDeserializer implements JsonDeserializer<ImageAnnotation> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final String annotationFileName;

        public ImageAnnotationDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                           String annotationFileName) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.annotationFileName = annotationFileName;
        }

        @Override
        public ImageAnnotation deserialize(JsonElement json, java.lang.reflect.Type type,
                                           JsonDeserializationContext context) {

            if(!json.getAsJsonObject().has(IMAGE_META_DATA_SERIALIZED_NAME)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          MISSING_IMAGES_FIELD_ERROR_MESSAGE));
                return null;
            }

            final ImageMetaData imageMetaData =
                    context.deserialize(json.getAsJsonObject().get(IMAGE_META_DATA_SERIALIZED_NAME),
                                        ImageMetaData.class);

            if(imageMetaData == null) {
                return null;
            }

            if(!json.getAsJsonObject().has(BOUNDING_SHAPE_DATA_SERIALIZED_NAME)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          MISSING_OBJECTS_FIELD_ERROR_MESSAGE
                                                                                  + imageMetaData.getFileName() + "."));
                return null;
            }

            final java.lang.reflect.Type boundingShapeDataListType = new TypeToken<List<BoundingShapeData>>() {
            }.getType();
            final List<BoundingShapeData> boundingShapeDataList =
                    context.deserialize(json.getAsJsonObject().get(BOUNDING_SHAPE_DATA_SERIALIZED_NAME),
                                        boundingShapeDataListType);

            boundingShapeDataList.removeIf(Objects::isNull);

            if(boundingShapeDataList.isEmpty()) {
                return null;
            }

            return new ImageAnnotation(imageMetaData, boundingShapeDataList);
        }
    }

    private static class BoundingBoxDataDeserializer implements JsonDeserializer<BoundingBoxData> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final AtomicReference<String> currentFileName;
        private final String annotationFileName;
        private final Map<String, ObjectCategory> nameToObjectCategoryMap;
        private final Map<String, Integer> boundingShapeCountPerCategory;

        public BoundingBoxDataDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                           AtomicReference<String> currentFileName, String annotationFileName,
                                           Map<String, ObjectCategory> nameToObjectCategoryMap,
                                           Map<String, Integer> boundingShapeCountPerCategory) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.currentFileName = currentFileName;
            this.annotationFileName = annotationFileName;
            this.nameToObjectCategoryMap = nameToObjectCategoryMap;
            this.boundingShapeCountPerCategory = boundingShapeCountPerCategory;
        }

        @Override
        public BoundingBoxData deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                           JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            final ObjectCategory parsedObjectCategory =
                    parseObjectCategory(context, jsonObject, unParsedFileErrorMessages,
                                        BOUNDING_BOX_SERIALIZED_NAME, annotationFileName,
                                        currentFileName.get());

            if(parsedObjectCategory == null) {
                return null;
            }

            final Bounds bounds =
                    context.deserialize(json.getAsJsonObject().get(BOUNDING_BOX_SERIALIZED_NAME), Bounds.class);

            if(bounds == null) {
                return null;
            }

            final List<String> tags = parseBoundingShapeTags(context, jsonObject, unParsedFileErrorMessages,
                                                             BOUNDING_BOX_SERIALIZED_NAME, annotationFileName,
                                                             currentFileName.get());

            if(tags == null) {
                return null;
            }

            final List<BoundingShapeData> parts = parseBoundingShapeDataParts(context, jsonObject,
                                                                              unParsedFileErrorMessages,
                                                                              BOUNDING_BOX_SERIALIZED_NAME,
                                                                              annotationFileName,
                                                                              currentFileName.get());

            if(parts == null) {
                return null;
            }

            final ObjectCategory objectCategory =
                    nameToObjectCategoryMap.computeIfAbsent(parsedObjectCategory.getName(),
                                                            key -> parsedObjectCategory);

            boundingShapeCountPerCategory.merge(objectCategory.getName(), 1, Integer::sum);

            final BoundingBoxData boundingBoxData = new BoundingBoxData(objectCategory, bounds, tags);
            boundingBoxData.setParts(parts);

            return boundingBoxData;
        }

    }

    private static class BoundingPolygonDataDeserializer implements JsonDeserializer<BoundingPolygonData> {
        private final List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;
        private final AtomicReference<String> currentFilename;
        private final String annotationFileName;
        private final Map<String, ObjectCategory> nameToObjectCategoryMap;
        private final Map<String, Integer> boundingShapeCountPerCategory;

        public BoundingPolygonDataDeserializer(List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages,
                                               AtomicReference<String> currentFilename,
                                               String annotationFileName,
                                               Map<String, ObjectCategory> nameToObjectCategoryMap,
                                               Map<String, Integer> boundingShapeCountPerCategory) {
            this.unParsedFileErrorMessages = unParsedFileErrorMessages;
            this.currentFilename = currentFilename;
            this.annotationFileName = annotationFileName;
            this.nameToObjectCategoryMap = nameToObjectCategoryMap;
            this.boundingShapeCountPerCategory = boundingShapeCountPerCategory;
        }

        @Override
        public BoundingPolygonData deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                               JsonDeserializationContext context) {
            final JsonObject jsonObject = json.getAsJsonObject();

            final ObjectCategory parsedObjectCategory = parseObjectCategory(context, jsonObject,
                                                                            unParsedFileErrorMessages,
                                                                            BOUNDING_POLYGON_SERIALIZED_NAME,
                                                                            annotationFileName, currentFilename.get());
            context.deserialize(json.getAsJsonObject().get(OBJECT_CATEGORY_SERIALIZED_NAME),
                                ObjectCategory.class);

            if(parsedObjectCategory == null) {
                return null;
            }

            final java.lang.reflect.Type pointsType = new TypeToken<List<Double>>() {
            }.getType();
            List<Double> points;

            try {
                points = context.deserialize(json.getAsJsonObject().get(BOUNDING_POLYGON_SERIALIZED_NAME), pointsType);
            } catch(JsonParseException | NumberFormatException e) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          INVALID_COORDINATES_ERROR_MESSAGE +
                                                                                  BOUNDING_POLYGON_SERIALIZED_NAME +
                                                                                  IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                                  currentFilename.get() + "."));
                return null;
            }

            if(points == null || points.isEmpty() || points.size() % 2 != 0) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          INVALID_COORDINATE_NUMBER_ERROR_MESSAGE +
                                                                                  BOUNDING_POLYGON_SERIALIZED_NAME +
                                                                                  IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                                  currentFilename.get() + "."));
                return null;
            }

            if(!points.stream().allMatch(JSONLoadStrategy::isValidRelativeCoordinate)) {
                unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(annotationFileName,
                                                                          INVALID_COORDINATES_ERROR_MESSAGE +
                                                                                  BOUNDING_POLYGON_SERIALIZED_NAME +
                                                                                  IMAGE_ATTRIBUTION_MESSAGE_PART +
                                                                                  currentFilename.get() + "."));
                return null;
            }

            final List<String> tags = parseBoundingShapeTags(context, jsonObject, unParsedFileErrorMessages,
                                                             BOUNDING_POLYGON_SERIALIZED_NAME, annotationFileName,
                                                             currentFilename.get());

            if(tags == null) {
                return null;
            }

            final List<BoundingShapeData> parts = parseBoundingShapeDataParts(context, jsonObject,
                                                                              unParsedFileErrorMessages,
                                                                              BOUNDING_POLYGON_SERIALIZED_NAME,
                                                                              annotationFileName,
                                                                              currentFilename.get());

            if(parts == null) {
                return null;
            }

            final ObjectCategory objectCategory =
                    nameToObjectCategoryMap.computeIfAbsent(parsedObjectCategory.getName(),
                                                            key -> parsedObjectCategory);

            boundingShapeCountPerCategory.merge(objectCategory.getName(), 1, Integer::sum);

            final BoundingPolygonData boundingPolygonData = new BoundingPolygonData(objectCategory, points, tags);
            boundingPolygonData.setParts(parts);

            return boundingPolygonData;
        }
    }

    private static class ImageAnnotationDataListDeserializer implements JsonDeserializer<List<ImageAnnotation>> {
        final DoubleProperty progress;

        public ImageAnnotationDataListDeserializer(DoubleProperty progress) {
            this.progress = progress;
        }

        @Override
        public List<ImageAnnotation> deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                                 JsonDeserializationContext context) {
            final JsonArray jsonArray = json.getAsJsonArray();

            int totalNrAnnotations = jsonArray.size();
            int nrProcessedAnnotations = 0;
            progress.set(nrProcessedAnnotations);

            final List<ImageAnnotation> imageAnnotationList = new ArrayList<>();

            for(final JsonElement annotationElement : jsonArray) {
                final ImageAnnotation annotation = context.deserialize(annotationElement,
                                                                       ImageAnnotation.class);

                if(annotation != null) {
                    imageAnnotationList.add(annotation);
                }

                progress.set(1.0 * ++nrProcessedAnnotations / totalNrAnnotations);
            }

            return imageAnnotationList;
        }
    }
}