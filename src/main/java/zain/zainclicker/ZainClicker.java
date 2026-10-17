package zain.zainclicker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class ZainClicker extends Application
{
    private volatile boolean enabled = false;
    private volatile int minCps = 0, maxCps = 0;
    private volatile boolean leftHeld = false;
    private final AtomicInteger fakeEvents = new AtomicInteger(0);

    @Override
    public void start(Stage stage)
    {
        //setting the title
        Label title = new Label("ZainClicker");
        title.getStyleClass().add("title");

        //setting default status when program is run
        Label status = new Label("● OFF");
        status.getStyleClass().add("status-off");

        //creation of enable row - label, checkbox & label
        Label enableLabel = new Label("Enable");
        CheckBox enableClicker = new CheckBox();
        enableLabel.setLabelFor(enableClicker);
        enableLabel.setOnMouseClicked(e -> enableClicker.fire());
        HBox enableRow = new HBox(8, enableLabel, enableClicker);
        enableRow.setAlignment(Pos.CENTER);

        //creation of mincps content - label, textfield and setting an example
        TextField minCpsField = new TextField();
        minCpsField.setPromptText("e.g. 8");
        minCpsField.setPrefWidth(90);
        minCpsField.setTextFormatter(integerOnly());
        VBox minBox = new VBox(4, new Label("Min CPS"), minCpsField);

        //creation of maxcps content - label, textfield and setting an example
        TextField maxCpsField = new TextField();
        maxCpsField.setPromptText("e.g. 12");
        maxCpsField.setPrefWidth(90);
        maxCpsField.setTextFormatter(integerOnly());
        VBox maxBox = new VBox(4, new Label("Max CPS"), maxCpsField);

        //creating a row for both cps rows
        HBox cpsRow = new HBox(12, minBox, maxBox);
        cpsRow.setAlignment(Pos.CENTER);

        //creation of text to show users how to use the program
        Label hint = new Label("Middle-click to toggle\nHold left click to fire");
        hint.getStyleClass().add("hint");
        hint.setTextAlignment(TextAlignment.CENTER);

        //creation of a new box including all of the content above
        VBox root = new VBox(14, title, status, enableRow, cpsRow, hint);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(20));

        //adding listeners for the enabling, setting of the cps values
        enableClicker.selectedProperty().addListener((obs, oldValue, newValue) -> {
            enabled = newValue;
            status.setText(newValue ? "● ON" : "● OFF");
            status.getStyleClass().setAll("label", newValue ? "status-on" : "status-off");
        });
        minCpsField.textProperty().addListener((obs, oldValue, newValue) -> minCps = parseOrZero(newValue));
        maxCpsField.textProperty().addListener((obs, oldValue, newValue) -> maxCps = parseOrZero(newValue));

        //starting the global mouse listener hook
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            System.err.println("Couldn't start global mouse listener: " + ex.getMessage());
        }

        //creating the mouse listeners for both pressed and released and calculation of fake clicks
        GlobalScreen.addNativeMouseListener(new NativeMouseListener() {
            @Override
            public void nativeMousePressed(NativeMouseEvent e) {
                if (e.getButton() == NativeMouseEvent.BUTTON3) {
                    Platform.runLater(() -> enableClicker.setSelected(!enableClicker.isSelected()));
                } else if (e.getButton() == NativeMouseEvent.BUTTON1 && !isFake()) {
                    leftHeld = true;
                }
            }

            @Override
            public void nativeMouseReleased(NativeMouseEvent e) {
                if (e.getButton() == NativeMouseEvent.BUTTON1 && !isFake()) {
                    leftHeld = false;
                }
            }

            private boolean isFake() {
                return fakeEvents.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
            }
        });

        //starting the clicker and initialising the gui
        startClicker();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        stage.setTitle("ZainClicker");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    //method to stop the clicker
    public void stop() throws Exception
    {
        GlobalScreen.unregisterNativeHook();
    }

    //method reading 0 from a blank box or parsing actual integers
    private static int parseOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //method to start the clicker called earlier
    private void startClicker() {
        Thread clickerThread = new Thread(() -> {
            Robot robot;
            try {
                robot = new Robot();
            } catch (AWTException e) {
                System.err.println("Couldn't create Robot: " + e.getMessage());
                return;
            }

            Random random = new Random();

            while (true) {
                try {
                    int low = Math.min(minCps, maxCps);
                    int high = Math.max(minCps, maxCps);

                    if (!enabled || !leftHeld || high <= 0) {
                        Thread.sleep(50);
                        fakeEvents.set(0);   // safety reset while idle
                        continue;
                    }

                    if (low < 1) low = 1;
                    int cps = low + random.nextInt(high - low + 1);

                    //split each click into a held half and a released half, because games like
                    //minecraft bedrock check the button once per frame and miss instant clicks
                    int half = Math.max(1, (1000 / cps) / 2);

                    Thread.sleep(half);   // wait first, since your own press already counts as a click

                    if (enabled && leftHeld) {   // you might have let go during the wait
                        //release first, then press again after a gap, so the button ends up down
                        //just like your finger is, and every cycle is a real up -> down change
                        fakeEvents.incrementAndGet();
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                        Thread.sleep(half);

                        if (leftHeld) {   // only press back down if you're still holding
                            fakeEvents.incrementAndGet();
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        clickerThread.setDaemon(true);
        clickerThread.start();
    }

    //main function to run the program
    public static void main(String[] args)
    {
        launch(args);
    }

    //disallowing users to enter integers into the program at all
    private static TextFormatter<String> integerOnly()
    {
        return new TextFormatter<>(change ->
        {
            if (change.getControlNewText().matches("\\d{0,2}"))
            {
                return change;
            }
            return null;
        });
    }
}
