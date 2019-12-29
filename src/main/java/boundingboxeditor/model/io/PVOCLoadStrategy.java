package boundingboxeditor.model.io;

import boundingboxeditor.model.BoundingBoxCategory;
import boundingboxeditor.model.ImageMetaData;
import boundingboxeditor.model.Model;
import boundingboxeditor.utils.ColorUtils;
import javafx.beans.property.DoubleProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements the loading of xml-files containing image-annotations in the
 * 'PASCAL Visual Object Classes (Pascal VOC)'-format.
 *
 * @see <a href="http://host.robots.ox.ac.uk/pascal/VOC/">Pascal VOC</a>
 */
public class PVOCLoadStrategy implements ImageAnnotationLoadStrategy {
    private static final boolean INCLUDE_SUBDIRECTORIES = false;
    private static final String MISSING_ELEMENT_PREFIX = "Missing element: ";
    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private Set<String> fileNamesToLoad;
    private Map<String, BoundingBoxCategory> existingBoundingBoxCategories;
    private Map<String, Integer> boundingBoxCountPerCategory;
    private List<IOResult.ErrorInfoEntry> unParsedFileErrorMessages;

    @Override
    public IOResult load(Model model, Path path, DoubleProperty progress) throws IOException {
        long startTime = System.nanoTime();

        this.fileNamesToLoad = model.getImageFileNameSet();
        this.boundingBoxCountPerCategory = new ConcurrentHashMap<>(model.getCategoryToAssignedBoundingBoxesCountMap());
        this.existingBoundingBoxCategories = new ConcurrentHashMap<>(model.getBoundingBoxCategories().stream()
                .collect(Collectors.toMap(BoundingBoxCategory::getName, Function.identity())));

        try(Stream<Path> fileStream = Files.walk(path, INCLUDE_SUBDIRECTORIES ? Integer.MAX_VALUE : 1)) {
            List<File> annotationFiles = fileStream
                    .filter(pathItem -> pathItem.getFileName().toString().endsWith(".xml"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            unParsedFileErrorMessages = Collections.synchronizedList(new ArrayList<>());

            int totalNrOfFiles = annotationFiles.size();
            AtomicInteger nrProcessedFiles = new AtomicInteger(0);

            List<ImageAnnotation> imageAnnotations = annotationFiles.parallelStream()
                    .map(file -> {
                        progress.set(1.0 * nrProcessedFiles.incrementAndGet() / totalNrOfFiles);

                        try {
                            return parseAnnotationFile(file);
                        } catch(SAXException | IOException | InvalidAnnotationFileFormatException
                                | ParserConfigurationException | AnnotationToNonExistentImageException e) {
                            unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(file.getName(), e.getMessage()));
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            model.getBoundingBoxCategories().setAll(existingBoundingBoxCategories.values());
            model.getCategoryToAssignedBoundingBoxesCountMap().putAll(boundingBoxCountPerCategory);
            model.updateImageAnnotations(imageAnnotations);

            long estimatedTime = System.nanoTime() - startTime;

            return new IOResult(
                    IOResult.OperationType.ANNOTATION_IMPORT,
                    imageAnnotations.size(),
                    TimeUnit.MILLISECONDS.convert(Duration.ofNanos(estimatedTime)),
                    unParsedFileErrorMessages
            );
        }
    }

    private ImageAnnotation parseAnnotationFile(File file) throws SAXException, IOException,
            ParserConfigurationException {
        final Document document = documentBuilderFactory.newDocumentBuilder().parse(file);
        document.normalize();

        ImageMetaData imageMetaData = parseImageMetaData(document);

        if(!fileNamesToLoad.contains(imageMetaData.getFileName())) {
            throw new AnnotationToNonExistentImageException("The image file does not belong to the currently loaded images.");
        }

        List<BoundingBoxData> boundingBoxData = parseBoundingBoxData(document, file.getName());

        if(boundingBoxData.isEmpty()) {
            // No image annotation will be constructed if it does not contain any bounding boxes.
            return null;
        }

        return new ImageAnnotation(imageMetaData, boundingBoxData);
    }

    private ImageMetaData parseImageMetaData(Document document) {
        String folderName = parseTextElement(document, "folder");
        String fileName = parseTextElement(document, "filename");
        double width = parseDoubleElement(document, "width");
        double height = parseDoubleElement(document, "height");
        int depth = parseIntElement(document, "depth");

        return new ImageMetaData(fileName, folderName, width, height, depth);
    }

    private List<BoundingBoxData> parseBoundingBoxData(Document document, String filename) {
        NodeList objectElements = document.getElementsByTagName("object");

        List<BoundingBoxData> boundingBoxDataList = new ArrayList<>();

        for(int i = 0; i != objectElements.getLength(); ++i) {
            Node objectNode = objectElements.item(i);

            if(objectNode.getNodeType() == Node.ELEMENT_NODE) {
                try {
                    BoundingBoxData boundingBoxData = parseBoundingBoxElement((Element) objectNode, filename);
                    boundingBoxDataList.add(boundingBoxData);
                } catch(InvalidAnnotationFileFormatException e) {
                    unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(filename, e.getMessage()));
                }
            }
        }

        return boundingBoxDataList;
    }

    private BoundingBoxData parseBoundingBoxElement(Element objectElement, String filename) {
        NodeList childElements = objectElement.getChildNodes();

        BoundingBoxDataParseResult boxDataParseResult = new BoundingBoxDataParseResult();

        // At first, parse all child elements except parts. In this way if errors occur,
        // no parts will be parsed.
        parseNonPartElements(childElements, boxDataParseResult);

        if(boxDataParseResult.getCategoryName() == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + "name");
        }

        if(boxDataParseResult.getxMin() == null || boxDataParseResult.getxMax() == null
                || boxDataParseResult.getyMin() == null || boxDataParseResult.getyMax() == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + "bndbox");
        }

        BoundingBoxCategory category = existingBoundingBoxCategories.computeIfAbsent(boxDataParseResult.getCategoryName(),
                key -> new BoundingBoxCategory(key, ColorUtils.createRandomColor()));

        boundingBoxCountPerCategory.merge(category.getName(), 1, Integer::sum);

        BoundingBoxData boundingBoxData = new BoundingBoxData(category, boxDataParseResult.getxMin(),
                boxDataParseResult.getyMin(), boxDataParseResult.getxMax(),
                boxDataParseResult.getyMax(), boxDataParseResult.getTags());

        // Now parse parts.
        parsePartElements(childElements, boxDataParseResult, filename);

        if(!boxDataParseResult.getParts().isEmpty()) {
            boundingBoxData.setParts(boxDataParseResult.getParts());
        }

        return boundingBoxData;
    }

    private void parseBoundingBoxDataTag(Element tagElement, BoundingBoxDataParseResult boxDataParseResult) {
        switch(tagElement.getTagName()) {
            case "name":
                String categoryName = tagElement.getTextContent();

                if(categoryName == null || categoryName.isBlank()) {
                    throw new InvalidAnnotationFileFormatException("Blank object name");
                }

                boxDataParseResult.setCategoryName(categoryName);
                break;
            case "bndbox":
                boxDataParseResult.setxMin(parseDoubleElement(tagElement, "xmin"));
                boxDataParseResult.setxMax(parseDoubleElement(tagElement, "xmax"));
                boxDataParseResult.setyMin(parseDoubleElement(tagElement, "ymin"));
                boxDataParseResult.setyMax(parseDoubleElement(tagElement, "ymax"));
                break;
            case "pose":
                String poseValue = tagElement.getTextContent();

                if(poseValue != null && !poseValue.equalsIgnoreCase("unspecified")) {
                    boxDataParseResult.getTags().add("pose: " + poseValue.toLowerCase());
                }

                break;
            case "truncated":
                if(Integer.parseInt(tagElement.getTextContent()) == 1) {
                    boxDataParseResult.getTags().add("truncated");
                }

                break;
            case "occluded":
                if(Integer.parseInt(tagElement.getTextContent()) == 1) {
                    boxDataParseResult.getTags().add("occluded");
                }

                break;
            case "difficult":
                if(Integer.parseInt(tagElement.getTextContent()) == 1) {
                    boxDataParseResult.getTags().add("difficult");
                }

                break;
            case "actions":
                boxDataParseResult.getTags().addAll(parseActions(tagElement));
                break;
            default: // Unknown tags are ignored!
        }
    }

    private void parsePart(Element tagElement, BoundingBoxDataParseResult boxDataParseResult, String filename) {
        try {
            boxDataParseResult.getParts().add(parseBoundingBoxElement(tagElement, filename));
        } catch(InvalidAnnotationFileFormatException e) {
            unParsedFileErrorMessages.add(new IOResult.ErrorInfoEntry(filename, e.getMessage()));
        }
    }

    private void parseNonPartElements(NodeList childElements, BoundingBoxDataParseResult boxDataParseResult) {
        for(int i = 0; i != childElements.getLength(); ++i) {
            Node currentChild = childElements.item(i);

            if(currentChild.getNodeType() == Node.ELEMENT_NODE) {
                Element currentElement = (Element) currentChild;

                if(!currentElement.getTagName().equals("part")) {
                    parseBoundingBoxDataTag((Element) currentChild, boxDataParseResult);
                }
            }
        }
    }

    private void parsePartElements(NodeList childElements, BoundingBoxDataParseResult boxDataParseResult, String filename) {
        for(int i = 0; i != childElements.getLength(); ++i) {
            Node currentChild = childElements.item(i);

            if(currentChild.getNodeType() == Node.ELEMENT_NODE) {
                Element currentElement = (Element) currentChild;

                if(currentElement.getTagName().equals("part")) {
                    parsePart((Element) currentChild, boxDataParseResult, filename);
                }
            }
        }
    }

    private List<String> parseActions(Element element) {
        NodeList childList = element.getChildNodes();
        List<String> actions = new ArrayList<>();

        for(int i = 0; i != childList.getLength(); ++i) {
            Node childNode = childList.item(i);

            if(childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) childNode;

            if(Integer.parseInt(childElement.getTextContent()) == 1) {
                actions.add("action: " + childElement.getTagName());
            }
        }

        return actions;
    }

    private String parseTextElement(Document document, String tagName) {
        Node textNode = document.getElementsByTagName(tagName).item(0);

        if(textNode == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + tagName);
        }

        return textNode.getTextContent();
    }

    private double parseDoubleElement(Document document, String tagName) {
        Node doubleNode = document.getElementsByTagName(tagName).item(0);

        if(doubleNode == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + tagName);
        }

        return Double.parseDouble(doubleNode.getTextContent());
    }

    private double parseDoubleElement(Element element, String tagName) {
        Node doubleNode = element.getElementsByTagName(tagName).item(0);

        if(doubleNode == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + tagName);
        }

        return Double.parseDouble(doubleNode.getTextContent());
    }

    private int parseIntElement(Document document, String tagName) {
        Node intNode = document.getElementsByTagName(tagName).item(0);

        if(intNode == null) {
            throw new InvalidAnnotationFileFormatException(MISSING_ELEMENT_PREFIX + tagName);
        }

        return Integer.parseInt(intNode.getTextContent());
    }

    private static class BoundingBoxDataParseResult {
        private String categoryName;
        private Double xMin;
        private Double xMax;
        private Double yMin;
        private Double yMax;
        private List<String> tags = new ArrayList<>();
        private List<BoundingBoxData> parts = new ArrayList<>();


        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public Double getxMin() {
            return xMin;
        }

        public void setxMin(Double xMin) {
            this.xMin = xMin;
        }

        public Double getxMax() {
            return xMax;
        }

        public void setxMax(Double xMax) {
            this.xMax = xMax;
        }

        public Double getyMin() {
            return yMin;
        }

        public void setyMin(Double yMin) {
            this.yMin = yMin;
        }

        public Double getyMax() {
            return yMax;
        }

        public void setyMax(Double yMax) {
            this.yMax = yMax;
        }

        public List<String> getTags() {
            return tags;
        }


        public List<BoundingBoxData> getParts() {
            return parts;
        }
    }

    @SuppressWarnings("serial")
    private static class AnnotationToNonExistentImageException extends RuntimeException {
        AnnotationToNonExistentImageException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
    private static class InvalidAnnotationFileFormatException extends RuntimeException {
        InvalidAnnotationFileFormatException(String message) {
            super(message);
        }
    }
}
