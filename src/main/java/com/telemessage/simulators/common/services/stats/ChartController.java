package com.telemessage.simulators.common.services.stats;


import org.knowm.xchart.*;
import org.knowm.xchart.style.XYStyler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.ui.Model;

import java.awt.*;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

public class ChartController {

    public static XYChart createChart(String title, String xAxisTitle, String yAxisTitle, double[] xData, double[] yData, Double limit) {
        XYChart chart =createBaseChart(title, xAxisTitle, yAxisTitle);

        // Add data series
        XYSeries series = chart.addSeries("Data", xData, yData);
        series.setMarker(new None()); // Line only, no points

        // Add limit line if provided
        if (limit != null) {
            double[] limitLineY = new double[xData.length];
            for (int i = 0; i < xData.length; i++) {
                limitLineY[i] = limit;
            }
            XYSeries limitSeries = chart.addSeries("Limit", xData, limitLineY);
            limitSeries.setLineStyle(SeriesLines.DASH_DASH);
            limitSeries.setLineColor(Color.RED);
            limitSeries.setMarker(new None());
        }

        return chart;
    }

    public static String encodeChartToBase64(XYChart chart) {
        try {
            byte[] imageBytes = BitmapEncoder.getBitmapBytes(chart, BitmapEncoder.BitmapFormat.PNG);
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode chart to Base64", e);
        }
    }


    public static XYChart createBaseChart(String title, String xAxisTitle, String yAxisTitle) {
        // Data for the chart
//        String[] categories = {"A", "B", "C", "D", "E", "F", "G", "H"};
//        double[] x1 = {1000, 3000, 5000, 4000, 2000, 3000, 4000, 6000};
//        double[] y1 = {7000, 6000, 5000, 4000, 3000, 2000, 1000, 500};
//        double[] series3 = {3000, 7000, 6000, 2000, 7000, 5000, 2000, 7000};

        // Create the chart
        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(600)
                .title(title)
                .xAxisTitle(xAxisTitle)
                .yAxisTitle(yAxisTitle)
                .build();

        // Customize the chart style
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setPlotGridVerticalLinesVisible(false);
        chart.getStyler().setPlotGridHorizontalLinesVisible(true);
        chart.getStyler().setAxisTickLabelsColor(Color.BLACK);
        chart.getStyler().setAxisTickMarksColor(Color.GRAY);
//        chart.getStyler().setYAxisMin(0.0);
//        chart.getStyler().setYAxisMax(8000.0);

        // Alternate background stripes
//        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(new Color(245, 245, 245));

        return chart;
    }


    public static void handleRequest(Model model, StatisticsService statisticsService, WebServerApplicationContext webServerApplicationContext) {
        List<StatsData> history = statisticsService.getStatsHistory();

        // Generate X-axis data
        double[] xData = IntStream.range(0, history.size()).asDoubleStream().toArray();

        // CPU Data
        double[] cpuYData = history.stream().mapToDouble(StatsData::getCpuLoad).toArray();
        Double cpuLimit = history.get(history.size() - 1).getCpuMax();
        XYChart cpuChart = createChart("CPU Load Over Time", "Time (Steps)", "Load (%)", xData, cpuYData, cpuLimit);
        model.addAttribute("cpuChart", encodeChartToBase64(cpuChart));
    }
}
