package org.example.javafxtryoutapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ImageGenerator extends Application {
    // Using Stable Diffusion XL for better compatibility
    private static final String API_URL = "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0";
    private static String API_KEY = ${HUGGING_FACE_API}; // Replace with your API key/
    // private static String API_KEY;
    private TextArea promptInput;
    private ImageView imageView;
    private ProgressIndicator progressIndicator;
    private Button generateButton;
    private TextArea logArea; // Added for debugging

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();
        root.setPadding(new Insets(5));
        root.autosize();
//        root.notify();

//Text Field
        Label apiKey =new Label("Enter your Hugging face API Key");
        TextField api_key = new TextField();
        //API_KEY = api_key.getText();

        // Components
        Label titleLabel = new Label("Hugging Face Image Generator");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        promptInput = new TextArea();
        promptInput.setPromptText("Enter your image prompt here...");
        promptInput.setPrefRowCount(2);

        generateButton = new Button("Generate Image");
        generateButton.setMaxWidth(Double.MAX_VALUE);

        progressIndicator = new ProgressIndicator();

//        progressIndicator.getOnMouseClicked(generateButton.);

        imageView = new ImageView();
        imageView.setFitWidth(200);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);

        // Add debug log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(4);
        logArea.setWrapText(true);
        logArea.setPromptText("Debug logs will appear here...");

        // Layout
        root.getChildren().addAll(
                titleLabel,
                apiKey,
                api_key,
                new Label("Prompt:"),
                promptInput,
                generateButton,
                progressIndicator,
                imageView,
                new Label("Debug Log:"),
                logArea
        );
        // Scene setup
        Scene scene = new Scene(root);
        primaryStage.setTitle("Hugging Face Image Generator");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial API key check
        if (API_KEY.isEmpty()) {
            showAlert("Please set your Hugging Face API key in the code.");
        }

        // Event handling
        generateButton.setOnAction(e -> generateImage());



    }

    private void log(String message) {
        javafx.application.Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }

    private void generateImage() {
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(0.25F);
        String prompt = promptInput.getText().trim();
        if (prompt.isEmpty()) {
            showAlert("Please enter a prompt first.");
            return;
        }

        setUIState(false);
        animateProgressBar();
        log("Starting image generation for prompt: " + prompt);

        new Thread(() -> {
            try {
                log("Calling Hugging Face API...");
                byte[] imageData = callHuggingFaceAPI(prompt);
                log("Received response from API");

                if (imageData == null || imageData.length == 0) {
                    throw new IOException("Received empty response from API");
                }

                // Check if response is an error message in JSON format
                String responseStr = new String(imageData, StandardCharsets.UTF_8);
                if (responseStr.startsWith("{")) {
                    log("Received error JSON response: " + responseStr);
                    throw new IOException("API returned error: " + responseStr);
                }

                ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                Image image = new Image(bis);

                if (image.isError()) {
                    throw new IOException("Failed to create image from response data");
                }

                javafx.application.Platform.runLater(() -> {
                    imageView.setImage(image);
                    setUIState(true);
                    log("Image generated successfully!");
                });

            } catch (Exception e) {
                log("Error: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    showAlert("Error generating image: " + e.getMessage());
                    setUIState(true);
                });
            }
        }).start();
    }

    private void animateProgressBar() {
        // Create a thread to animate the progress bar
        new Thread(() -> {
            for (double progress = 0; progress <= 1; progress += 0.1) {
                final double finalProgress = progress;
                Platform.runLater(() -> {
                    progressIndicator.setProgress(finalProgress);
                });

                try {
                    Thread.sleep(500); // Adjust speed of progression
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private byte[] callHuggingFaceAPI(String prompt) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Updated JSON payload format
        String jsonInput = String.format("{\"inputs\": \"%s\", \"parameters\": {\"negative_prompt\": \"\", \"num_inference_steps\": 30}}",
                prompt.replace("\"", "\\\""));
        log("Sending request with payload: " + jsonInput);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        log("Received response code: " + responseCode);

        if (responseCode != 200) {
            // Read error stream if available
            try (InputStream errorStream = conn.getErrorStream()) {
                if (errorStream != null) {
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = errorStream.read(buffer)) != -1) {
                        result.write(buffer, 0, length);
                    }
                    String errorResponse = result.toString(StandardCharsets.UTF_8);
                    log("Error response: " + errorResponse);
                    throw new IOException("HTTP error code: " + responseCode + "\nError: " + errorResponse);
                }
            }
            throw new IOException("HTTP error code: " + responseCode);
        }

        // Read response
        try (InputStream is = conn.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private void setUIState(boolean enabled) {
        generateButton.setDisable(!enabled);
        promptInput.setDisable(!enabled);
        progressIndicator.setVisible(!enabled);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
