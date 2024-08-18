package cs1302.gallery;

import java.net.http.HttpClient;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.TilePane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.geometry.Orientation;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.util.Random;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    private static final String ITUNES_API = "https://itunes.apple.com/search";
    //creating the foundation for the app
    private Stage stage;
    private Scene scene;
    private VBox root;

    //User input panel
    private HBox searchPanel;
    private Button playButton;
    private Separator separator;
    private Text search;
    private TextField searchField;
    private ComboBox<String> searchParam;
    private Button searchButton;

    //ImageView text updater panel
    private HBox loadingPanel;
    private Text progressText;

    //ImageView panel
    private GridPane twenty;
    private Image[] imageArray;

    //Pannel for area under ImageView
    private HBox bottomPanel;
    private ProgressBar progressBar;
    private Text apiSource;

    //miscellaneous variables
    private String mainURL;
    private int arraySize;
    private List<String> allImages;
    private Image[] backgroundImageArray;
    private boolean playing;
    private Thread playThread;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox(8);
        this.searchPanel = new HBox(8);
        this.playButton = new Button("Play");
        this.playButton.setDisable(true);
        this.separator = new Separator();
        this.separator.setOrientation(Orientation.VERTICAL);
        this.separator.setMaxHeight(25);
        this.search = new Text("Search:");
        this.searchField = new TextField("daft punk");
        this.searchField.setPrefWidth(230);
        this.searchParam = new ComboBox<>();
        this.searchParam.getItems().addAll("movie", "podcast", "music", "musicVideo", "audiobook"
                                           , "shortFilm", "tvShow", "software", "ebook", "all");
        this.searchParam.setValue("music");
        this.searchButton = new Button("Get Images");
        this.loadingPanel = new HBox(8);
        this.progressText = new Text("Type in a term, select a media type, then click the Button.");
        this.twenty = new GridPane();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                Image image = new Image("file:resources/default.png");
                ImageView imgView = new ImageView(image);
                imgView.setFitHeight(120);
                imgView.setFitWidth(120);
                twenty.add(imgView, j, i);
            }
        }
        this.bottomPanel = new HBox(8);
        this.progressBar = new ProgressBar();
        this.progressBar.setProgress(0);
        this.progressBar.setPrefWidth(315);
        this.apiSource = new Text("Images provided by iTunes Search API.");
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        // feel free to modify this method
        this.root.getChildren().addAll(this.searchPanel, this.loadingPanel,
                                       this.twenty, this.bottomPanel);
        this.searchPanel.getChildren().addAll(this.playButton, this.separator, this.search,
                                              this.searchField,
                                              this.searchParam, this.searchButton);
        this.loadingPanel.getChildren().addAll(this.progressText);
        this.bottomPanel.getChildren().addAll(this.progressBar, this.apiSource);
        this.searchButton.setOnAction(event -> {
            this.progressText.setText("Getting Images...");
            this.generateImages();
        });
        this.playButton.setOnAction(event -> pressPlayButton());

        System.out.println("init() called");
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.root);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /**
     * Takes the images from the API and outputs the artwork100s onto the screen.
     *
     */
    private void generateImages() {
        Runnable runner = () -> {
            try {
                stopPlay();
                String url = this.buildItunesQuery(this.searchField.getText(),
                                                   this.searchParam.getValue(), "200");
                mainURL = url;
                allImages = new ArrayList<>();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException(response.toString());
                }
                String jsonString = response.body();
                ItunesResponse data =
                    GSON.<ItunesResponse>fromJson(jsonString, ItunesResponse.class);
                for (int i = 0; i < data.resultCount; i++) {
                    String artworkURLs = data.results[i].artworkUrl100;
                    if (!allImages.contains(artworkURLs)) {
                        allImages.add(artworkURLs);
                    }
                }
                arraySize = allImages.size();
                if (allImages.size() < 21) {
                    throw new IllegalArgumentException(allImages.size() +  " results found, "
                                                       +  "but 21 or more are required");
                }
                Platform.runLater(() -> playButton.setDisable(true));
                Platform.runLater(() -> playButton.setText("Play"));
                this.searchButton.setDisable(true);
                progressBar.setProgress(0);
                imageArray = new Image[allImages.size()];
                for (int i = 0; i < allImages.size(); i++) {
                    progressBar.setProgress(1.0 * i / allImages.size());
                    imageArray[i] = new Image(allImages.get(i));
                }
                progressBar.setProgress(1);
                searchButton.setDisable(false);
                playButton.setDisable(false);
                Platform.runLater(() -> {
                    for (int i = 0; i < 20; i++) {
                        ImageView imageView = (ImageView) twenty.getChildren().get(i);
                        imageView.setImage(imageArray[i]);
                    }
                    progressText.setText(url);
                });
            } catch (IllegalArgumentException | IOException | InterruptedException e) {
                System.err.println(e);
                e.printStackTrace();
                stopPlay();
                Platform.runLater(() -> {
                    this.progressText.setText("Last attempt to get images failed...");
                    this.playButton.setText("Play");
                    progressBar.setProgress(1);
                    alertError(e, mainURL);
                });
            }
        };
        runThread(runner, "Thread");
    }

    /**
     * Triggers upon PlayButton Press.
     */
    private void pressPlayButton() {
        if (playing == false) {
            startPlay();
            this.playButton.setText("Pause");
        } else {
            stopPlay();
            this.playButton.setText("Play");
        }
    }

    /**
     * Starts shuffling the images on screen with images in the background.
     */
    private void startPlay() {
        playing = true;
        backgroundImageArray = new Image[imageArray.length - 20];
        playThread = new Thread(() -> {
            while (playing) {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        Random random = new Random();
                        for (int i = 0; i < imageArray.length - 20; i++) {
                            if (backgroundImageArray[i] == null) {
                                backgroundImageArray[i] = imageArray[i + 20];
                            } //if
                        } //for
                        int remove = random.nextInt(20);
                        int replace = random.nextInt(imageArray.length - 20);
                        ImageView onScreen = (ImageView) twenty.getChildren().get(remove);
                        Image temp = onScreen.getImage();
                        onScreen.setImage(backgroundImageArray[replace]);
                        backgroundImageArray[replace] = temp;
                    });
                } catch (InterruptedException e) {
                    System.out.println("Exception");
                } //try
            } //while
        });
        playThread.start();
    }

    /**
     * Stops the startPlay() method, this method is implemented in the generateImages/PlayButton.
     */
    private void stopPlay() {
        playing = false;
        if (playThread != null) {
            playThread.stop();
            playThread = null;
        } //if

    }

    /**
     * Creates the itunes query using user inputs and a limit of 200.
     *
     * @return the query
     *
     * @param term is the user input
     * @param type comes from the dropdown
     * @param limit defaults to 200
     *
     */
    public String buildItunesQuery(String term, String type, String limit) {
        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
        String encodedType = URLEncoder.encode(type, StandardCharsets.UTF_8);
        String encodedLimit = URLEncoder.encode(limit, StandardCharsets.UTF_8);
        String query = String.format("%s?term=%s&media=%s&limit=%s", ITUNES_API, encodedTerm,
                                     encodedType, encodedLimit);
        return query;
    } //buildItunesQuery

    /**
     * Throws an error on screen if the generated query does not work in this instance.
     * @param cause shows what exception was thrown
     * @param url shows the url
     */
    private static void alertError(Throwable cause, String url) {
        TextArea text = new TextArea("URI: " + url + "\n" + "\n"
                                     + "Exception: " + cause.toString());
        text.setEditable(false);
        Alert alert = new Alert(AlertType.ERROR);
        alert.getDialogPane().setContent(text);
        alert.setResizable(true);
        alert.showAndWait();
    } //alertError

    /**
     * Runs the threads so that background processes can occur simultaneously.
     *
     * @param task requires the runnable
     * @param name requires a string
     *
     */
    private static void runThread(Runnable task, String name) {
        Thread t = new Thread(task, name);
        t.start();
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

} // GalleryApp
