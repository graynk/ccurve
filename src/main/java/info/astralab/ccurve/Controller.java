package info.astralab.ccurve;

import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.gillius.jfxutils.chart.ChartPanManager;
import org.gillius.jfxutils.chart.JFXChartUtil;
import org.gillius.jfxutils.chart.StableTicksAxis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class Controller {
    @FXML private TextField time1;
    @FXML private TextField time2;
    @FXML private TextField time3;
    @FXML private TextField time4;
    @FXML private TextField time5;
    @FXML private TextField percent1;
    @FXML private TextField percent2;
    @FXML private TextField percent3;
    @FXML private TextField percent4;
    @FXML private TextField percent5;
    @FXML private StackPane stackpane;
    @FXML private CheckBox linLinBox;
    @FXML private CheckBox logLogBox;
    @FXML private CheckBox linLogBox;
    @FXML private CheckBox logLinBox;
    private final StableTicksAxis xAxis = new StableTicksAxis();
    private final StableTicksAxis yAxis = new StableTicksAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
    private TextField[] timeList;
    private TextField[] percentList;

    public enum Axis {
        NONE, LIN_LIN, LIN_LOG, LOG_LIN, LOG_LOG;

        @Override
        public String toString() {
            switch (this) {
                case NONE:
                    return "-";
                case LIN_LIN:
                    return "lin/lin";
                case LIN_LOG:
                    return "lin/log";
                case LOG_LIN:
                    return "log/lin";
                case LOG_LOG:
                    return "log/log";
                default:
                    return this.name();
            }
        }
    }

    public void initialize() {
        timeList = new TextField[]{time1, time2, time3, time4, time5};
        percentList = new TextField[]{percent1, percent2, percent3, percent4, percent5};
        stackpane.getChildren().add(chart);
        chart.setAnimated(false);

        //chart.setCreateSymbols(false);
        chart.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                if (mouseEvent.getClickCount() == 2) {
                    chart.getXAxis().setAutoRanging(true);
                    chart.getYAxis().setAutoRanging(true);
                    /*((StableTicksAxis)chart.getXAxis()).setLowerBound(0);
                    ((StableTicksAxis)chart.getXAxis()).setUpperBound(700);
                    ((StableTicksAxis)chart.getYAxis()).setLowerBound(0);
                    ((StableTicksAxis)chart.getYAxis()).setUpperBound(1000);
                    chart.getScene().getWindow().setHeight(700+75);
                    chart.getScene().getWindow().setWidth(700);*/
                }
            }
        });

        ChartPanManager panner = new ChartPanManager(chart);
        panner.setMouseFilter(mouseEvent -> {
            if (!(mouseEvent.getButton() == MouseButton.SECONDARY)) {
                mouseEvent.consume();
            }
        });
        panner.start();

        //Zooming works only via primary mouse button with ctrl held down
        JFXChartUtil.setupZooming(chart, mouseEvent -> {
            if ( mouseEvent.getButton() != MouseButton.PRIMARY ||
                    !mouseEvent.isControlDown() )
                mouseEvent.consume();
        });
    }

    // В секторах 5 точек, либо возрастающие с нуля до 100 либо убывающие со 100. Если возрастающие -- может быть 4 точки, а 5-ая 0 -- его не брать
    // Нужно найти между какими точками провести прямую. Если точка за гранью секторов, то берем ближайший сектор
    private int findSector(float value, float[] values, float[] percentages) {
        int sector;
        int size = percentages.length;
        // возрастание проверяю В ДРУГОМ МАССИВЕ. то, что убывают времена до 0 не показатель, непонятно брать последний 0 или нет.
        int n = values[size - 3] < values[size - 2] && values[size - 1] == 0 ? size - 2 : size - 1; // если 4 возрастающих, то последний 0 не беру. если убывающие или 5 возрастающих, то беру все.
        for (sector = 1; sector < n; sector++) { // начинаем с 1, потому что дальше считаю прямую между sector-1 и sector
            // Не уверен возрастающий или убывающий порядок, проверяю на попадание в обоих случаях
            if ((value >= values[sector-1] && value <= values[sector]) || (value >= values[sector] && value <= values[sector-1]))
                break;
        }
        if (sector == n && values[0] > values[n] && value > values[0] || value < values[0]) { // если возрастающие и за пределами секторов, то проверить, вдруг больше первой точки. тогда вернуться в первый сектор.
            sector = 1;
        }
        return sector;
    }

    private float calculateConcentration(Axis selectedAxis, float time, float[] times, float[] percentages) {
        int sector = findSector(time, times, percentages);
        if(time == 0) time = 1; // логарифмы в бесконечность уйдут
        // Строим прямую между двумя точками на графике и находим процент/абсорбцию для времени пациента
        double concentration;
        switch (selectedAxis) { // оси могут быть логарифмическими или линейными
            case LOG_LOG:
            default:
                concentration =
                        Math.exp(
                                Math.log(time / times[sector - 1]) * // преобразовываю формулы с учетом разности логарифмов
                                        Math.log(percentages[sector] / percentages[sector - 1]) /
                                        Math.log(times[sector] / times[sector - 1]) +
                                        Math.log(percentages[sector - 1])
                        );
                break;
            case LIN_LIN:
                concentration = calculateDotOnLine(time, times[sector-1], times[sector], percentages[sector-1], percentages[sector]);
                break;
            case LIN_LOG:
                concentration =
                        Math.log(time / times[sector - 1]) * (percentages[sector] - percentages[sector - 1])
                                /
                                Math.log(times[sector] / times[sector - 1]) +
                                percentages[sector - 1];
                break;
            case LOG_LIN:
                concentration = Math.exp(
                        (time - times[sector - 1]) * Math.log(percentages[sector] / percentages[sector - 1])
                                /
                                (times[sector] - times[sector - 1]) +
                                Math.log(percentages[sector - 1])
                );
                break;
        }
        if (concentration < 0) concentration = 0;

        return BigDecimal.valueOf(concentration).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    private float calculateDotOnLine(float x, float x0, float x1, float y0, float y1) {
        return (x - x0) * (y1 - y0) / (x1 - x0) + y0;
    }

    @FXML
    private void go() {
        chart.getData().clear();
        float[] times = new float[] { Float.parseFloat(time1.getText()), Float.parseFloat(time2.getText()),
                Float.parseFloat(time3.getText()), Float.parseFloat(time4.getText()), Float.parseFloat(time5.getText()) };
        float[] percentages = new float[] { Float.parseFloat(percent1.getText()), Float.parseFloat(percent2.getText()),
                Float.parseFloat(percent3.getText()), Float.parseFloat(percent4.getText()), Float.parseFloat(percent5.getText()) };
        XYChart.Series<Number, Number> linLinSeries = new XYChart.Series<>();
        linLinSeries.setName("lin/lin");
        XYChart.Series<Number, Number> logLogSeries = new XYChart.Series<>();
        logLogSeries.setName("log/log");
        XYChart.Series<Number, Number> linLogSeries = new XYChart.Series<>();
        linLogSeries.setName("lin/log");
        XYChart.Series<Number, Number> logLinSeries = new XYChart.Series<>();
        logLinSeries.setName("log/lin");
        chart.getData().add(linLinSeries);
        chart.getData().add(logLogSeries);
        chart.getData().add(linLogSeries);
        chart.getData().add(logLinSeries);
        ArrayList<Node> points = new ArrayList<>();
        for (int i = 5; i < 70; i++) {
            float value;
            if (linLinBox.isSelected()) {
                value = calculateConcentration(Axis.LIN_LIN, i, times, percentages);
                linLinSeries.getData().add(new XYChart.Data<>(i, value));
            }
            if (logLogBox.isSelected()) {
                value = calculateConcentration(Axis.LOG_LOG, i, times, percentages);
                logLogSeries.getData().add(new XYChart.Data<>(i, value));
            }
            if (linLogBox.isSelected()) {
                value = calculateConcentration(Axis.LIN_LOG, i, times, percentages);
                linLogSeries.getData().add(new XYChart.Data<>(i, value));
            }
            if (logLinBox.isSelected()) {
                value = calculateConcentration(Axis.LOG_LIN, i, times, percentages);
                logLinSeries.getData().add(new XYChart.Data<>(i, value));
            }
        }
        XYChart.Series<Number, Number> controls = new XYChart.Series<>();
        controls.setName("ctrl");
        chart.getData().add(controls);
        for (int i = 0; i < times.length; i++) {
            if (times[i] == 0 || times[i] == 0.1 || percentages[i] == 0 || percentages[i] == 0.1)
                break;
            XYChart.Data<Number, Number> data = new XYChart.Data<>(times[i], percentages[i]);
            controls.getData().add(data);
            Node node = data.getNode();
            points.add(node);
            node.setCursor(Cursor.HAND);
            node.setOnMouseDragged(e -> {
                Point2D pointInScene = new Point2D(e.getSceneX(), e.getSceneY());
                double xAxisLoc = xAxis.sceneToLocal(pointInScene).getX();
                double yAxisLoc = yAxis.sceneToLocal(pointInScene).getY();
                Number x = xAxis.getValueForDisplay(xAxisLoc);
                Number y = yAxis.getValueForDisplay(yAxisLoc);
                BigDecimal bigX = BigDecimal.valueOf(x.floatValue()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal bigY = BigDecimal.valueOf(y.floatValue()).setScale(2, RoundingMode.HALF_UP);
                data.setXValue(bigX.floatValue());
                data.setYValue(bigY.floatValue());
                int controlPointIndex = points.indexOf(node);
                timeList[controlPointIndex].setText(bigX.toString());
                percentList[controlPointIndex].setText(bigY.toString());
            });
            node.setOnMouseReleased(e -> go());
        }
    }
}
